package com.actify.financialledger.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.actify.financialledger.IntegrationTestSupport;
import com.actify.financialledger.audit.entity.AuditAction;
import com.actify.financialledger.audit.repository.AuditLogRepository;
import com.actify.financialledger.user.repository.AppUserRepository;

class AuthIT extends IntegrationTestSupport {

	@Autowired
	private AppUserRepository userRepository;

	@Autowired
	private AuditLogRepository auditLogRepository;

	@Test
	void registerStoresBcryptHashAndLoginReturnsJwt() throws Exception {
		String email = uniqueEmail();

		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(json("email", email, "password", PASSWORD)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.role").value("CUSTOMER"))
				.andExpect(jsonPath("$.passwordHash").doesNotExist());

		String hash = userRepository.findByEmail(email).orElseThrow().getPasswordHash();
		assertThat(hash).startsWith("$2").isNotEqualTo(PASSWORD);

		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(json("email", email, "password", PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.accessToken").isNotEmpty());
	}

	@Test
	void wrongPasswordReturns401AndIsAudited() throws Exception {
		String email = uniqueEmail();
		registerAndLogin(email);

		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(json("email", email, "password", "WrongPassword1")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Invalid email or password"));

		assertThat(auditLogRepository.findByActorAndAction(email, AuditAction.LOGIN_FAILED)).hasSize(1);
	}

	@Test
	void duplicateEmailReturns409() throws Exception {
		String email = uniqueEmail();
		registerAndLogin(email);

		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(json("email", email.toUpperCase(), "password", PASSWORD)))
				.andExpect(status().isConflict());
	}

	@Test
	void invalidRegistrationReturns400() throws Exception {
		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(json("email", "not-an-email", "password", "short")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.path").value("/api/auth/register"));
	}

	@Test
	void missingOrInvalidTokenReturns401() throws Exception {
		mockMvc.perform(get("/api/accounts/my"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));

		mockMvc.perform(get("/api/accounts/my").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.valid.token"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void auditLogsAreAdminOnly() throws Exception {
		String customerToken = registerAndLogin(uniqueEmail());

		mockMvc.perform(get("/api/audit-logs").header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/audit-logs").header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray());
	}
}
