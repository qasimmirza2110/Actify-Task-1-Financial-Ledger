package com.actify.financialledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Rules that must hold for the whole database at any time. Checked with plain SQL so the test does
 * not depend on the application code it is checking.
 */
public final class LedgerInvariants {

	private LedgerInvariants() {
	}

	public static void assertConsistent(JdbcTemplate jdbc) {
		// 1. Every financial transaction has ledger entries.
		assertThat(count(jdbc, """
				SELECT COUNT(*) FROM financial_transactions t
				WHERE NOT EXISTS (SELECT 1 FROM ledger_entries e WHERE e.financial_transaction_id = t.id)
				""")).as("transactions without ledger entries").isZero();

		// 2. Every single transaction is balanced: its debits equal its credits.
		assertThat(count(jdbc, """
				SELECT COUNT(*) FROM (
				  SELECT financial_transaction_id FROM ledger_entries
				  GROUP BY financial_transaction_id
				  HAVING SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE 0 END)
				      <> SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE 0 END)
				) unbalanced
				""")).as("unbalanced transactions").isZero();

		// 3. Every stored balance equals (credits - debits) in the ledger, so no balance changed without a ledger entry.
		assertThat(count(jdbc, """
				SELECT COUNT(*) FROM accounts a
				WHERE a.balance <> COALESCE((
				  SELECT SUM(CASE WHEN e.entry_type = 'CREDIT' THEN e.amount ELSE -e.amount END)
				  FROM ledger_entries e WHERE e.account_id = a.id), 0)
				""")).as("accounts whose balance does not match the ledger").isZero();

		// 4. No customer balance is negative.
		assertThat(count(jdbc, "SELECT COUNT(*) FROM accounts WHERE account_type = 'CUSTOMER' AND balance < 0"))
				.as("negative customer balances").isZero();

		// 5. All balances together are zero (each posting moves money between accounts, never creates it).
		BigDecimal total = jdbc.queryForObject("SELECT COALESCE(SUM(balance), 0) FROM accounts", BigDecimal.class);
		assertThat(total).isEqualByComparingTo("0");
	}

	private static long count(JdbcTemplate jdbc, String sql) {
		return jdbc.queryForObject(sql, Long.class);
	}
}
