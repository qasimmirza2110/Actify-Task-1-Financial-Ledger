package com.actify.financialledger.user.service;

import java.util.Locale;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.actify.financialledger.audit.entity.AuditAction;
import com.actify.financialledger.audit.service.AuditService;
import com.actify.financialledger.exception.EmailAlreadyRegisteredException;
import com.actify.financialledger.security.service.JwtService;
import com.actify.financialledger.user.dto.LoginRequest;
import com.actify.financialledger.user.dto.LoginResponse;
import com.actify.financialledger.user.dto.RegisterRequest;
import com.actify.financialledger.user.dto.UserResponse;
import com.actify.financialledger.user.entity.AppUser;
import com.actify.financialledger.user.entity.Role;
import com.actify.financialledger.user.repository.AppUserRepository;

@Service
public class AuthService {

	private final AppUserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuthenticationManager authenticationManager;
	private final JwtService jwtService;
	private final AuditService auditService;

	public AuthService(AppUserRepository userRepository, PasswordEncoder passwordEncoder,
			AuthenticationManager authenticationManager, JwtService jwtService, AuditService auditService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.authenticationManager = authenticationManager;
		this.jwtService = jwtService;
		this.auditService = auditService;
	}

	/**
	 * Public registration always creates a CUSTOMER. An ADMIN can only come from AdminUserInitializer.
	 */
	@Transactional
	public UserResponse register(RegisterRequest request) {
		String email = normalizeEmail(request.email());
		if (userRepository.existsByEmail(email)) {
			throw new EmailAlreadyRegisteredException("Email is already registered");
		}
		AppUser user = userRepository.save(new AppUser(email, passwordEncoder.encode(request.password()), Role.CUSTOMER));
		auditService.recordSuccess(email, AuditAction.USER_REGISTERED, "USER", user.getId(), null, "User registered");
		return UserResponse.from(user);
	}

	public LoginResponse login(LoginRequest request) {
		String email = normalizeEmail(request.email());
		try {
			// AuthenticationManager loads the user and checks the password against the BCrypt hash.
			// It throws if the email or password is wrong, so a token is only created after a real check.
			authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));
		} catch (AuthenticationException e) {
			auditService.recordFailure(email, AuditAction.LOGIN_FAILED, "Invalid login attempt");
			throw e;
		}
		auditService.recordSuccess(email, AuditAction.LOGIN_SUCCESS, "USER", null, null, "User logged in");
		return new LoginResponse(jwtService.generateToken(email), "Bearer", jwtService.getExpirationMs());
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
