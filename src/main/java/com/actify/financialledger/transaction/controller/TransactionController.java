package com.actify.financialledger.transaction.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.actify.financialledger.common.PageResponse;
import com.actify.financialledger.security.service.AppUserPrincipal;
import com.actify.financialledger.transaction.dto.AmountRequest;
import com.actify.financialledger.transaction.dto.TransactionResponse;
import com.actify.financialledger.transaction.dto.TransferRequest;
import com.actify.financialledger.transaction.service.FinancialTransactionService;
import com.actify.financialledger.transaction.service.ReversalService;
import com.actify.financialledger.transaction.service.TransactionQueryService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api")
public class TransactionController {

	private final FinancialTransactionService financialTransactionService;
	private final ReversalService reversalService;
	private final TransactionQueryService transactionQueryService;

	public TransactionController(FinancialTransactionService financialTransactionService,
			ReversalService reversalService, TransactionQueryService transactionQueryService) {
		this.financialTransactionService = financialTransactionService;
		this.reversalService = reversalService;
		this.transactionQueryService = transactionQueryService;
	}

	@PostMapping("/accounts/{accountId}/deposit")
	@ResponseStatus(HttpStatus.CREATED)
	public TransactionResponse deposit(@PathVariable Long accountId, @Valid @RequestBody AmountRequest request,
			@AuthenticationPrincipal AppUserPrincipal currentUser) {
		return financialTransactionService.deposit(accountId, request.amount(), currentUser);
	}

	@PostMapping("/accounts/{accountId}/withdraw")
	@ResponseStatus(HttpStatus.CREATED)
	public TransactionResponse withdraw(@PathVariable Long accountId, @Valid @RequestBody AmountRequest request,
			@AuthenticationPrincipal AppUserPrincipal currentUser) {
		return financialTransactionService.withdraw(accountId, request.amount(), currentUser);
	}

	@PostMapping("/transfers")
	@ResponseStatus(HttpStatus.CREATED)
	public TransactionResponse transfer(@Valid @RequestBody TransferRequest request,
			@AuthenticationPrincipal AppUserPrincipal currentUser) {
		return financialTransactionService.transfer(request.sourceAccountId(), request.destinationAccountId(),
				request.amount(), currentUser);
	}

	@GetMapping("/transactions/{transactionId}")
	public TransactionResponse getTransaction(@PathVariable Long transactionId,
			@AuthenticationPrincipal AppUserPrincipal currentUser) {
		return transactionQueryService.getTransaction(transactionId, currentUser);
	}

	@GetMapping("/accounts/{accountId}/transactions")
	public PageResponse<TransactionResponse> getAccountTransactions(@PathVariable Long accountId,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
			@AuthenticationPrincipal AppUserPrincipal currentUser) {
		return transactionQueryService.getAccountTransactions(accountId, page, size, currentUser);
	}

	@PostMapping("/transactions/{transactionId}/reverse")
	@ResponseStatus(HttpStatus.CREATED)
	public TransactionResponse reverse(@PathVariable Long transactionId,
			@AuthenticationPrincipal AppUserPrincipal currentUser) {
		return reversalService.reverse(transactionId, currentUser);
	}
}
