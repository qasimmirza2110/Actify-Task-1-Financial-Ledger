package com.actify.financialledger.user.service;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.actify.financialledger.user.entity.AppUser;
import com.actify.financialledger.user.entity.Role;
import com.actify.financialledger.user.repository.AppUserRepository;

/**
 * Creates one ADMIN user on startup when ADMIN_EMAIL and ADMIN_PASSWORD are set.
 * Registration cannot create admins, so this is the only way to get one.
 */
@Component
public class AdminUserInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminUserInitializer.class);

	private final AppUserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final String adminEmail;
	private final String adminPassword;

	public AdminUserInitializer(AppUserRepository userRepository, PasswordEncoder passwordEncoder,
			@Value("${app.admin.email:}") String adminEmail, @Value("${app.admin.password:}") String adminPassword) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.adminEmail = adminEmail;
		this.adminPassword = adminPassword;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (adminEmail.isBlank() || adminPassword.isBlank()) {
			log.info("ADMIN_EMAIL/ADMIN_PASSWORD not set, no admin user created");
			return;
		}
		String email = adminEmail.trim().toLowerCase(Locale.ROOT);
		if (userRepository.existsByEmail(email)) {
			return;
		}
		userRepository.save(new AppUser(email, passwordEncoder.encode(adminPassword), Role.ADMIN));
		log.info("Admin user created");
	}
}
