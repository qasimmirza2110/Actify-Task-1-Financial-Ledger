package com.actify.financialledger.transaction.entity;

public enum TransactionType {
	DEPOSIT,
	WITHDRAWAL,
	TRANSFER,
	REVERSAL,
	// Needed so the scheduled monthly interest is traceable like any other money movement.
	INTEREST_CREDIT
}
