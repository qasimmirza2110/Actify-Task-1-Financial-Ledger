package com.actify.financialledger.exception;

public class UnauthorizedAccountAccessException extends RuntimeException {

	public UnauthorizedAccountAccessException(String message) {
		super(message);
	}
}
