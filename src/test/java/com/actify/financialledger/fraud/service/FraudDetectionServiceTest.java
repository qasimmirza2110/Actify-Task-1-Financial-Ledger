package com.actify.financialledger.fraud.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.actify.financialledger.account.entity.Account;
import com.actify.financialledger.exception.FraudDetectedException;
import com.actify.financialledger.transaction.repository.FinancialTransactionRepository;
import com.actify.financialledger.user.entity.AppUser;
import com.actify.financialledger.user.entity.Role;

class FraudDetectionServiceTest {

	private FinancialTransactionRepository repository;
	private FraudDetectionService fraudDetectionService;
	private Account account;

	@BeforeEach
	void setUp() {
		repository = mock(FinancialTransactionRepository.class);
		fraudDetectionService = new FraudDetectionService(new FraudProperties(new BigDecimal("10000.00"), 10, 3),
				repository);
		account = Account.customerAccount("ACC1", new AppUser("a@test.com", "hash", Role.CUSTOMER));
	}

	@Test
	void transferAtLimitIsAllowed() {
		when(repository.countOutgoingSince(any(), anyCollection(), any())).thenReturn(0L);

		assertThatCode(() -> fraudDetectionService.checkTransfer(account, new BigDecimal("10000.00")))
				.doesNotThrowAnyException();
	}

	@Test
	void transferAboveLimitIsRejected() {
		assertThatThrownBy(() -> fraudDetectionService.checkTransfer(account, new BigDecimal("10000.01")))
				.isInstanceOf(FraudDetectedException.class)
				.hasMessageContaining("limit");
	}

	@Test
	void tooManyRecentTransactionsAreRejected() {
		when(repository.countOutgoingSince(any(), anyCollection(), any())).thenReturn(3L);

		assertThatThrownBy(() -> fraudDetectionService.checkWithdrawal(account))
				.isInstanceOf(FraudDetectedException.class);
	}

	@Test
	void belowVelocityLimitIsAllowed() {
		when(repository.countOutgoingSince(any(), anyCollection(), any())).thenReturn(2L);

		assertThatCode(() -> fraudDetectionService.checkWithdrawal(account)).doesNotThrowAnyException();
	}
}
