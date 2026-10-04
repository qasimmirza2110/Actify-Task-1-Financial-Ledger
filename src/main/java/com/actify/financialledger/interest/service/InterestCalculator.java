package com.actify.financialledger.interest.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

/**
 * 4% nominal annual rate, compounded monthly.
 *
 * monthly interest = balance x 0.04 / 12, rounded to 2 decimals (HALF_UP)
 *
 * Compounding happens naturally: the interest is added to the balance, so next month's interest
 * is calculated on the bigger balance.
 */
@Component
public class InterestCalculator {

	static final BigDecimal ANNUAL_RATE = new BigDecimal("0.04");
	private static final BigDecimal MONTHS_PER_YEAR = new BigDecimal("12");
	private static final int MONEY_SCALE = 2;

	public BigDecimal monthlyInterest(BigDecimal balance) {
		// Multiply first and divide once at the end, so there is only one rounding step.
		// (0.04 / 12 = 0.00333... cannot be stored exactly as a BigDecimal.)
		return balance.multiply(ANNUAL_RATE).divide(MONTHS_PER_YEAR, MONEY_SCALE, RoundingMode.HALF_UP);
	}
}
