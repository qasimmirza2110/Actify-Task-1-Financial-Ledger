package com.actify.financialledger.transaction.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.actify.financialledger.account.service.AccountService;
import com.actify.financialledger.audit.entity.AuditAction;
import com.actify.financialledger.audit.service.AuditService;
import com.actify.financialledger.exception.InvalidTransactionException;
import com.actify.financialledger.exception.TransactionAlreadyReversedException;
import com.actify.financialledger.exception.TransactionNotFoundException;
import com.actify.financialledger.ledger.entity.LedgerEntry;
import com.actify.financialledger.ledger.repository.LedgerEntryRepository;
import com.actify.financialledger.ledger.service.LedgerService;
import com.actify.financialledger.security.service.AppUserPrincipal;
import com.actify.financialledger.transaction.dto.TransactionResponse;
import com.actify.financialledger.transaction.entity.FinancialTransaction;
import com.actify.financialledger.transaction.entity.TransactionStatus;
import com.actify.financialledger.transaction.entity.TransactionType;
import com.actify.financialledger.transaction.repository.FinancialTransactionRepository;

/**
 * Reverses a transaction by creating a NEW transaction of type REVERSAL whose ledger entries are the
 * exact opposite of the original ones. The original transaction and its ledger rows are kept, so the
 * full history stays auditable.
 *
 * Assumption: a reversal undoes the full financial effect, including the transfer fee and GST.
 * Only ADMIN can call it (rule in SecurityConfig).
 */
@Service
public class ReversalService {

	private final FinancialTransactionRepository transactionRepository;
	private final LedgerEntryRepository ledgerEntryRepository;
	private final LedgerService ledgerService;
	private final AccountService accountService;
	private final AuditService auditService;

	public ReversalService(FinancialTransactionRepository transactionRepository,
			LedgerEntryRepository ledgerEntryRepository, LedgerService ledgerService, AccountService accountService,
			AuditService auditService) {
		this.transactionRepository = transactionRepository;
		this.ledgerEntryRepository = ledgerEntryRepository;
		this.ledgerService = ledgerService;
		this.accountService = accountService;
		this.auditService = auditService;
	}

	@Transactional
	public TransactionResponse reverse(Long transactionId, AppUserPrincipal currentUser) {
		// Lock the original first, so a second reversal request waits here and then sees REVERSED.
		FinancialTransaction original = transactionRepository.findByIdForUpdate(transactionId)
				.orElseThrow(() -> new TransactionNotFoundException("Transaction " + transactionId + " not found"));

		if (original.getTransactionType() == TransactionType.REVERSAL) {
			throw new InvalidTransactionException("A reversal transaction cannot be reversed");
		}
		if (original.getStatus() == TransactionStatus.REVERSED) {
			throw new TransactionAlreadyReversedException(
					"Transaction " + original.getReference() + " has already been reversed");
		}

		// Lock every account touched by the original, in id order, before reading any balance.
		accountService.lockAccountsInIdOrder(ledgerEntryRepository.findAccountIdsByTransactionId(transactionId));
		List<LedgerEntry> originalEntries = ledgerEntryRepository.findByTransactionId(transactionId);

		FinancialTransaction reversal = transactionRepository
				.save(FinancialTransaction.reversal("REV-" + UUID.randomUUID(), original, currentUser.getEmail()));
		List<LedgerEntry> reversalEntries = originalEntries.stream()
				.map(entry -> entry.opposite(reversal))
				.toList();

		// If the receiver has already spent the money, this throws InsufficientBalanceException and
		// everything rolls back: a reversal never pushes a customer balance below zero.
		List<LedgerEntry> entries = ledgerService.post(reversalEntries);
		original.markReversed();

		auditService.recordSuccess(currentUser.getEmail(), AuditAction.REVERSAL, "TRANSACTION", original.getId(),
				reversal.getReference(), "Reversal of " + original.getReference());
		return TransactionResponse.from(reversal, entries);
	}
}
