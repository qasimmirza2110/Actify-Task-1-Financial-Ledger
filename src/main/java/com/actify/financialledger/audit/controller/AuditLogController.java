package com.actify.financialledger.audit.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.actify.financialledger.audit.dto.AuditLogResponse;
import com.actify.financialledger.audit.service.AuditService;
import com.actify.financialledger.common.PageResponse;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Read-only audit view for ADMIN users (the rule is in SecurityConfig).
 * There is intentionally no update or delete endpoint.
 */
@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

	private final AuditService auditService;

	public AuditLogController(AuditService auditService) {
		this.auditService = auditService;
	}

	@GetMapping
	public PageResponse<AuditLogResponse> getAuditLogs(@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return auditService.getAuditLogs(page, size);
	}
}
