package com.actify.financialledger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Common setup for the integration tests: full Spring context, real PostgreSQL test database
 * (see application-test.properties) and MockMvc that goes through the real security filters.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

	protected static final String ADMIN_EMAIL = "admin@test.local";
	protected static final String ADMIN_PASSWORD = "test-only-admin-password";
	protected static final String PASSWORD = "test-only-password";

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected ObjectMapper objectMapper;

	@Autowired
	protected JdbcTemplate jdbcTemplate;

	// Whatever a test did (including failed operations), the ledger must still be consistent afterwards.
	@AfterEach
	void ledgerStaysConsistent() {
		LedgerInvariants.assertConsistent(jdbcTemplate);
	}

	protected String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@test.com";
	}

	protected String registerAndLogin(String email) throws Exception {
		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(json("email", email, "password", PASSWORD)))
				.andExpect(status().isCreated());
		return login(email, PASSWORD);
	}

	protected String login(String email, String password) throws Exception {
		String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(json("email", email, "password", password)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body).get("accessToken").asString();
	}

	protected String adminToken() throws Exception {
		return login(ADMIN_EMAIL, ADMIN_PASSWORD);
	}

	protected long createAccount(String token) throws Exception {
		String body = mockMvc.perform(post("/api/accounts").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body).get("id").asLong();
	}

	protected JsonNode deposit(String token, long accountId, String amount) throws Exception {
		String body = mockMvc.perform(authPost(token, "/api/accounts/" + accountId + "/deposit")
				.content("{\"amount\": " + amount + "}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body);
	}

	protected MockHttpServletRequestBuilder authPost(String token, String url) {
		return post(url).header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON);
	}

	protected String bearer(String token) {
		return "Bearer " + token;
	}

	protected JsonNode readJson(String body) {
		return objectMapper.readTree(body);
	}

	// Builds a small JSON object from key/value pairs, e.g. json("email", "a@b.com").
	protected String json(String... keyValues) {
		StringBuilder builder = new StringBuilder("{");
		for (int i = 0; i < keyValues.length; i += 2) {
			if (i > 0) {
				builder.append(',');
			}
			builder.append('"').append(keyValues[i]).append("\":\"").append(keyValues[i + 1]).append('"');
		}
		return builder.append('}').toString();
	}
}
