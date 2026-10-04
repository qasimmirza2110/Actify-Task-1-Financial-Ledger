package com.actify.financialledger.audit.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.actify.financialledger.audit.dto.AuditLogResponse;
import com.actify.financialledger.audit.entity.AuditAction;
import com.actify.financialledger.audit.entity.AuditLog;
import com.actify.financialledger.audit.entity.AuditStatus;
import com.actify.financialledger.audit.repository.AuditLogRepository;
import com.actify.financialledger.common.PageResponse;

@Service
public class AuditService {

	private static final int MAX_DETAILS_LENGTH = 500;

	private final AuditLogRepository auditLogRepository;

	public AuditService(AuditLogRepository auditLogRepository) {
		this.auditLogRepository = auditLogRepository;
	}

	/**
	 * Joins the caller's database transaction, so the audit row and the money movement are
	 * committed (or rolled back) together.
	 */
	@Transactional
	public void recordSuccess(String actor, AuditAction action, String entityType, Object entityId,
			String transactionReference, String details) {
		auditLogRepository.save(new AuditLog(actor, action, entityType, entityId == null ? null : entityId.toString(),
				transactionReference, AuditStatus.SUCCESS, shorten(details)));
	}

	/**
	 * Failures are recorded after the failed business transaction has already rolled back,
	 * so this always commits in its own transaction.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordFailure(String actor, AuditAction action, String details) {
		auditLogRepository.save(new AuditLog(actor, action, null, null, null, AuditStatus.FAILURE, shorten(details)));
	}

	@Transactional(readOnly = true)
	public PageResponse<AuditLogResponse> getAuditLogs(int page, int size) {
		return PageResponse.from(auditLogRepository.findAllByOrderByIdDesc(PageRequest.of(page, size)),
				AuditLogResponse::from);
	}

	private String shorten(String details) {
		if (details == null || details.length() <= MAX_DETAILS_LENGTH) {
			return details;
		}
		return details.substring(0, MAX_DETAILS_LENGTH);
	}
}
