package com.actify.financialledger.transaction.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.actify.financialledger.account.entity.Account;
import com.actify.financialledger.account.repository.AccountRepository;
import com.actify.financialledger.account.service.AccountService;
import com.actify.financialledger.common.PageResponse;
import com.actify.financialledger.exception.AccountNotFoundException;
import com.actify.financialledger.exception.TransactionNotFoundException;
import com.actify.financialledger.exception.UnauthorizedAccountAccessException;
import com.actify.financialledger.ledger.repository.LedgerEntryRepository;
import com.actify.financialledger.security.service.AppUserPrincipal;
import com.actify.financialledger.transaction.dto.TransactionResponse;
import com.actify.financialledger.transaction.entity.FinancialTransaction;
import com.actify.financialledger.transaction.repository.FinancialTransactionRepository;

/**
 * Read-only transaction views with the same ownership rules as accounts.
 */
@Service
@Transactional(readOnly = true)
public class TransactionQueryService {

	private final FinancialTransactionRepository transactionRepository;
	private final LedgerEntryRepository ledgerEntryRepository;
	private final AccountRepository accountRepository;
	private final AccountService accountService;

	public TransactionQueryService(FinancialTransactionRepository transactionRepository,
			LedgerEntryRepository ledgerEntryRepository, AccountRepository accountRepository,
			AccountService accountService) {
		this.transactionRepository = transactionRepository;
		this.ledgerEntryRepository = ledgerEntryRepository;
		this.accountRepository = accountRepository;
		this.accountService = accountService;
	}

	public TransactionResponse getTransaction(Long transactionId, AppUserPrincipal currentUser) {
		FinancialTransaction transaction = transactionRepository.findById(transactionId)
				.orElseThrow(() -> new TransactionNotFoundException("Transaction " + transactionId + " not found"));
		if (!currentUser.isAdmin() && !involvesUser(transaction, currentUser)) {
			throw new UnauthorizedAccountAccessException("You do not have access to transaction " + transactionId);
		}
		return TransactionResponse.from(transaction, ledgerEntryRepository.findByTransactionId(transactionId));
	}

	public PageResponse<TransactionResponse> getAccountTransactions(Long accountId, int page, int size,
			AppUserPrincipal currentUser) {
		Account account = accountRepository.findById(accountId)
				.orElseThrow(() -> new AccountNotFoundException("Account " + accountId + " not found"));
		accountService.checkCanView(account, currentUser);
		return PageResponse.from(transactionRepository.findByAccountId(accountId, PageRequest.of(page, size)),
				TransactionResponse::from);
	}

	private boolean involvesUser(FinancialTransaction transaction, AppUserPrincipal currentUser) {
		Account source = transaction.getSourceAccount();
		Account destination = transaction.getDestinationAccount();
		return (source != null && source.isOwnedBy(currentUser.getId()))
				|| (destination != null && destination.isOwnedBy(currentUser.getId()));
	}
}
