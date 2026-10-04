package com.actify.financialledger.ledger.service;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.actify.financialledger.account.entity.Account;
import com.actify.financialledger.exception.InsufficientBalanceException;
import com.actify.financialledger.exception.UnbalancedLedgerException;
import com.actify.financialledger.ledger.entity.EntryType;
import com.actify.financialledger.ledger.entity.LedgerEntry;
import com.actify.financialledger.ledger.repository.LedgerEntryRepository;

/**
 * The only place where account balances are changed. Because balances change only together with
 * ledger entries, the balance of an account always equals (credits - debits) in the ledger.
 */
@Service
public class LedgerService {

	private final LedgerEntryRepository ledgerEntryRepository;

	public LedgerService(LedgerEntryRepository ledgerEntryRepository) {
		this.ledgerEntryRepository = ledgerEntryRepository;
	}

	/**
	 * Must run inside the caller's transaction, and the caller must already hold the row locks on
	 * every account used in the entries.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public List<LedgerEntry> post(List<LedgerEntry> entries) {
		validateBalanced(entries);

		Set<Account> touchedAccounts = new LinkedHashSet<>();
		for (LedgerEntry entry : entries) {
			Account account = entry.getAccount();
			if (entry.getEntryType() == EntryType.CREDIT) {
				account.credit(entry.getAmount());
			} else {
				account.debit(entry.getAmount());
			}
			touchedAccounts.add(account);
		}

		// Last line of defence: even if a caller forgot to check, a customer balance can never go
		// below zero. Throwing here rolls back the whole database transaction.
		for (Account account : touchedAccounts) {
			if (account.isCustomerAccount() && account.getBalance().signum() < 0) {
				throw new InsufficientBalanceException(
						"Insufficient balance in account " + account.getAccountNumber());
			}
		}

		return ledgerEntryRepository.saveAll(entries);
	}

	/**
	 * Double-entry rule: total DEBIT must equal total CREDIT, and every line must be positive.
	 */
	public void validateBalanced(List<LedgerEntry> entries) {
		if (entries.size() < 2) {
			throw new UnbalancedLedgerException("A ledger posting needs at least one debit and one credit");
		}
		BigDecimal totalDebit = BigDecimal.ZERO;
		BigDecimal totalCredit = BigDecimal.ZERO;
		for (LedgerEntry entry : entries) {
			if (entry.getAmount() == null || entry.getAmount().signum() <= 0) {
				throw new UnbalancedLedgerException("Ledger entry amounts must be greater than zero");
			}
			if (entry.getEntryType() == EntryType.DEBIT) {
				totalDebit = totalDebit.add(entry.getAmount());
			} else {
				totalCredit = totalCredit.add(entry.getAmount());
			}
		}
		// compareTo, not equals: equals would treat 10.0 and 10.00 as different.
		if (totalDebit.compareTo(totalCredit) != 0) {
			throw new UnbalancedLedgerException(
					"Ledger is not balanced: debit " + totalDebit + " != credit " + totalCredit);
		}
	}
}
