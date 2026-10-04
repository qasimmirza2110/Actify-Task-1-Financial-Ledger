package com.actify.financialledger.security.service;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.actify.financialledger.security.config.JwtProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

	// HS256 needs a key of at least 256 bits (32 bytes).
	private static final int MIN_SECRET_BYTES = 32;

	private final SecretKey signingKey;
	private final long expirationMs;

	public JwtService(JwtProperties properties) {
		if (properties.secret() == null || properties.secret().getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("app.jwt.secret (LEDGER_JWT_SECRET) must be at least 32 characters long");
		}
		this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
		this.expirationMs = properties.expirationMs();
	}

	public String generateToken(String email) {
		Date now = new Date();
		return Jwts.builder()
				.subject(email)
				.issuedAt(now)
				.expiration(new Date(now.getTime() + expirationMs))
				.signWith(signingKey, Jwts.SIG.HS256)
				.compact();
	}

	/**
	 * Returns the claims of a valid token, or empty if the token is expired, tampered or malformed.
	 */
	public Optional<Claims> parseClaims(String token) {
		try {
			return Optional.of(Jwts.parser()
					.verifyWith(signingKey)
					.build()
					.parseSignedClaims(token)
					.getPayload());
		} catch (JwtException | IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	public Optional<String> extractEmail(String token) {
		return parseClaims(token).map(Claims::getSubject);
	}

	public long getExpirationMs() {
		return expirationMs;
	}
}
