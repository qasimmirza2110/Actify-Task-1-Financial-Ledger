package com.actify.financialledger.account.entity;

import java.math.BigDecimal;
import java.time.Instant;

import com.actify.financialledger.user.entity.AppUser;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * A customer wallet or an internal SYSTEM accounting account.
 *
 * Balance rule for every account: CREDIT increases the balance and DEBIT decreases it.
 * Customer balances can never be negative (checked in LedgerService and by a database constraint).
 * SYSTEM accounts are accounting counterparties, so they may go negative: for example SYSTEM_CASH
 * shows -500.00 after a 500.00 deposit, meaning the bank holds 500.00 cash that it owes the customer.
 */
@Entity
@Table(name = "accounts",
		indexes = @Index(name = "idx_accounts_owner", columnList = "owner_id"),
		check = {
				@CheckConstraint(name = "chk_customer_balance_not_negative",
						constraint = "account_type <> 'CUSTOMER' OR balance >= 0"),
				@CheckConstraint(name = "chk_customer_account_has_owner",
						constraint = "account_type <> 'CUSTOMER' OR owner_id IS NOT NULL") })
public class Account {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "account_number", nullable = false, unique = true, length = 30)
	private String accountNumber;

	// Null for SYSTEM accounts.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id")
	private AppUser owner;

	@Enumerated(EnumType.STRING)
	@Column(name = "account_type", nullable = false, length = 20)
	private AccountType accountType;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal balance;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AccountStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Account() {
	}

	private Account(String accountNumber, AppUser owner, AccountType accountType) {
		this.accountNumber = accountNumber;
		this.owner = owner;
		this.accountType = accountType;
		this.balance = new BigDecimal("0.00");
		this.status = AccountStatus.ACTIVE;
	}

	public static Account customerAccount(String accountNumber, AppUser owner) {
		return new Account(accountNumber, owner, AccountType.CUSTOMER);
	}

	public static Account systemAccount(String accountNumber) {
		return new Account(accountNumber, null, AccountType.SYSTEM);
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
		updatedAt = createdAt;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	// Only LedgerService should call credit/debit, so every balance change has a matching ledger entry.
	public void credit(BigDecimal amount) {
		balance = balance.add(amount);
	}

	public void debit(BigDecimal amount) {
		balance = balance.subtract(amount);
	}

	public boolean isCustomerAccount() {
		return accountType == AccountType.CUSTOMER;
	}

	public boolean isActive() {
		return status == AccountStatus.ACTIVE;
	}

	public boolean isOwnedBy(Long userId) {
		// getId() on a lazy proxy does not hit the database.
		return owner != null && owner.getId().equals(userId);
	}

	public Long getId() {
		return id;
	}

	public String getAccountNumber() {
		return accountNumber;
	}

	public AppUser getOwner() {
		return owner;
	}

	public AccountType getAccountType() {
		return accountType;
	}

	public BigDecimal getBalance() {
		return balance;
	}

	public AccountStatus getStatus() {
		return status;
	}

	public void setStatus(AccountStatus status) {
		this.status = status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
