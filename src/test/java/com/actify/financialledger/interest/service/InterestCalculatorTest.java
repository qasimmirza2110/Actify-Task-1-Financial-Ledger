package com.actify.financialledger.interest.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InterestCalculatorTest {

	private final InterestCalculator calculator = new InterestCalculator();

	@ParameterizedTest
	@CsvSource({
			"12000.00, 40.00", // 12000 x 0.04 / 12
			"1000.00, 3.33", // 3.3333 -> 3.33
			"5000.00, 16.67", // 16.6666 -> 16.67
			"0.10, 0.00" })
	void monthlyInterestIsFourPercentDividedByTwelve(String balance, String expected) {
		assertThat(calculator.monthlyInterest(new BigDecimal(balance))).isEqualByComparingTo(expected);
	}

	@Test
	void interestCompoundsMonthOnMonth() {
		BigDecimal balance = new BigDecimal("12000.00");

		BigDecimal first = calculator.monthlyInterest(balance);
		balance = balance.add(first);
		BigDecimal second = calculator.monthlyInterest(balance);

		assertThat(first).isEqualByComparingTo("40.00");
		// Second month is calculated on 12040.00, so it is a little more than 40.00.
		assertThat(second).isEqualByComparingTo("40.13");
	}
}
