package com.actify.financialledger.security.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.actify.financialledger.security.config.JwtProperties;

class JwtServiceTest {

	private static final String SECRET = "unit-test-secret-that-is-at-least-32-bytes";

	@Test
	void validTokenReturnsEmail() {
		JwtService jwtService = new JwtService(new JwtProperties(SECRET, 60_000));

		String token = jwtService.generateToken("qasim@example.test");

		assertThat(jwtService.extractEmail(token)).contains("qasim@example.test");
	}

	@Test
	void changingEmailInPayloadBreaksSignature() {
		JwtService jwtService = new JwtService(new JwtProperties(SECRET, 60_000));
		String token = jwtService.generateToken("qasim@example.test");

		// Someone tries to log in as another user by editing the token they already have.
		String forged = replaceInPayload(token, "qasim@example.test", "admin@example.test");

		assertThat(jwtService.parseClaims(forged)).isEmpty();
		assertThat(jwtService.extractEmail(forged)).isEmpty();
	}

	// Rewrites the payload part of the token but keeps the original signature, like an attacker would.
	private static String replaceInPayload(String token, String from, String to) {
		String[] parts = token.split("\\.");
		Base64.Decoder decoder = Base64.getUrlDecoder();
		Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
		String payload = new String(decoder.decode(parts[1]), StandardCharsets.UTF_8).replace(from, to);
		return parts[0] + "." + encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];
	}

	@Test
	void tamperedTokenIsRejected() {
		JwtService jwtService = new JwtService(new JwtProperties(SECRET, 60_000));
		String token = jwtService.generateToken("qasim@example.test");

		String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

		assertThat(jwtService.extractEmail(tampered)).isEmpty();
		assertThat(jwtService.extractEmail("not-a-jwt")).isEmpty();
	}

	@Test
	void tokenSignedWithOtherSecretIsRejected() {
		String token = new JwtService(new JwtProperties(SECRET, 60_000)).generateToken("qasim@example.test");
		JwtService other = new JwtService(new JwtProperties(SECRET + "-other", 60_000));

		assertThat(other.extractEmail(token)).isEmpty();
	}

	@Test
	void expiredTokenIsRejected() {
		JwtService jwtService = new JwtService(new JwtProperties(SECRET, -1_000));

		assertThat(jwtService.extractEmail(jwtService.generateToken("qasim@example.test"))).isEmpty();
	}

	@Test
	void shortSecretFailsAtStartup() {
		assertThatThrownBy(() -> new JwtService(new JwtProperties("too-short", 60_000)))
				.isInstanceOf(IllegalStateException.class);
	}
}
