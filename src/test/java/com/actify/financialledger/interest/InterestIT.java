package com.actify.financialledger.interest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.support.TransactionTemplate;

import com.actify.financialledger.IntegrationTestSupport;
import com.actify.financialledger.account.entity.Account;
import com.actify.financialledger.account.entity.AccountStatus;
import com.actify.financialledger.account.repository.AccountRepository;
import com.actify.financialledger.interest.service.InterestScheduler;
import com.actify.financialledger.interest.service.InterestService;
import com.actify.financialledger.ledger.entity.EntryType;
import com.actify.financialledger.ledger.entity.LedgerEntry;
import com.actify.financialledger.ledger.repository.LedgerEntryRepository;
import com.actify.financialledger.transaction.entity.FinancialTransaction;
import com.actify.financialledger.transaction.entity.TransactionType;
import com.actify.financialledger.transaction.repository.FinancialTransactionRepository;

class InterestIT extends IntegrationTestSupport {

	@Autowired
	private InterestScheduler interestScheduler;

	@Autowired
	private InterestService interestService;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private FinancialTransactionRepository transactionRepository;

	@Autowired
	private LedgerEntryRepository ledgerEntryRepository;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Test
	void monthlyInterestIsCreditedOnceWithBalancedLedger() throws Exception {
		String token = registerAndLogin(uniqueEmail());
		long accountId = createAccount(token);
		deposit(token, accountId, "12000.00");
		YearMonth period = YearMonth.of(2031, 1);

		interestScheduler.runForPeriod(period);

		Account account = accountRepository.findById(accountId).orElseThrow();
		assertThat(account.getBalance()).isEqualByComparingTo("12040.00"); // 12000 x 4% / 12 = 40.00

		String reference = InterestService.interestReference(account, period);
		FinancialTransaction interest = transactionRepository.findAll().stream()
				.filter(t -> t.getReference().equals(reference)).findFirst().orElseThrow();
		assertThat(interest.getTransactionType()).isEqualTo(TransactionType.INTEREST_CREDIT);
		List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(interest.getId());
		assertThat(entries).extracting(LedgerEntry::getEntryType).containsExactly(EntryType.DEBIT, EntryType.CREDIT);
		assertThat(entries.get(0).getAccount().getAccountNumber()).isEqualTo("SYS-INTEREST-EXPENSE");
		assertThat(entries.get(0).getAmount()).isEqualByComparingTo(entries.get(1).getAmount());

		// Running the job again for the same month must not pay interest twice.
		interestScheduler.runForPeriod(period);
		assertThat(interestService.applyMonthlyInterest(accountId, period)).isFalse();
		assertThat(accountRepository.findById(accountId).orElseThrow().getBalance()).isEqualByComparingTo("12040.00");

		// Next month compounds on the new balance: 12040 x 4% / 12 = 40.13
		interestScheduler.runForPeriod(period.plusMonths(1));
		assertThat(accountRepository.findById(accountId).orElseThrow().getBalance()).isEqualByComparingTo("12080.13");
	}

	@Test
	void blockedAndZeroBalanceAccountsGetNoInterest() throws Exception {
		String token = registerAndLogin(uniqueEmail());
		long blockedAccount = createAccount(token);
		long emptyAccount = createAccount(token);
		deposit(token, blockedAccount, "5000.00");
		transactionTemplate.executeWithoutResult(tx -> accountRepository.findById(blockedAccount).orElseThrow()
				.setStatus(AccountStatus.BLOCKED));
		YearMonth period = YearMonth.of(2031, 6);

		interestScheduler.runForPeriod(period);

		assertThat(accountRepository.findById(blockedAccount).orElseThrow().getBalance()).isEqualByComparingTo("5000.00");
		assertThat(accountRepository.findById(emptyAccount).orElseThrow().getBalance()).isEqualByComparingTo("0.00");
		assertThat(interestService.applyMonthlyInterest(blockedAccount, period)).isFalse();
		assertThat(interestService.applyMonthlyInterest(emptyAccount, period)).isFalse();
	}

	@Test
	void manualTriggerIsAdminOnly() throws Exception {
		String customer = registerAndLogin(uniqueEmail());

		mockMvc.perform(post("/api/admin/interest/run").param("period", "2031-09")
				.header(HttpHeaders.AUTHORIZATION, bearer(customer)))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/admin/interest/run").param("period", "2031-09")
				.header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
				.andExpect(status().isOk());
	}

	@Test
	void interestAmountUsesBigDecimalRounding() throws Exception {
		String token = registerAndLogin(uniqueEmail());
		long accountId = createAccount(token);
		deposit(token, accountId, "1000.00");

		interestScheduler.runForPeriod(YearMonth.of(2032, 3));

		// 1000 x 0.04 / 12 = 3.3333... -> 3.33
		assertThat(accountRepository.findById(accountId).orElseThrow().getBalance())
				.isEqualByComparingTo(new BigDecimal("1003.33"));
	}
}
