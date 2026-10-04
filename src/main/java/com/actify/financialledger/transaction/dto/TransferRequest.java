package com.actify.financialledger.transaction.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TransferRequest(
		@NotNull Long sourceAccountId,
		@NotNull Long destinationAccountId,
		@NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount) {
}
