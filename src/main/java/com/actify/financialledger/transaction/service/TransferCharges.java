package com.actify.financialledger.transaction.service;

import java.math.BigDecimal;

public record TransferCharges(BigDecimal amount, BigDecimal fee, BigDecimal gst, BigDecimal totalDebit) {
}
