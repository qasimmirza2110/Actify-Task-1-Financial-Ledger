package com.actify.financialledger.interest.service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.actify.financialledger.account.entity.Account;
import com.actify.financialledger.account.entity.SystemAccount;
import com.actify.financialledger.account.service.AccountService;
import com.actify.financialledger.audit.entity.AuditAction;
import com.actify.financialledger.audit.service.AuditService;
import com.actify.financialledger.ledger.entity.LedgerEntry;
import com.actify.financialledger.ledger.service.LedgerService;
import com.actify.financialledger.transaction.entity.FinancialTransaction;
import com.actify.financialledger.transaction.repository.FinancialTransactionRepository;

/**
 * Applies one month of interest to ONE account in its own database transaction, so a problem with
 * one account does not undo the interest already credited to others.
 */
@Service
public class InterestService {

	private final AccountService accountService;
	private final LedgerService ledgerService;
	private final FinancialTransactionRepository transactionRepository;
	private final InterestCalculator interestCalculator;
	private final AuditService auditService;

	public InterestService(AccountService accountService, LedgerService ledgerService,
			FinancialTransactionRepository transactionRepository, InterestCalculator interestCalculator,
			AuditService auditService) {
		this.accountService = accountService;
		this.ledgerService = ledgerService;
		this.transactionRepository = transactionRepository;
		this.interestCalculator = interestCalculator;
		this.auditService = auditService;
	}

	/**
	 * @return true if interest was credited, false if the account was skipped
	 */
	@Transactional
	public boolean applyMonthlyInterest(Long accountId, YearMonth period) {
		Long expenseAccountId = accountService.getSystemAccountId(SystemAccount.INTEREST_EXPENSE);
		Map<Long, Account> locked = accountService.lockAccountsInIdOrder(List.of(accountId, expenseAccountId));
		Account account = locked.get(accountId);

		// One interest credit per account per month. The reference is unique in the database, so even
		// if the scheduler runs twice, the second run finds it here (or fails on the unique constraint).
		String reference = interestReference(account, period);
		if (transactionRepository.existsByReference(reference)) {
			return false;
		}
		// Re-checked after locking, because the account could have changed since the scheduler listed it.
		if (!account.isCustomerAccount() || !account.isActive() || account.getBalance().signum() <= 0) {
			return false;
		}
		BigDecimal interest = interestCalculator.monthlyInterest(account.getBalance());
		if (interest.signum() <= 0) {
			// Very small balances round to 0.00 interest.
			return false;
		}

		FinancialTransaction transaction = transactionRepository
				.save(FinancialTransaction.interestCredit(reference, account, interest));
		ledgerService.post(List.of(
				LedgerEntry.debit(transaction, locked.get(expenseAccountId), interest),
				LedgerEntry.credit(transaction, account, interest)));

		auditService.recordSuccess("SYSTEM", AuditAction.INTEREST_CREDIT, "ACCOUNT", account.getId(), reference,
				"Interest " + interest + " for " + period + " credited to " + account.getAccountNumber());
		return true;
	}

	public static String interestReference(Account account, YearMonth period) {
		return "INT-" + account.getAccountNumber() + "-" + period;
	}
}
