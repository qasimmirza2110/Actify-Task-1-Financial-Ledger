package com.actify.financialledger.ledger.entity;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.Immutable;

import com.actify.financialledger.account.entity.Account;
import com.actify.financialledger.transaction.entity.FinancialTransaction;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * One line of the double-entry ledger. Every FinancialTransaction has at least one DEBIT and one
 * CREDIT line, and the debit total always equals the credit total.
 *
 * Ledger rows are never updated or deleted. Mistakes are corrected with a REVERSAL transaction.
 */
@Entity
@Immutable
@Table(name = "ledger_entries",
		indexes = {
				@Index(name = "idx_ledger_entries_transaction", columnList = "financial_transaction_id"),
				@Index(name = "idx_ledger_entries_account", columnList = "account_id") },
		check = @CheckConstraint(name = "chk_ledger_amount_positive", constraint = "amount > 0"))
public class LedgerEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "financial_transaction_id", nullable = false)
	private FinancialTransaction financialTransaction;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "account_id", nullable = false)
	private Account account;

	@Enumerated(EnumType.STRING)
	@Column(name = "entry_type", nullable = false, length = 10)
	private EntryType entryType;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal amount;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected LedgerEntry() {
	}

	private LedgerEntry(FinancialTransaction financialTransaction, Account account, EntryType entryType,
			BigDecimal amount) {
		this.financialTransaction = financialTransaction;
		this.account = account;
		this.entryType = entryType;
		this.amount = amount;
	}

	public static LedgerEntry debit(FinancialTransaction transaction, Account account, BigDecimal amount) {
		return new LedgerEntry(transaction, account, EntryType.DEBIT, amount);
	}

	public static LedgerEntry credit(FinancialTransaction transaction, Account account, BigDecimal amount) {
		return new LedgerEntry(transaction, account, EntryType.CREDIT, amount);
	}

	/**
	 * Same account and amount, opposite side. Used to build a reversal.
	 */
	public LedgerEntry opposite(FinancialTransaction reversalTransaction) {
		EntryType oppositeType = entryType == EntryType.DEBIT ? EntryType.CREDIT : EntryType.DEBIT;
		return new LedgerEntry(reversalTransaction, account, oppositeType, amount);
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public FinancialTransaction getFinancialTransaction() {
		return financialTransaction;
	}

	public Account getAccount() {
		return account;
	}

	public EntryType getEntryType() {
		return entryType;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
