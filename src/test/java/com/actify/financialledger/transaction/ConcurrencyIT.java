package com.actify.financialledger.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.actify.financialledger.IntegrationTestSupport;
import com.actify.financialledger.LedgerInvariants;
import com.actify.financialledger.account.repository.AccountRepository;
import com.actify.financialledger.exception.InsufficientBalanceException;
import com.actify.financialledger.security.service.AppUserDetailsService;
import com.actify.financialledger.security.service.AppUserPrincipal;
import com.actify.financialledger.transaction.service.FinancialTransactionService;

/**
 * Fires many requests at the same moment against real PostgreSQL row locks.
 * Without SELECT ... FOR UPDATE, several withdrawals would read the same balance and the account
 * would end up negative (or money would be created by lost updates).
 */
class ConcurrencyIT extends IntegrationTestSupport {

	private static final int THREADS = 20;

	@Autowired
	private FinancialTransactionService transactionService;

	@Autowired
	private AppUserDetailsService userDetailsService;

	@Autowired
	private AccountRepository accountRepository;

	@Test
	void concurrentWithdrawalsNeverMakeBalanceNegative() throws Exception {
		String email = uniqueEmail();
		String token = registerAndLogin(email);
		long accountId = createAccount(token);
		deposit(token, accountId, "1000.00");
		AppUserPrincipal user = userDetailsService.loadUserByUsername(email);

		// 20 withdrawals of 100 against a balance of 1000: exactly 10 may succeed.
		List<Callable<Object>> tasks = new ArrayList<>();
		for (int i = 0; i < THREADS; i++) {
			tasks.add(() -> transactionService.withdraw(accountId, new BigDecimal("100.00"), user));
		}
		Results results = runAllAtOnce(tasks);

		assertThat(results.succeeded).isEqualTo(10);
		assertThat(results.insufficientBalance).isEqualTo(10);
		assertThat(results.otherErrors).isEmpty();
		assertThat(accountRepository.findById(accountId).orElseThrow().getBalance()).isEqualByComparingTo("0.00");
		LedgerInvariants.assertConsistent(jdbcTemplate);
	}

	@Test
	void concurrentTransfersAndWithdrawalsFromSameAccountStayConsistent() throws Exception {
		String qasimEmail = uniqueEmail();
		String qasim = registerAndLogin(qasimEmail);
		String sahil = registerAndLogin(uniqueEmail());
		long qasimAccount = createAccount(qasim);
		long sahilAccount = createAccount(sahil);
		deposit(qasim, qasimAccount, "1000.00");
		AppUserPrincipal qasimUser = userDetailsService.loadUserByUsername(qasimEmail);

		// Transfers of 100 cost 101.77 each; withdrawals cost 100. Only some fit into 1000.
		List<Callable<Object>> tasks = new ArrayList<>();
		for (int i = 0; i < THREADS; i++) {
			if (i % 2 == 0) {
				tasks.add(() -> transactionService.transfer(qasimAccount, sahilAccount, new BigDecimal("100.00"),
						qasimUser));
			} else {
				tasks.add(() -> transactionService.withdraw(qasimAccount, new BigDecimal("100.00"), qasimUser));
			}
		}
		Results results = runAllAtOnce(tasks);

		assertThat(results.otherErrors).isEmpty();
		BigDecimal qasimBalance = accountRepository.findById(qasimAccount).orElseThrow().getBalance();
		assertThat(qasimBalance.signum()).isGreaterThanOrEqualTo(0);
		assertThat(qasimBalance).isLessThan(new BigDecimal("100.00"));
		LedgerInvariants.assertConsistent(jdbcTemplate);
	}

	@Test
	void opposingTransfersDoNotDeadlock() throws Exception {
		String qasimEmail = uniqueEmail();
		String sahilEmail = uniqueEmail();
		String qasim = registerAndLogin(qasimEmail);
		String sahil = registerAndLogin(sahilEmail);
		long qasimAccount = createAccount(qasim);
		long sahilAccount = createAccount(sahil);
		deposit(qasim, qasimAccount, "5000.00");
		deposit(sahil, sahilAccount, "5000.00");
		AppUserPrincipal qasimUser = userDetailsService.loadUserByUsername(qasimEmail);
		AppUserPrincipal sahilUser = userDetailsService.loadUserByUsername(sahilEmail);

		// A->B and B->A at the same time. Without a fixed lock order this is the classic deadlock.
		List<Callable<Object>> tasks = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			tasks.add(() -> transactionService.transfer(qasimAccount, sahilAccount, new BigDecimal("10.00"), qasimUser));
			tasks.add(() -> transactionService.transfer(sahilAccount, qasimAccount, new BigDecimal("10.00"), sahilUser));
		}
		Results results = runAllAtOnce(tasks);

		assertThat(results.otherErrors).isEmpty();
		assertThat(results.succeeded).isEqualTo(20);
		// Each side paid 10 x (0.15 fee + 0.03 GST) = 1.80 in charges; the principal moved back and forth.
		assertThat(accountRepository.findById(qasimAccount).orElseThrow().getBalance()).isEqualByComparingTo("4998.20");
		assertThat(accountRepository.findById(sahilAccount).orElseThrow().getBalance()).isEqualByComparingTo("4998.20");
		LedgerInvariants.assertConsistent(jdbcTemplate);
	}

	private Results runAllAtOnce(List<Callable<Object>> tasks) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
		CountDownLatch startGate = new CountDownLatch(1);
		try {
			List<Future<Object>> futures = new ArrayList<>();
			for (Callable<Object> task : tasks) {
				futures.add(executor.submit(() -> {
					startGate.await();
					return task.call();
				}));
			}
			startGate.countDown();

			Results results = new Results();
			for (Future<Object> future : futures) {
				try {
					future.get(60, TimeUnit.SECONDS);
					results.succeeded++;
				} catch (java.util.concurrent.ExecutionException e) {
					if (e.getCause() instanceof InsufficientBalanceException) {
						results.insufficientBalance++;
					} else {
						results.otherErrors.add(e.getCause());
					}
				}
			}
			return results;
		} finally {
			executor.shutdownNow();
		}
	}

	private static class Results {
		int succeeded;
		int insufficientBalance;
		List<Throwable> otherErrors = new ArrayList<>();
	}
}
