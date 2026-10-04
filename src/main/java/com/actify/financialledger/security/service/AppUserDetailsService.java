package com.actify.financialledger.security.service;

import java.util.Locale;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.actify.financialledger.user.repository.AppUserRepository;

@Service
public class AppUserDetailsService implements UserDetailsService {

	private final AppUserRepository userRepository;

	public AppUserDetailsService(AppUserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public AppUserPrincipal loadUserByUsername(String email) {
		return userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
				.map(AppUserPrincipal::new)
				.orElseThrow(() -> new UsernameNotFoundException("User not found"));
	}
}
