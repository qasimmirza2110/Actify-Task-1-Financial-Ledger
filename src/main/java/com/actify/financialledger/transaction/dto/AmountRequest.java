package com.actify.financialledger.transaction.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Body for deposit and withdrawal. At most 2 decimal places, so no silent rounding of what the user sent.
 */
public record AmountRequest(
		@NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount) {
}
