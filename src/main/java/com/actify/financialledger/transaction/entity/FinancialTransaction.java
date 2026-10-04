package com.actify.financialledger.transaction.entity;

import java.math.BigDecimal;
import java.time.Instant;

import com.actify.financialledger.account.entity.Account;

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
import jakarta.persistence.UniqueConstraint;

/**
 * One business operation (deposit, withdrawal, transfer, reversal or interest credit).
 * The actual money movement is recorded in its LedgerEntry rows.
 *
 * Named FinancialTransaction so it is not confused with Spring's @Transactional.
 */
@Entity
@Table(name = "financial_transactions",
		indexes = {
				@Index(name = "idx_fin_tx_source_created", columnList = "source_account_id, created_at"),
				@Index(name = "idx_fin_tx_destination", columnList = "destination_account_id") },
		// A transaction can be reversed only once, enforced by the database as well.
		uniqueConstraints = @UniqueConstraint(name = "uk_fin_tx_reversal_of", columnNames = "reversal_of_id"))
public class FinancialTransaction {

	private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 60)
	private String reference;

	@Enumerated(EnumType.STRING)
	@Column(name = "transaction_type", nullable = false, length = 20)
	private TransactionType transactionType;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal amount;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal fee;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal gst;

	@Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
	private BigDecimal totalAmount;

	// Account the money comes from (null for deposits and interest).
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "source_account_id")
	private Account sourceAccount;

	// Account the money goes to (null for withdrawals).
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "destination_account_id")
	private Account destinationAccount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TransactionStatus status;

	// Set only on REVERSAL transactions: the original transaction being reversed.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reversal_of_id")
	private FinancialTransaction reversalOf;

	// Email of the user who started it, or SYSTEM for scheduled interest.
	@Column(name = "initiated_by", nullable = false, length = 255)
	private String initiatedBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected FinancialTransaction() {
	}

	private FinancialTransaction(String reference, TransactionType transactionType, BigDecimal amount, BigDecimal fee,
			BigDecimal gst, Account sourceAccount, Account destinationAccount, String initiatedBy) {
		this.reference = reference;
		this.transactionType = transactionType;
		this.amount = amount;
		this.fee = fee;
		this.gst = gst;
		this.totalAmount = amount.add(fee).add(gst);
		this.sourceAccount = sourceAccount;
		this.destinationAccount = destinationAccount;
		this.status = TransactionStatus.COMPLETED;
		this.initiatedBy = initiatedBy;
	}

	public static FinancialTransaction deposit(String reference, Account account, BigDecimal amount,
			String initiatedBy) {
		return new FinancialTransaction(reference, TransactionType.DEPOSIT, amount, ZERO_MONEY,
				ZERO_MONEY, null, account, initiatedBy);
	}

	public static FinancialTransaction withdrawal(String reference, Account account, BigDecimal amount,
			String initiatedBy) {
		return new FinancialTransaction(reference, TransactionType.WITHDRAWAL, amount, ZERO_MONEY,
				ZERO_MONEY, account, null, initiatedBy);
	}

	public static FinancialTransaction transfer(String reference, Account source, Account destination,
			BigDecimal amount, BigDecimal fee, BigDecimal gst, String initiatedBy) {
		return new FinancialTransaction(reference, TransactionType.TRANSFER, amount, fee, gst, source, destination,
				initiatedBy);
	}

	public static FinancialTransaction interestCredit(String reference, Account account, BigDecimal interest) {
		return new FinancialTransaction(reference, TransactionType.INTEREST_CREDIT, interest, ZERO_MONEY,
				ZERO_MONEY, null, account, "SYSTEM");
	}

	/**
	 * Same amounts as the original, with source and destination swapped because the money flows back.
	 */
	public static FinancialTransaction reversal(String reference, FinancialTransaction original,
			String initiatedBy) {
		FinancialTransaction reversal = new FinancialTransaction(reference, TransactionType.REVERSAL,
				original.getAmount(), original.getFee(), original.getGst(), original.getDestinationAccount(),
				original.getSourceAccount(), initiatedBy);
		reversal.reversalOf = original;
		return reversal;
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}

	// The original row is kept; only its status changes. Its ledger entries are never touched.
	public void markReversed() {
		status = TransactionStatus.REVERSED;
	}

	public Long getId() {
		return id;
	}

	public String getReference() {
		return reference;
	}

	public TransactionType getTransactionType() {
		return transactionType;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public BigDecimal getFee() {
		return fee;
	}

	public BigDecimal getGst() {
		return gst;
	}

	public BigDecimal getTotalAmount() {
		return totalAmount;
	}

	public Account getSourceAccount() {
		return sourceAccount;
	}

	public Account getDestinationAccount() {
		return destinationAccount;
	}

	public TransactionStatus getStatus() {
		return status;
	}

	public FinancialTransaction getReversalOf() {
		return reversalOf;
	}

	public String getInitiatedBy() {
		return initiatedBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
