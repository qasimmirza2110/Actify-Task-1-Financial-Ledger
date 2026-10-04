package com.actify.financialledger.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TransferChargeCalculatorTest {

	private final TransferChargeCalculator calculator = new TransferChargeCalculator();

	@Test
	void taskExample_1000_gives_fee_15_gst_2_70_total_1017_70() {
		TransferCharges charges = calculator.calculate(new BigDecimal("1000"));

		assertThat(charges.amount()).isEqualByComparingTo("1000.00");
		assertThat(charges.fee()).isEqualByComparingTo("15.00");
		assertThat(charges.gst()).isEqualByComparingTo("2.70");
		assertThat(charges.totalDebit()).isEqualByComparingTo("1017.70");
		assertThat(charges.totalDebit().scale()).isEqualTo(2);
	}

	@Test
	void gstIsCalculatedOnFeeNotOnAmount() {
		TransferCharges charges = calculator.calculate(new BigDecimal("1000.00"));

		// 18% of the amount would be 180.00; 18% of the fee is 2.70.
		assertThat(charges.gst()).isEqualByComparingTo(charges.fee().multiply(new BigDecimal("0.18")));
		assertThat(charges.gst()).isNotEqualByComparingTo("180.00");
	}

	@ParameterizedTest
	@CsvSource({
			// amount, fee, gst, total
			"333.33, 5.00, 0.90, 339.23", // fee 4.99995 -> 5.00
			"100.00, 1.50, 0.27, 101.77",
			"10.00, 0.15, 0.03, 10.18", // gst 0.027 -> 0.03
			"0.01, 0.00, 0.00, 0.01", // fee rounds to zero
			"12345.67, 185.19, 33.33, 12564.19" })
	void roundsHalfUpToTwoDecimals(String amount, String fee, String gst, String total) {
		TransferCharges charges = calculator.calculate(new BigDecimal(amount));

		assertThat(charges.fee()).isEqualByComparingTo(fee);
		assertThat(charges.gst()).isEqualByComparingTo(gst);
		assertThat(charges.totalDebit()).isEqualByComparingTo(total);
	}
}
