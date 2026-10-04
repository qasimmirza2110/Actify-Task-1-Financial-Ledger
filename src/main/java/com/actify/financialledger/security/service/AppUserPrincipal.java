package com.actify.financialledger.security.service;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.actify.financialledger.user.entity.AppUser;
import com.actify.financialledger.user.entity.Role;
import com.actify.financialledger.user.entity.UserStatus;

/**
 * The logged-in user as seen by Spring Security. Services use getId() from here instead of
 * trusting any user id sent by the client.
 */
public class AppUserPrincipal implements UserDetails {

	private final Long id;
	private final String email;
	private final String passwordHash;
	private final Role role;
	private final boolean enabled;

	public AppUserPrincipal(AppUser user) {
		this.id = user.getId();
		this.email = user.getEmail();
		this.passwordHash = user.getPasswordHash();
		this.role = user.getRole();
		this.enabled = user.getStatus() == UserStatus.ACTIVE;
	}

	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public Role getRole() {
		return role;
	}

	public boolean isAdmin() {
		return role == Role.ADMIN;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public boolean isEnabled() {
		return enabled;
	}
}
