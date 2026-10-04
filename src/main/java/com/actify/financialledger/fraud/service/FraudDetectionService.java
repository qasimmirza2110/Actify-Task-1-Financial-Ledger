package com.actify.financialledger.fraud.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.actify.financialledger.account.entity.Account;
import com.actify.financialledger.exception.FraudDetectedException;
import com.actify.financialledger.transaction.entity.TransactionType;
import com.actify.financialledger.transaction.repository.FinancialTransactionRepository;

/**
 * Two simple, deterministic rules for money leaving a customer account:
 *
 * 1. Large transfer: a single transfer above app.fraud.max-transfer-amount is rejected.
 * 2. Velocity: if the account already made app.fraud.max-transactions-in-window withdrawals/transfers
 *    in the last app.fraud.velocity-window-minutes, the next one is rejected.
 *
 * Called after the source account is locked and before any balance is changed, so a rejected
 * operation changes nothing, and two parallel requests cannot both slip under the velocity limit.
 */
@Service
public class FraudDetectionService {

	private static final Logger log = LoggerFactory.getLogger(FraudDetectionService.class);
	private static final List<TransactionType> OUTGOING_TYPES = List.of(TransactionType.WITHDRAWAL,
			TransactionType.TRANSFER);

	private final FraudProperties properties;
	private final FinancialTransactionRepository transactionRepository;

	public FraudDetectionService(FraudProperties properties, FinancialTransactionRepository transactionRepository) {
		this.properties = properties;
		this.transactionRepository = transactionRepository;
	}

	public void checkTransfer(Account source, BigDecimal amount) {
		if (amount.compareTo(properties.maxTransferAmount()) > 0) {
			log.warn("Fraud rule MAX_TRANSFER_AMOUNT triggered for account id {}", source.getId());
			throw new FraudDetectedException(
					"Transfer rejected: amount exceeds the allowed limit of " + properties.maxTransferAmount());
		}
		checkVelocity(source);
	}

	public void checkWithdrawal(Account source) {
		checkVelocity(source);
	}

	private void checkVelocity(Account source) {
		Instant since = Instant.now().minus(Duration.ofMinutes(properties.velocityWindowMinutes()));
		long recentCount = transactionRepository.countOutgoingSince(source.getId(), OUTGOING_TYPES, since);
		if (recentCount >= properties.maxTransactionsInWindow()) {
			log.warn("Fraud rule VELOCITY triggered for account id {}", source.getId());
			throw new FraudDetectedException("Transaction rejected: more than "
					+ properties.maxTransactionsInWindow() + " outgoing transactions in "
					+ properties.velocityWindowMinutes() + " minutes");
		}
	}
}
