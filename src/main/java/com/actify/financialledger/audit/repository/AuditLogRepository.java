package com.actify.financialledger.audit.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import com.actify.financialledger.audit.entity.AuditAction;
import com.actify.financialledger.audit.entity.AuditLog;

/**
 * Extends Repository instead of JpaRepository on purpose: only save and read methods exist,
 * so audit history cannot be deleted through the application.
 */
public interface AuditLogRepository extends Repository<AuditLog, Long> {

	AuditLog save(AuditLog auditLog);

	Page<AuditLog> findAllByOrderByIdDesc(Pageable pageable);

	List<AuditLog> findByTransactionReference(String transactionReference);

	List<AuditLog> findByActorAndAction(String actor, AuditAction action);
}
