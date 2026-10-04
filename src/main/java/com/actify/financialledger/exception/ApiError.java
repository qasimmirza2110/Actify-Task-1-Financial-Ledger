package com.actify.financialledger.exception;

import java.time.Instant;

import org.springframework.http.HttpStatus;

/**
 * Error body returned by every failed API call, so clients always get the same shape.
 */
public record ApiError(Instant timestamp, int status, String error, String message, String path) {

	public static ApiError of(HttpStatus status, String message, String path) {
		return new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, path);
	}
}
