package com.actify.financialledger.account.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.actify.financialledger.account.entity.Account;
import com.actify.financialledger.account.entity.SystemAccount;
import com.actify.financialledger.account.repository.AccountRepository;

/**
 * Creates the SYSTEM accounts on startup. Existing ones are left alone, so restarting the
 * application never creates duplicates.
 */
@Component
public class SystemAccountInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(SystemAccountInitializer.class);

	private final AccountRepository accountRepository;

	public SystemAccountInitializer(AccountRepository accountRepository) {
		this.accountRepository = accountRepository;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		for (SystemAccount systemAccount : SystemAccount.values()) {
			if (!accountRepository.existsByAccountNumber(systemAccount.getAccountNumber())) {
				accountRepository.save(Account.systemAccount(systemAccount.getAccountNumber()));
				log.info("Created system account {}", systemAccount.getAccountNumber());
			}
		}
	}
}
