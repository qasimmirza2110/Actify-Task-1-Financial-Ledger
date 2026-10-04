package com.actify.financialledger.transaction.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

/**
 * The only place where transfer fee and GST are calculated.
 *
 * fee   = amount x 1.5%        rounded to 2 decimals (HALF_UP)
 * gst   = fee x 18%            rounded to 2 decimals (HALF_UP)
 * total = amount + fee + gst   (what the sender pays)
 *
 * Example: amount 1000.00 -> fee 15.00, gst 2.70, total 1017.70
 */
@Component
public class TransferChargeCalculator {

	static final BigDecimal FEE_RATE = new BigDecimal("0.015");
	static final BigDecimal GST_RATE = new BigDecimal("0.18");
	private static final int MONEY_SCALE = 2;
	private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

	public TransferCharges calculate(BigDecimal amount) {
		BigDecimal principal = amount.setScale(MONEY_SCALE, ROUNDING);
		BigDecimal fee = principal.multiply(FEE_RATE).setScale(MONEY_SCALE, ROUNDING);
		// GST is charged only on the transfer fee, not on the transfer amount. It uses the rounded fee
		// because that is the fee actually charged to the customer.
		BigDecimal gst = fee.multiply(GST_RATE).setScale(MONEY_SCALE, ROUNDING);
		return new TransferCharges(principal, fee, gst, principal.add(fee).add(gst));
	}
}
