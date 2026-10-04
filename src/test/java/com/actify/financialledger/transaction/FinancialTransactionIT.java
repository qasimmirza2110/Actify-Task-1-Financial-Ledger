package com.actify.financialledger.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.support.TransactionTemplate;

import com.actify.financialledger.IntegrationTestSupport;
import com.actify.financialledger.account.entity.Account;
import com.actify.financialledger.account.entity.AccountStatus;
import com.actify.financialledger.account.entity.SystemAccount;
import com.actify.financialledger.account.repository.AccountRepository;
import com.actify.financialledger.audit.entity.AuditAction;
import com.actify.financialledger.audit.entity.AuditStatus;
import com.actify.financialledger.audit.repository.AuditLogRepository;
import com.actify.financialledger.ledger.entity.EntryType;
import com.actify.financialledger.ledger.entity.LedgerEntry;
import com.actify.financialledger.ledger.repository.LedgerEntryRepository;
import com.actify.financialledger.transaction.entity.FinancialTransaction;
import com.actify.financialledger.transaction.entity.TransactionStatus;
import com.actify.financialledger.transaction.repository.FinancialTransactionRepository;

import tools.jackson.databind.JsonNode;

class FinancialTransactionIT extends IntegrationTestSupport {

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private FinancialTransactionRepository transactionRepository;

	@Autowired
	private LedgerEntryRepository ledgerEntryRepository;

	@Autowired
	private AuditLogRepository auditLogRepository;

	@Autowired
	private TransactionTemplate transactionTemplate;

	// ---------- accounts ----------

	@Test
	void createAndReadOwnAccount() throws Exception {
		String token = registerAndLogin(uniqueEmail());
		long accountId = createAccount(token);

		mockMvc.perform(get("/api/accounts/" + accountId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accountType").value("CUSTOMER"))
				.andExpect(jsonPath("$.balance").value(0.0))
				.andExpect(jsonPath("$.accountNumber").isNotEmpty());

		mockMvc.perform(get("/api/accounts/my").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void cannotReadOrUseAnotherUsersAccount() throws Exception {
		String qasim = registerAndLogin(uniqueEmail());
		String sahil = registerAndLogin(uniqueEmail());
		long qasimAccount = createAccount(qasim);
		deposit(qasim, qasimAccount, "100.00");

		mockMvc.perform(get("/api/accounts/" + qasimAccount).header(HttpHeaders.AUTHORIZATION, bearer(sahil)))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/accounts/" + qasimAccount + "/transactions")
				.header(HttpHeaders.AUTHORIZATION, bearer(sahil)))
				.andExpect(status().isForbidden());
		mockMvc.perform(authPost(sahil, "/api/accounts/" + qasimAccount + "/deposit").content("{\"amount\": 10}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(authPost(sahil, "/api/accounts/" + qasimAccount + "/withdraw").content("{\"amount\": 10}"))
				.andExpect(status().isForbidden());

		assertThat(balanceOf(qasimAccount)).isEqualByComparingTo("100.00");
	}

	@Test
	void unknownAccountReturns404() throws Exception {
		String token = registerAndLogin(uniqueEmail());

		mockMvc.perform(get("/api/accounts/99999999").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isNotFound());
	}

	@Test
	void accountNumberIsUniqueInDatabase() throws Exception {
		String token = registerAndLogin(uniqueEmail());
		long accountId = createAccount(token);
		Account existing = accountRepository.findById(accountId).orElseThrow();

		// Inserting a system account with an existing account number must fail at the database level.
		assertThrows(DataIntegrityViolationException.class,
				() -> accountRepository.saveAndFlush(Account.systemAccount(existing.getAccountNumber())));
	}

	// ---------- deposit / withdrawal ----------

	@Test
	void depositCreatesTransactionLedgerAndAudit() throws Exception {
		String token = registerAndLogin(uniqueEmail());
		long accountId = createAccount(token);

		JsonNode response = deposit(token, accountId, "500.00");

		assertThat(balanceOf(accountId)).isEqualByComparingTo("500.00");
		assertThat(response.get("transactionType").asString()).isEqualTo("DEPOSIT");
		String reference = response.get("reference").asString();
		assertBalancedLedger(response.get("id").asLong(), "500.00");
		assertThat(auditLogRepository.findByTransactionReference(reference))
				.extracting(log -> log.getAction()).containsExactly(AuditAction.DEPOSIT);
	}

	@Test
	void invalidAmountsAreRejected() throws Exception {
		String token = registerAndLogin(uniqueEmail());
		long accountId = createAccount(token);
		String url = "/api/accounts/" + accountId + "/deposit";

		for (String body : List.of("{\"amount\": 0}", "{\"amount\": -5}", "{\"amount\": null}", "{}",
				"{\"amount\": 10.123}", "{\"amount\": \"abc\"}", "not json")) {
			mockMvc.perform(authPost(token, url).content(body)).andExpect(status().isBadRequest());
		}
		assertThat(balanceOf(accountId)).isEqualByComparingTo("0.00");
	}

	@Test
	void withdrawalReducesBalance() throws Exception {
		String token = registerAndLogin(uniqueEmail());
		long accountId = createAccount(token);
		deposit(token, accountId, "500.00");

		String body = mockMvc.perform(authPost(token, "/api/accounts/" + accountId + "/withdraw")
				.content("{\"amount\": 200.50}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		assertThat(balanceOf(accountId)).isEqualByComparingTo("299.50");
		assertBalancedLedger(readJson(body).get("id").asLong(), "200.50");
	}

	@Test
	void withdrawalWithInsufficientBalanceIsRejectedAndAudited() throws Exception {
		String email = uniqueEmail();
		String token = registerAndLogin(email);
		long accountId = createAccount(token);
		deposit(token, accountId, "100.00");

		mockMvc.perform(authPost(token, "/api/accounts/" + accountId + "/withdraw").content("{\"amount\": 100.01}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(containsString("Insufficient balance")));

		assertThat(balanceOf(accountId)).isEqualByComparingTo("100.00");
		assertThat(auditLogRepository.findByActorAndAction(email, AuditAction.TRANSACTION_REJECTED))
				.extracting(log -> log.getStatus()).containsExactly(AuditStatus.FAILURE);
	}

	@Test
	void blockedAccountCannotBeUsed() throws Exception {
		String token = registerAndLogin(uniqueEmail());
		long accountId = createAccount(token);
		setStatus(accountId, AccountStatus.BLOCKED);

		mockMvc.perform(authPost(token, "/api/accounts/" + accountId + "/deposit").content("{\"amount\": 10}"))
				.andExpect(status().isBadRequest());
	}

	// ---------- transfer ----------

	@Test
	void transferChargesFeeAndGstAndBalancesLedger() throws Exception {
		String qasim = registerAndLogin(uniqueEmail());
		String sahil = registerAndLogin(uniqueEmail());
		long qasimAccount = createAccount(qasim);
		long sahilAccount = createAccount(sahil);
		deposit(qasim, qasimAccount, "2000.00");
		BigDecimal feeBefore = systemBalance(SystemAccount.FEE_REVENUE);
		BigDecimal gstBefore = systemBalance(SystemAccount.GST_PAYABLE);

		String body = mockMvc.perform(authPost(qasim, "/api/transfers")
				.content(transferBody(qasimAccount, sahilAccount, "1000.00")))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		JsonNode response = readJson(body);

		assertThat(response.get("fee").decimalValue()).isEqualByComparingTo("15.00");
		assertThat(response.get("gst").decimalValue()).isEqualByComparingTo("2.70");
		assertThat(response.get("totalAmount").decimalValue()).isEqualByComparingTo("1017.70");

		assertThat(balanceOf(qasimAccount)).isEqualByComparingTo("982.30"); // 2000 - 1017.70
		assertThat(balanceOf(sahilAccount)).isEqualByComparingTo("1000.00");
		assertThat(systemBalance(SystemAccount.FEE_REVENUE).subtract(feeBefore)).isEqualByComparingTo("15.00");
		assertThat(systemBalance(SystemAccount.GST_PAYABLE).subtract(gstBefore)).isEqualByComparingTo("2.70");

		List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(response.get("id").asLong());
		assertThat(entries).hasSize(4);
		assertBalancedLedger(response.get("id").asLong(), "1017.70");
	}

	@Test
	void transferRules() throws Exception {
		String qasim = registerAndLogin(uniqueEmail());
		String sahil = registerAndLogin(uniqueEmail());
		long qasimAccount = createAccount(qasim);
		long sahilAccount = createAccount(sahil);
		deposit(qasim, qasimAccount, "1000.00");

		// same source and destination
		mockMvc.perform(authPost(qasim, "/api/transfers").content(transferBody(qasimAccount, qasimAccount, "10")))
				.andExpect(status().isBadRequest());
		// sahil tries to send money from qasim's account
		mockMvc.perform(authPost(sahil, "/api/transfers").content(transferBody(qasimAccount, sahilAccount, "10")))
				.andExpect(status().isForbidden());
		// 1000 + 15 + 2.70 > 1000: the fee and GST must also be covered
		mockMvc.perform(authPost(qasim, "/api/transfers").content(transferBody(qasimAccount, sahilAccount, "1000")))
				.andExpect(status().isConflict());
		// destination does not exist
		mockMvc.perform(authPost(qasim, "/api/transfers").content(transferBody(qasimAccount, 99999999L, "10")))
				.andExpect(status().isNotFound());
		// transfers to SYSTEM accounts are not allowed
		long cashId = accountRepository.findIdByAccountNumber(SystemAccount.CASH.getAccountNumber()).orElseThrow();
		mockMvc.perform(authPost(qasim, "/api/transfers").content(transferBody(qasimAccount, cashId, "10")))
				.andExpect(status().isBadRequest());
		// missing fields
		mockMvc.perform(authPost(qasim, "/api/transfers").content("{\"amount\": 10}"))
				.andExpect(status().isBadRequest());

		assertThat(balanceOf(qasimAccount)).isEqualByComparingTo("1000.00");
		assertThat(balanceOf(sahilAccount)).isEqualByComparingTo("0.00");
	}

	@Test
	void transferToBlockedAccountIsRejected() throws Exception {
		String qasim = registerAndLogin(uniqueEmail());
		String sahil = registerAndLogin(uniqueEmail());
		long qasimAccount = createAccount(qasim);
		long sahilAccount = createAccount(sahil);
		deposit(qasim, qasimAccount, "100.00");
		setStatus(sahilAccount, AccountStatus.BLOCKED);

		mockMvc.perform(authPost(qasim, "/api/transfers").content(transferBody(qasimAccount, sahilAccount, "10")))
				.andExpect(status().isBadRequest());
		assertThat(balanceOf(qasimAccount)).isEqualByComparingTo("100.00");
	}

	// ---------- fraud ----------

	@Test
	void transferAboveFraudLimitIsRejectedWithoutChangingBalances() throws Exception {
		String email = uniqueEmail();
		String qasim = registerAndLogin(email);
		String sahil = registerAndLogin(uniqueEmail());
		long qasimAccount = createAccount(qasim);
		long sahilAccount = createAccount(sahil);
		deposit(qasim, qasimAccount, "60000.00");

		// test limit is 50000.00 (application-test.properties)
		mockMvc.perform(authPost(qasim, "/api/transfers").content(transferBody(qasimAccount, sahilAccount, "50000.01")))
				.andExpect(status().isUnprocessableContent());

		assertThat(balanceOf(qasimAccount)).isEqualByComparingTo("60000.00");
		assertThat(balanceOf(sahilAccount)).isEqualByComparingTo("0.00");
		// Only the deposit exists: the rejected transfer left no transaction row behind.
		assertThat(transactionCountFor(qasimAccount)).isEqualTo(1);
		assertThat(auditLogRepository.findByActorAndAction(email, AuditAction.FRAUD_REJECTED)).hasSize(1);
	}

	@Test
	void databaseRejectsNegativeCustomerBalanceEvenWithoutJavaChecks() throws Exception {
		String token = registerAndLogin(uniqueEmail());
		long accountId = createAccount(token);

		// Last safety net: the CHECK constraint on accounts, independent of the service code.
		assertThrows(DataIntegrityViolationException.class,
				() -> jdbcTemplate.update("UPDATE accounts SET balance = -0.01 WHERE id = ?", accountId));
		assertThat(balanceOf(accountId)).isEqualByComparingTo("0.00");
	}

	@Test
	void tooManyWithdrawalsInWindowAreRejected() throws Exception {
		String token = registerAndLogin(uniqueEmail());
		long accountId = createAccount(token);
		deposit(token, accountId, "100.00");

		// test limit: 15 outgoing transactions in 10 minutes
		for (int i = 0; i < 15; i++) {
			mockMvc.perform(authPost(token, "/api/accounts/" + accountId + "/withdraw").content("{\"amount\": 1}"))
					.andExpect(status().isCreated());
		}
		mockMvc.perform(authPost(token, "/api/accounts/" + accountId + "/withdraw").content("{\"amount\": 1}"))
				.andExpect(status().isUnprocessableContent());

		assertThat(balanceOf(accountId)).isEqualByComparingTo("85.00");
	}

	// ---------- reversal ----------

	@Test
	void reversalCreatesOppositeEntriesAndKeepsOriginal() throws Exception {
		String qasim = registerAndLogin(uniqueEmail());
		String sahil = registerAndLogin(uniqueEmail());
		long qasimAccount = createAccount(qasim);
		long sahilAccount = createAccount(sahil);
		deposit(qasim, qasimAccount, "2000.00");
		long transferId = transfer(qasim, qasimAccount, sahilAccount, "1000.00");

		String body = mockMvc.perform(authPost(adminToken(), "/api/transactions/" + transferId + "/reverse"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.transactionType").value("REVERSAL"))
				.andReturn().getResponse().getContentAsString();
		long reversalId = readJson(body).get("id").asLong();

		// Full effect is undone, including fee and GST.
		assertThat(balanceOf(qasimAccount)).isEqualByComparingTo("2000.00");
		assertThat(balanceOf(sahilAccount)).isEqualByComparingTo("0.00");

		// Original is still there, now marked REVERSED, with its 4 ledger rows untouched.
		FinancialTransaction original = transactionRepository.findById(transferId).orElseThrow();
		assertThat(original.getStatus()).isEqualTo(TransactionStatus.REVERSED);
		List<LedgerEntry> originalEntries = ledgerEntryRepository.findByTransactionId(transferId);
		List<LedgerEntry> reversalEntries = ledgerEntryRepository.findByTransactionId(reversalId);
		assertThat(originalEntries).hasSize(4);
		assertThat(reversalEntries).hasSize(4);
		for (int i = 0; i < originalEntries.size(); i++) {
			assertThat(reversalEntries.get(i).getAccount().getId())
					.isEqualTo(originalEntries.get(i).getAccount().getId());
			assertThat(reversalEntries.get(i).getAmount()).isEqualByComparingTo(originalEntries.get(i).getAmount());
			assertThat(reversalEntries.get(i).getEntryType()).isNotEqualTo(originalEntries.get(i).getEntryType());
		}
		assertBalancedLedger(reversalId, "1017.70");
		assertThat(transactionRepository.findByReversalOfId(transferId)).isPresent();
	}

	@Test
	void transactionCannotBeReversedTwice() throws Exception {
		String token = registerAndLogin(uniqueEmail());
		long accountId = createAccount(token);
		long depositId = deposit(token, accountId, "100.00").get("id").asLong();
		String admin = adminToken();

		mockMvc.perform(authPost(admin, "/api/transactions/" + depositId + "/reverse"))
				.andExpect(status().isCreated());
		mockMvc.perform(authPost(admin, "/api/transactions/" + depositId + "/reverse"))
				.andExpect(status().isConflict());

		assertThat(balanceOf(accountId)).isEqualByComparingTo("0.00");
	}

	@Test
	void reversalIsRejectedWhenReceiverAlreadySpentTheMoney() throws Exception {
		String qasim = registerAndLogin(uniqueEmail());
		String sahil = registerAndLogin(uniqueEmail());
		long qasimAccount = createAccount(qasim);
		long sahilAccount = createAccount(sahil);
		deposit(qasim, qasimAccount, "2000.00");
		long transferId = transfer(qasim, qasimAccount, sahilAccount, "1000.00");
		mockMvc.perform(authPost(sahil, "/api/accounts/" + sahilAccount + "/withdraw").content("{\"amount\": 600}"))
				.andExpect(status().isCreated());

		mockMvc.perform(authPost(adminToken(), "/api/transactions/" + transferId + "/reverse"))
				.andExpect(status().isConflict());

		// Nothing changed: no negative balance, original still COMPLETED, and the half-built reversal
		// (its row was already saved before the balance check failed) was rolled back.
		assertThat(balanceOf(sahilAccount)).isEqualByComparingTo("400.00");
		assertThat(balanceOf(qasimAccount)).isEqualByComparingTo("982.30");
		assertThat(transactionRepository.findById(transferId).orElseThrow().getStatus())
				.isEqualTo(TransactionStatus.COMPLETED);
		assertThat(transactionRepository.findByReversalOfId(transferId)).isEmpty();
	}

	@Test
	void onlyAdminCanReverse() throws Exception {
		String token = registerAndLogin(uniqueEmail());
		long accountId = createAccount(token);
		long depositId = deposit(token, accountId, "100.00").get("id").asLong();

		mockMvc.perform(authPost(token, "/api/transactions/" + depositId + "/reverse"))
				.andExpect(status().isForbidden());
	}

	// ---------- history ----------

	@Test
	void transactionHistoryAndDetailsRespectOwnership() throws Exception {
		String qasim = registerAndLogin(uniqueEmail());
		String sahil = registerAndLogin(uniqueEmail());
		long qasimAccount = createAccount(qasim);
		long sahilAccount = createAccount(sahil);
		deposit(qasim, qasimAccount, "500.00");
		long transferId = transfer(qasim, qasimAccount, sahilAccount, "100.00");
		String outsider = registerAndLogin(uniqueEmail());

		mockMvc.perform(get("/api/accounts/" + qasimAccount + "/transactions")
				.header(HttpHeaders.AUTHORIZATION, bearer(qasim)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.content[0].transactionType").value("TRANSFER"));

		// Both sides of a transfer may see it; an unrelated user may not.
		mockMvc.perform(get("/api/transactions/" + transferId).header(HttpHeaders.AUTHORIZATION, bearer(sahil)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ledgerEntries.length()").value(4));
		mockMvc.perform(get("/api/transactions/" + transferId).header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
				.andExpect(status().isForbidden());
	}

	// ---------- helpers ----------

	private long transfer(String token, long from, long to, String amount) throws Exception {
		String body = mockMvc.perform(authPost(token, "/api/transfers").content(transferBody(from, to, amount)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return readJson(body).get("id").asLong();
	}

	private String transferBody(long from, long to, String amount) {
		return "{\"sourceAccountId\": " + from + ", \"destinationAccountId\": " + to + ", \"amount\": " + amount + "}";
	}

	private long transactionCountFor(long accountId) {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM financial_transactions WHERE source_account_id = ? OR destination_account_id = ?",
				Long.class, accountId, accountId);
	}

	private BigDecimal balanceOf(long accountId) {
		return accountRepository.findById(accountId).orElseThrow().getBalance();
	}

	private BigDecimal systemBalance(SystemAccount systemAccount) {
		return balanceOf(accountRepository.findIdByAccountNumber(systemAccount.getAccountNumber()).orElseThrow());
	}

	private void setStatus(long accountId, AccountStatus status) {
		transactionTemplate.executeWithoutResult(tx -> accountRepository.findById(accountId).orElseThrow()
				.setStatus(status));
	}

	private void assertBalancedLedger(long transactionId, String expectedTotal) {
		List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(transactionId);
		BigDecimal debit = sum(entries, EntryType.DEBIT);
		BigDecimal credit = sum(entries, EntryType.CREDIT);
		assertThat(debit).isEqualByComparingTo(credit).isEqualByComparingTo(expectedTotal);
	}

	private BigDecimal sum(List<LedgerEntry> entries, EntryType type) {
		return entries.stream().filter(e -> e.getEntryType() == type).map(LedgerEntry::getAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
	}
}
