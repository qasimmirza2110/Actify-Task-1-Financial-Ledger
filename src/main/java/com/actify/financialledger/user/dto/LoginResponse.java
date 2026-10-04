package com.actify.financialledger.user.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresInMs) {
}
