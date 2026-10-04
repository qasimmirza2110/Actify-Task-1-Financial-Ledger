package com.actify.financialledger.audit.entity;

import java.time.Instant;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Append-only audit record. @Immutable tells Hibernate never to issue UPDATE statements for it,
 * and the repository has no delete methods.
 */
@Entity
@Immutable
@Table(name = "audit_logs", indexes = @Index(name = "idx_audit_logs_created_at", columnList = "created_at"))
public class AuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// Email of the user, SYSTEM for scheduled jobs, or ANONYMOUS.
	@Column(nullable = false, length = 255)
	private String actor;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private AuditAction action;

	@Column(name = "entity_type", length = 50)
	private String entityType;

	@Column(name = "entity_id", length = 50)
	private String entityId;

	@Column(name = "transaction_reference", length = 60)
	private String transactionReference;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AuditStatus status;

	@Column(length = 500)
	private String details;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected AuditLog() {
	}

	public AuditLog(String actor, AuditAction action, String entityType, String entityId, String transactionReference,
			AuditStatus status, String details) {
		this.actor = actor;
		this.action = action;
		this.entityType = entityType;
		this.entityId = entityId;
		this.transactionReference = transactionReference;
		this.status = status;
		this.details = details;
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getActor() {
		return actor;
	}

	public AuditAction getAction() {
		return action;
	}

	public String getEntityType() {
		return entityType;
	}

	public String getEntityId() {
		return entityId;
	}

	public String getTransactionReference() {
		return transactionReference;
	}

	public AuditStatus getStatus() {
		return status;
	}

	public String getDetails() {
		return details;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
