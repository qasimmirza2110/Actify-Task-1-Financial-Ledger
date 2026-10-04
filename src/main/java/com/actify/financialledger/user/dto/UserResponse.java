package com.actify.financialledger.user.dto;

import java.time.Instant;

import com.actify.financialledger.user.entity.AppUser;
import com.actify.financialledger.user.entity.Role;

public record UserResponse(Long id, String email, Role role, Instant createdAt) {

	public static UserResponse from(AppUser user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.getCreatedAt());
	}
}
