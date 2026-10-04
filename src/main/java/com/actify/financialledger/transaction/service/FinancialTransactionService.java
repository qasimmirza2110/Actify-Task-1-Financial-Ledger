package com.actify.financialledger.transaction.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.actify.financialledger.account.entity.Account;
import com.actify.financialledger.account.entity.SystemAccount;
import com.actify.financialledger.account.service.AccountService;
import com.actify.financialledger.audit.entity.AuditAction;
import com.actify.financialledger.audit.service.AuditService;
import com.actify.financialledger.exception.InsufficientBalanceException;
import com.actify.financialledger.exception.InvalidTransactionException;
import com.actify.financialledger.fraud.service.FraudDetectionService;
import com.actify.financialledger.ledger.entity.LedgerEntry;
import com.actify.financialledger.ledger.service.LedgerService;
import com.actify.financialledger.security.service.AppUserPrincipal;
import com.actify.financialledger.transaction.dto.TransactionResponse;
import com.actify.financialledger.transaction.entity.FinancialTransaction;
import com.actify.financialledger.transaction.repository.FinancialTransactionRepository;

/**
 * Deposit, withdrawal and transfer.
 *
 * Every public method is one database transaction: lock accounts -> validate -> save transaction ->
 * post ledger entries (which changes balances) -> audit -> commit. If anything throws, everything
 * is rolled back, so we never end up with a changed balance but no ledger, or a debited sender
 * but no credited receiver.
 *
 * Accounts are always locked BEFORE they are read and validated. Checking the balance before
 * taking the lock would let two requests both see the same old balance.
 */
@Service
public class FinancialTransactionService {

	private static final int MONEY_SCALE = 2;

	private final AccountService accountService;
	private final LedgerService ledgerService;
	private final FinancialTransactionRepository transactionRepository;
	private final TransferChargeCalculator chargeCalculator;
	private final FraudDetectionService fraudDetectionService;
	private final AuditService auditService;

	public FinancialTransactionService(AccountService accountService, LedgerService ledgerService,
			FinancialTransactionRepository transactionRepository, TransferChargeCalculator chargeCalculator,
			FraudDetectionService fraudDetectionService, AuditService auditService) {
		this.accountService = accountService;
		this.ledgerService = ledgerService;
		this.transactionRepository = transactionRepository;
		this.chargeCalculator = chargeCalculator;
		this.fraudDetectionService = fraudDetectionService;
		this.auditService = auditService;
	}

	/**
	 * DEBIT SYSTEM_CASH, CREDIT customer account.
	 */
	@Transactional
	public TransactionResponse deposit(Long accountId, BigDecimal requestedAmount, AppUserPrincipal currentUser) {
		BigDecimal amount = toMoney(requestedAmount);
		Long cashAccountId = accountService.getSystemAccountId(SystemAccount.CASH);

		Map<Long, Account> locked = accountService.lockAccountsInIdOrder(List.of(accountId, cashAccountId));
		Account account = locked.get(accountId);
		Account cash = locked.get(cashAccountId);

		accountService.checkIsOwner(account, currentUser);
		accountService.checkIsActive(account);

		FinancialTransaction transaction = transactionRepository
				.save(FinancialTransaction.deposit(newReference(), account, amount, currentUser.getEmail()));
		List<LedgerEntry> entries = ledgerService.post(List.of(
				LedgerEntry.debit(transaction, cash, amount),
				LedgerEntry.credit(transaction, account, amount)));

		auditService.recordSuccess(currentUser.getEmail(), AuditAction.DEPOSIT, "ACCOUNT", account.getId(),
				transaction.getReference(), "Deposit of " + amount + " to " + account.getAccountNumber());
		return TransactionResponse.from(transaction, entries);
	}

	/**
	 * DEBIT customer account, CREDIT SYSTEM_CASH.
	 */
	@Transactional
	public TransactionResponse withdraw(Long accountId, BigDecimal requestedAmount, AppUserPrincipal currentUser) {
		BigDecimal amount = toMoney(requestedAmount);
		Long cashAccountId = accountService.getSystemAccountId(SystemAccount.CASH);

		// Lock the account before checking the balance so two withdrawals cannot use the same old balance.
		Map<Long, Account> locked = accountService.lockAccountsInIdOrder(List.of(accountId, cashAccountId));
		Account account = locked.get(accountId);
		Account cash = locked.get(cashAccountId);

		accountService.checkIsOwner(account, currentUser);
		accountService.checkIsActive(account);
		fraudDetectionService.checkWithdrawal(account);
		requireSufficientBalance(account, amount);

		FinancialTransaction transaction = transactionRepository
				.save(FinancialTransaction.withdrawal(newReference(), account, amount, currentUser.getEmail()));
		List<LedgerEntry> entries = ledgerService.post(List.of(
				LedgerEntry.debit(transaction, account, amount),
				LedgerEntry.credit(transaction, cash, amount)));

		auditService.recordSuccess(currentUser.getEmail(), AuditAction.WITHDRAWAL, "ACCOUNT", account.getId(),
				transaction.getReference(), "Withdrawal of " + amount + " from " + account.getAccountNumber());
		return TransactionResponse.from(transaction, entries);
	}

	/**
	 * DEBIT sender (amount + fee + GST), CREDIT receiver (amount), CREDIT fee revenue (fee),
	 * CREDIT GST payable (GST).
	 */
	@Transactional
	public TransactionResponse transfer(Long sourceAccountId, Long destinationAccountId, BigDecimal requestedAmount,
			AppUserPrincipal currentUser) {
		if (sourceAccountId.equals(destinationAccountId)) {
			throw new InvalidTransactionException("Source and destination accounts must be different");
		}
		TransferCharges charges = chargeCalculator.calculate(toMoney(requestedAmount));
		Long feeAccountId = accountService.getSystemAccountId(SystemAccount.FEE_REVENUE);
		Long gstAccountId = accountService.getSystemAccountId(SystemAccount.GST_PAYABLE);

		// All four accounts are locked in ascending id order (see AccountService) to avoid deadlocks
		// when two users transfer to each other at the same time. The fee and GST accounts are locked
		// too, because every transfer updates their balances.
		Map<Long, Account> locked = accountService.lockAccountsInIdOrder(
				List.of(sourceAccountId, destinationAccountId, feeAccountId, gstAccountId));
		Account source = locked.get(sourceAccountId);
		Account destination = locked.get(destinationAccountId);

		accountService.checkIsOwner(source, currentUser);
		if (!destination.isCustomerAccount()) {
			throw new InvalidTransactionException("Transfers can only be made to customer accounts");
		}
		accountService.checkIsActive(source);
		accountService.checkIsActive(destination);
		fraudDetectionService.checkTransfer(source, charges.amount());
		requireSufficientBalance(source, charges.totalDebit());

		FinancialTransaction transaction = transactionRepository.save(FinancialTransaction.transfer(newReference(),
				source, destination, charges.amount(), charges.fee(), charges.gst(), currentUser.getEmail()));

		List<LedgerEntry> lines = new ArrayList<>();
		lines.add(LedgerEntry.debit(transaction, source, charges.totalDebit()));
		lines.add(LedgerEntry.credit(transaction, destination, charges.amount()));
		// For very small transfers (e.g. 0.01) the rounded fee is 0.00, and a ledger line of 0 is not allowed.
		if (charges.fee().signum() > 0) {
			lines.add(LedgerEntry.credit(transaction, locked.get(feeAccountId), charges.fee()));
		}
		if (charges.gst().signum() > 0) {
			lines.add(LedgerEntry.credit(transaction, locked.get(gstAccountId), charges.gst()));
		}
		List<LedgerEntry> entries = ledgerService.post(lines);

		auditService.recordSuccess(currentUser.getEmail(), AuditAction.TRANSFER, "ACCOUNT", source.getId(),
				transaction.getReference(),
				"Transfer of " + charges.amount() + " from " + source.getAccountNumber() + " to "
						+ destination.getAccountNumber() + " (fee " + charges.fee() + ", GST " + charges.gst() + ")");
		return TransactionResponse.from(transaction, entries);
	}

	private void requireSufficientBalance(Account account, BigDecimal required) {
		if (account.getBalance().compareTo(required) < 0) {
			throw new InsufficientBalanceException("Insufficient balance in account " + account.getAccountNumber()
					+ ": required " + required + ", available " + account.getBalance());
		}
	}

	/**
	 * The controller already validates the amount; this keeps the service safe when called directly.
	 * setScale(2) without a rounding mode throws if the amount has more than 2 decimals.
	 */
	private BigDecimal toMoney(BigDecimal amount) {
		if (amount == null || amount.signum() <= 0) {
			throw new InvalidTransactionException("Amount must be greater than zero");
		}
		try {
			return amount.setScale(MONEY_SCALE);
		} catch (ArithmeticException e) {
			throw new InvalidTransactionException("Amount can have at most 2 decimal places");
		}
	}

	private String newReference() {
		return "TXN-" + UUID.randomUUID();
	}
}
