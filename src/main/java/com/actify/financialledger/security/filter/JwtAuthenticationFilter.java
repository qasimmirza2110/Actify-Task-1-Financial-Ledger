package com.actify.financialledger.security.filter;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import com.actify.financialledger.security.service.AppUserDetailsService;
import com.actify.financialledger.security.service.AppUserPrincipal;
import com.actify.financialledger.security.service.JwtService;

import io.jsonwebtoken.Claims;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Reads the Bearer token, and if it is valid, puts the user into the SecurityContext.
 * If the token is missing or invalid the request simply stays anonymous, and Spring Security
 * returns 401 for protected URLs.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtService jwtService;
	private final AppUserDetailsService userDetailsService;

	public JwtAuthenticationFilter(JwtService jwtService, AppUserDetailsService userDetailsService) {
		this.jwtService = jwtService;
		this.userDetailsService = userDetailsService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);

		if (header != null && header.startsWith(BEARER_PREFIX)) {
			String token = header.substring(BEARER_PREFIX.length());
			jwtService.parseClaims(token).ifPresent(claims -> authenticate(claims, request));
		}

		filterChain.doFilter(request, response);
	}

	private void authenticate(Claims claims, HttpServletRequest request) {
		if (claims.getSubject() == null) {
			return;
		}
		try {
			// Load the user again so a disabled or deleted user cannot keep using an old token.
			AppUserPrincipal user = userDetailsService.loadUserByUsername(claims.getSubject());
			if (!user.isEnabled()) {
				return;
			}
			UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(user, null,
					user.getAuthorities());
			authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
			SecurityContextHolder.getContext().setAuthentication(authentication);
		} catch (UsernameNotFoundException e) {
			// Token belongs to a user that no longer exists: leave the request unauthenticated.
		}
	}
}
