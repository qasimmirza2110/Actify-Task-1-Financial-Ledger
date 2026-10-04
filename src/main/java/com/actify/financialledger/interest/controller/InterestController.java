package com.actify.financialledger.interest.controller;

import java.time.YearMonth;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.actify.financialledger.interest.dto.InterestRunResponse;
import com.actify.financialledger.interest.service.InterestScheduler;

/**
 * ADMIN-only manual trigger for the monthly interest job, so it can be demonstrated without waiting
 * for the 1st of the month. It is idempotent: running it again for the same month credits nothing.
 */
@RestController
@RequestMapping("/api/admin/interest")
public class InterestController {

	private final InterestScheduler interestScheduler;

	public InterestController(InterestScheduler interestScheduler) {
		this.interestScheduler = interestScheduler;
	}

	// Example: POST /api/admin/interest/run?period=2026-09
	@PostMapping("/run")
	public InterestRunResponse run(@RequestParam YearMonth period) {
		return interestScheduler.runForPeriod(period);
	}
}
