package com.actify.financialledger.transaction.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.actify.financialledger.account.entity.Account;
import com.actify.financialledger.ledger.dto.LedgerEntryResponse;
import com.actify.financialledger.ledger.entity.LedgerEntry;
import com.actify.financialledger.transaction.entity.FinancialTransaction;
import com.actify.financialledger.transaction.entity.TransactionStatus;
import com.actify.financialledger.transaction.entity.TransactionType;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * ledgerEntries is filled for single-transaction responses and left out of history lists.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionResponse(Long id, String reference, TransactionType transactionType, BigDecimal amount,
		BigDecimal fee, BigDecimal gst, BigDecimal totalAmount, String sourceAccountNumber,
		String destinationAccountNumber, TransactionStatus status, String reversalOfReference, Instant timestamp,
		List<LedgerEntryResponse> ledgerEntries) {

	public static TransactionResponse from(FinancialTransaction transaction) {
		return from(transaction, null);
	}

	public static TransactionResponse from(FinancialTransaction transaction, List<LedgerEntry> entries) {
		return new TransactionResponse(transaction.getId(), transaction.getReference(),
				transaction.getTransactionType(), transaction.getAmount(), transaction.getFee(), transaction.getGst(),
				transaction.getTotalAmount(), accountNumber(transaction.getSourceAccount()),
				accountNumber(transaction.getDestinationAccount()), transaction.getStatus(),
				transaction.getReversalOf() == null ? null : transaction.getReversalOf().getReference(),
				transaction.getCreatedAt(),
				entries == null ? null : entries.stream().map(LedgerEntryResponse::from).toList());
	}

	private static String accountNumber(Account account) {
		return account == null ? null : account.getAccountNumber();
	}
}
