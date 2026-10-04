package com.actify.financialledger.account.service;

import java.security.SecureRandom;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.actify.financialledger.account.dto.AccountResponse;
import com.actify.financialledger.account.entity.Account;
import com.actify.financialledger.account.entity.SystemAccount;
import com.actify.financialledger.account.repository.AccountRepository;
import com.actify.financialledger.audit.entity.AuditAction;
import com.actify.financialledger.audit.service.AuditService;
import com.actify.financialledger.exception.AccountNotFoundException;
import com.actify.financialledger.exception.InvalidTransactionException;
import com.actify.financialledger.exception.UnauthorizedAccountAccessException;
import com.actify.financialledger.security.service.AppUserPrincipal;
import com.actify.financialledger.user.entity.AppUser;
import com.actify.financialledger.user.repository.AppUserRepository;

@Service
public class AccountService {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final AccountRepository accountRepository;
	private final AppUserRepository userRepository;
	private final AuditService auditService;

	public AccountService(AccountRepository accountRepository, AppUserRepository userRepository,
			AuditService auditService) {
		this.accountRepository = accountRepository;
		this.userRepository = userRepository;
		this.auditService = auditService;
	}

	@Transactional
	public AccountResponse createCustomerAccount(AppUserPrincipal currentUser) {
		AppUser owner = userRepository.getReferenceById(currentUser.getId());
		Account account = accountRepository.save(Account.customerAccount(generateAccountNumber(), owner));
		auditService.recordSuccess(currentUser.getEmail(), AuditAction.ACCOUNT_CREATED, "ACCOUNT", account.getId(),
				null, "Customer account " + account.getAccountNumber() + " created");
		return AccountResponse.from(account);
	}

	@Transactional(readOnly = true)
	public AccountResponse getAccount(Long accountId, AppUserPrincipal currentUser) {
		Account account = accountRepository.findById(accountId)
				.orElseThrow(() -> new AccountNotFoundException("Account " + accountId + " not found"));
		checkCanView(account, currentUser);
		return AccountResponse.from(account);
	}

	@Transactional(readOnly = true)
	public List<AccountResponse> getMyAccounts(AppUserPrincipal currentUser) {
		return accountRepository.findByOwnerIdOrderByIdAsc(currentUser.getId()).stream()
				.map(AccountResponse::from)
				.toList();
	}

	/**
	 * Locks all given accounts with SELECT ... FOR UPDATE, always in ascending id order.
	 *
	 * If request 1 locks A then B while request 2 locks B then A, both wait for each other forever
	 * (deadlock). Because every operation locks in the same order, that situation cannot happen.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public Map<Long, Account> lockAccountsInIdOrder(Collection<Long> accountIds) {
		Map<Long, Account> lockedAccounts = new LinkedHashMap<>();
		for (Long accountId : new TreeSet<>(accountIds)) {
			Account account = accountRepository.findByIdForUpdate(accountId)
					.orElseThrow(() -> new AccountNotFoundException("Account " + accountId + " not found"));
			lockedAccounts.put(accountId, account);
		}
		return lockedAccounts;
	}

	public Long getSystemAccountId(SystemAccount systemAccount) {
		return accountRepository.findIdByAccountNumber(systemAccount.getAccountNumber())
				.orElseThrow(() -> new IllegalStateException("System account " + systemAccount + " is missing"));
	}

	/**
	 * Owners can view their own accounts. ADMIN can view any account (including SYSTEM accounts).
	 */
	public void checkCanView(Account account, AppUserPrincipal currentUser) {
		if (!currentUser.isAdmin() && !account.isOwnedBy(currentUser.getId())) {
			throw new UnauthorizedAccountAccessException("You do not have access to account " + account.getId());
		}
	}

	/**
	 * Moving money out of or into an account through the API requires owning it, even for ADMIN.
	 */
	public void checkIsOwner(Account account, AppUserPrincipal currentUser) {
		if (!account.isOwnedBy(currentUser.getId())) {
			throw new UnauthorizedAccountAccessException("You do not have access to account " + account.getId());
		}
	}

	public void checkIsActive(Account account) {
		if (!account.isActive()) {
			throw new InvalidTransactionException(
					"Account " + account.getAccountNumber() + " is " + account.getStatus() + " and cannot be used");
		}
	}

	private String generateAccountNumber() {
		String accountNumber;
		do {
			accountNumber = "ACC" + String.format("%012d", RANDOM.nextLong(1_000_000_000_000L));
		} while (accountRepository.existsByAccountNumber(accountNumber));
		// The unique constraint on account_number is still the final safety net.
		return accountNumber;
	}
}
