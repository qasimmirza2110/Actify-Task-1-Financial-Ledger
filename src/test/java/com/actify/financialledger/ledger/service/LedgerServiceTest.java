package com.actify.financialledger.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.actify.financialledger.account.entity.Account;
import com.actify.financialledger.exception.InsufficientBalanceException;
import com.actify.financialledger.exception.UnbalancedLedgerException;
import com.actify.financialledger.ledger.entity.LedgerEntry;
import com.actify.financialledger.ledger.repository.LedgerEntryRepository;
import com.actify.financialledger.transaction.entity.FinancialTransaction;
import com.actify.financialledger.user.entity.AppUser;
import com.actify.financialledger.user.entity.Role;

class LedgerServiceTest {

	private LedgerEntryRepository repository;
	private LedgerService ledgerService;
	private Account customer;
	private Account cash;
	private FinancialTransaction transaction;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		repository = mock(LedgerEntryRepository.class);
		when(repository.saveAll(any(Iterable.class))).thenAnswer(invocation -> invocation.getArgument(0));
		ledgerService = new LedgerService(repository);
		customer = Account.customerAccount("ACC1", new AppUser("a@test.com", "hash", Role.CUSTOMER));
		cash = Account.systemAccount("SYS-CASH");
		transaction = FinancialTransaction.deposit("TXN-1", customer, new BigDecimal("100.00"), "a@test.com");
	}

	@Test
	void balancedPostingUpdatesBalances() {
		ledgerService.post(List.of(
				LedgerEntry.debit(transaction, cash, new BigDecimal("100.00")),
				LedgerEntry.credit(transaction, customer, new BigDecimal("100.00"))));

		assertThat(customer.getBalance()).isEqualByComparingTo("100.00");
		// System accounts may go negative: cash is an asset shown with credit-minus-debit sign.
		assertThat(cash.getBalance()).isEqualByComparingTo("-100.00");
	}

	@Test
	void unbalancedPostingIsRejectedAndNothingSaved() {
		List<LedgerEntry> entries = List.of(
				LedgerEntry.debit(transaction, cash, new BigDecimal("100.00")),
				LedgerEntry.credit(transaction, customer, new BigDecimal("99.99")));

		assertThatThrownBy(() -> ledgerService.post(entries)).isInstanceOf(UnbalancedLedgerException.class);
		assertThat(customer.getBalance()).isEqualByComparingTo("0.00");
		verify(repository, never()).saveAll(any());
	}

	@Test
	void singleEntryIsRejected() {
		assertThatThrownBy(() -> ledgerService.validateBalanced(
				List.of(LedgerEntry.credit(transaction, customer, new BigDecimal("1.00")))))
				.isInstanceOf(UnbalancedLedgerException.class);
	}

	@Test
	void zeroAmountEntryIsRejected() {
		assertThatThrownBy(() -> ledgerService.validateBalanced(List.of(
				LedgerEntry.debit(transaction, cash, BigDecimal.ZERO),
				LedgerEntry.credit(transaction, customer, BigDecimal.ZERO))))
				.isInstanceOf(UnbalancedLedgerException.class);
	}

	@Test
	void customerBalanceCannotGoNegative() {
		List<LedgerEntry> entries = List.of(
				LedgerEntry.debit(transaction, customer, new BigDecimal("0.01")),
				LedgerEntry.credit(transaction, cash, new BigDecimal("0.01")));

		assertThatThrownBy(() -> ledgerService.post(entries)).isInstanceOf(InsufficientBalanceException.class);
		verify(repository, never()).saveAll(any());
	}

	@Test
	void reversalEntriesAreExactOpposite() {
		LedgerEntry original = LedgerEntry.debit(transaction, customer, new BigDecimal("17.70"));
		LedgerEntry opposite = original.opposite(transaction);

		assertThat(opposite.getEntryType().name()).isEqualTo("CREDIT");
		assertThat(opposite.getAmount()).isEqualByComparingTo("17.70");
		assertThat(opposite.getAccount()).isSameAs(customer);
	}
}
