package com.actify.financialledger.fraud.service;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Fraud thresholds from application.properties (app.fraud.*). The task does not give exact
 * numbers, so the defaults are sample values and can be changed without touching code.
 */
@ConfigurationProperties(prefix = "app.fraud")
public record FraudProperties(BigDecimal maxTransferAmount, int velocityWindowMinutes, int maxTransactionsInWindow) {
}
