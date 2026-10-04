package com.actify.financialledger.audit.dto;

import java.time.Instant;

import com.actify.financialledger.audit.entity.AuditAction;
import com.actify.financialledger.audit.entity.AuditLog;
import com.actify.financialledger.audit.entity.AuditStatus;

public record AuditLogResponse(Long id, String actor, AuditAction action, String entityType, String entityId,
		String transactionReference, AuditStatus status, String details, Instant timestamp) {

	public static AuditLogResponse from(AuditLog log) {
		return new AuditLogResponse(log.getId(), log.getActor(), log.getAction(), log.getEntityType(),
				log.getEntityId(), log.getTransactionReference(), log.getStatus(), log.getDetails(),
				log.getCreatedAt());
	}
}
