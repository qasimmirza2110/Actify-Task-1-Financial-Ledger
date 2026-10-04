package com.actify.financialledger.account.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.actify.financialledger.account.dto.AccountResponse;
import com.actify.financialledger.account.service.AccountService;
import com.actify.financialledger.security.service.AppUserPrincipal;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

	private final AccountService accountService;

	public AccountController(AccountService accountService) {
		this.accountService = accountService;
	}

	// The owner is always the logged-in user; the request body is not used to choose the owner.
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public AccountResponse createAccount(@AuthenticationPrincipal AppUserPrincipal currentUser) {
		return accountService.createCustomerAccount(currentUser);
	}

	@GetMapping("/my")
	public List<AccountResponse> getMyAccounts(@AuthenticationPrincipal AppUserPrincipal currentUser) {
		return accountService.getMyAccounts(currentUser);
	}

	@GetMapping("/{accountId}")
	public AccountResponse getAccount(@PathVariable Long accountId,
			@AuthenticationPrincipal AppUserPrincipal currentUser) {
		return accountService.getAccount(accountId, currentUser);
	}
}
