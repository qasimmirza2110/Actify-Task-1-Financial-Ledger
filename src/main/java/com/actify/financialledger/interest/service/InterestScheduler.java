package com.actify.financialledger.interest.service;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.actify.financialledger.account.repository.AccountRepository;
import com.actify.financialledger.interest.dto.InterestRunResponse;

/**
 * Runs once a month (app.interest.cron, default 01:00 on the 1st) and credits interest for the
 * month that just ended, using each account's balance at the time the job runs.
 *
 * Note: this is safe to run twice on one server (see InterestService), but there is no
 * distributed lock, so on several servers each one would try and the duplicates would be skipped.
 */
@Component
public class InterestScheduler {

	private static final Logger log = LoggerFactory.getLogger(InterestScheduler.class);

	private final AccountRepository accountRepository;
	private final InterestService interestService;
	private final ZoneId zone;

	public InterestScheduler(AccountRepository accountRepository, InterestService interestService,
			@Value("${app.interest.zone}") String zone) {
		this.accountRepository = accountRepository;
		this.interestService = interestService;
		this.zone = ZoneId.of(zone);
	}

	@Scheduled(cron = "${app.interest.cron}", zone = "${app.interest.zone}")
	public void applyInterestForPreviousMonth() {
		runForPeriod(YearMonth.now(zone).minusMonths(1));
	}

	public InterestRunResponse runForPeriod(YearMonth period) {
		log.info("Monthly interest job started for {}", period);
		List<Long> accountIds = accountRepository.findIdsEligibleForInterest();
		int credited = 0;
		int skipped = 0;
		int failed = 0;

		for (Long accountId : accountIds) {
			try {
				if (interestService.applyMonthlyInterest(accountId, period)) {
					credited++;
				} else {
					skipped++;
				}
			} catch (RuntimeException e) {
				// Keep going with the other accounts; this one can be picked up by running the job again.
				failed++;
				log.error("Interest failed for account id {} and period {}", accountId, period, e);
			}
		}

		log.info("Monthly interest job finished for {}: credited={}, skipped={}, failed={}", period, credited,
				skipped, failed);
		return new InterestRunResponse(period.toString(), credited, skipped, failed);
	}
}
