package com.actify.financialledger.account.entity;

/**
 * Internal accounts needed as the "other side" of double-entry postings.
 * The account numbers are fixed so they can be found again after every restart.
 */
public enum SystemAccount {

	// Money that enters or leaves the bank through deposits and withdrawals.
	CASH("SYS-CASH"),
	// The 1.5% transfer fee earned by the bank.
	FEE_REVENUE("SYS-FEE-REVENUE"),
	// GST collected on the fee, owed to the government.
	GST_PAYABLE("SYS-GST-PAYABLE"),
	// Interest paid to customers is an expense for the bank.
	INTEREST_EXPENSE("SYS-INTEREST-EXPENSE");

	private final String accountNumber;

	SystemAccount(String accountNumber) {
		this.accountNumber = accountNumber;
	}

	public String getAccountNumber() {
		return accountNumber;
	}
}
