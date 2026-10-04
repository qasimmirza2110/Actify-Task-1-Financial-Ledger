package com.actify.financialledger.account.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.actify.financialledger.account.entity.Account;
import com.actify.financialledger.account.entity.AccountStatus;
import com.actify.financialledger.account.entity.AccountType;

public record AccountResponse(Long id, String accountNumber, AccountType accountType, BigDecimal balance,
		AccountStatus status, Instant createdAt, Instant updatedAt) {

	public static AccountResponse from(Account account) {
		return new AccountResponse(account.getId(), account.getAccountNumber(), account.getAccountType(),
				account.getBalance(), account.getStatus(), account.getCreatedAt(), account.getUpdatedAt());
	}
}
