package com.actify.financialledger.exception;

public class TransactionAlreadyReversedException extends RuntimeException {

	public TransactionAlreadyReversedException(String message) {
		super(message);
	}
}
