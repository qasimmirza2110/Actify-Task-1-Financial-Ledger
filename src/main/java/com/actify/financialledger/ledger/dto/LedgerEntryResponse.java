package com.actify.financialledger.ledger.dto;

import java.math.BigDecimal;

import com.actify.financialledger.ledger.entity.EntryType;
import com.actify.financialledger.ledger.entity.LedgerEntry;

public record LedgerEntryResponse(String accountNumber, EntryType entryType, BigDecimal amount) {

	public static LedgerEntryResponse from(LedgerEntry entry) {
		return new LedgerEntryResponse(entry.getAccount().getAccountNumber(), entry.getEntryType(), entry.getAmount());
	}
}
