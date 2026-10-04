package com.actify.financialledger.exception;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.actify.financialledger.audit.entity.AuditAction;
import com.actify.financialledger.audit.service.AuditService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private final AuditService auditService;

	public GlobalExceptionHandler(AuditService auditService) {
		this.auditService = auditService;
	}

	// ---------- 404 ----------

	@ExceptionHandler({ AccountNotFoundException.class, TransactionNotFoundException.class })
	public ResponseEntity<ApiError> handleNotFound(RuntimeException ex, HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
	}

	// ---------- 400 ----------

	@ExceptionHandler(InvalidTransactionException.class)
	public ResponseEntity<ApiError> handleBadRequest(RuntimeException ex, HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
		String message = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.sorted()
				.collect(Collectors.joining(", "));
		return build(HttpStatus.BAD_REQUEST, message, request);
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ApiError> handleParameterValidation(HandlerMethodValidationException ex,
			HttpServletRequest request) {
		String message = ex.getParameterValidationResults().stream()
				.flatMap(result -> result.getResolvableErrors().stream()
						.map(error -> result.getMethodParameter().getParameterName() + ": " + error.getDefaultMessage()))
				.sorted()
				.collect(Collectors.joining(", "));
		return build(HttpStatus.BAD_REQUEST, message, request);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
			HttpServletRequest request) {
		String message = ex.getConstraintViolations().stream()
				.map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
				.sorted()
				.collect(Collectors.joining(", "));
		return build(HttpStatus.BAD_REQUEST, message, request);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "Request body is missing or malformed", request);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + ex.getName() + "'", request);
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "Missing required parameter '" + ex.getParameterName() + "'", request);
	}

	// ---------- 401 / 403 ----------

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
		// Same message for unknown email and wrong password, so attackers cannot find valid emails.
		return build(HttpStatus.UNAUTHORIZED, "Invalid email or password", request);
	}

	@ExceptionHandler(UnauthorizedAccountAccessException.class)
	public ResponseEntity<ApiError> handleAccountAccess(UnauthorizedAccountAccessException ex,
			HttpServletRequest request) {
		auditFailure(AuditAction.ACCESS_DENIED, ex.getMessage() + " [" + request.getRequestURI() + "]");
		return build(HttpStatus.FORBIDDEN, ex.getMessage(), request);
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
		return build(HttpStatus.FORBIDDEN, "You are not allowed to perform this operation.", request);
	}

	// ---------- 409 / 422 ----------

	@ExceptionHandler(InsufficientBalanceException.class)
	public ResponseEntity<ApiError> handleInsufficientBalance(InsufficientBalanceException ex,
			HttpServletRequest request) {
		auditFailure(AuditAction.TRANSACTION_REJECTED, ex.getMessage() + " [" + request.getRequestURI() + "]");
		return build(HttpStatus.CONFLICT, ex.getMessage(), request);
	}

	@ExceptionHandler({ TransactionAlreadyReversedException.class, EmailAlreadyRegisteredException.class })
	public ResponseEntity<ApiError> handleConflict(RuntimeException ex, HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, ex.getMessage(), request);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex,
			HttpServletRequest request) {
		// Log only the constraint name: the full database message contains the conflicting values (e.g. an email).
		String constraint = ex.getCause() instanceof org.hibernate.exception.ConstraintViolationException violation
				? violation.getConstraintName()
				: "unknown";
		log.warn("Data integrity violation on {} (constraint {})", request.getRequestURI(), constraint);
		return build(HttpStatus.CONFLICT, "The request conflicts with existing data", request);
	}

	@ExceptionHandler(PessimisticLockingFailureException.class)
	public ResponseEntity<ApiError> handleLockFailure(PessimisticLockingFailureException ex,
			HttpServletRequest request) {
		log.warn("Lock failure on {}: {}", request.getRequestURI(), ex.getMessage());
		return build(HttpStatus.CONFLICT, "The account is busy with another operation, please retry", request);
	}

	@ExceptionHandler(FraudDetectedException.class)
	public ResponseEntity<ApiError> handleFraud(FraudDetectedException ex, HttpServletRequest request) {
		auditFailure(AuditAction.FRAUD_REJECTED, ex.getMessage() + " [" + request.getRequestURI() + "]");
		return build(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage(), request);
	}

	// ---------- Spring MVC errors (404 unknown URL, 405, 415) ----------

	@ExceptionHandler({ NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class,
			HttpMediaTypeNotSupportedException.class })
	public ResponseEntity<ApiError> handleMvcErrors(Exception ex, HttpServletRequest request) {
		HttpStatusCode statusCode = ((ErrorResponse) ex).getStatusCode();
		HttpStatus status = HttpStatus.valueOf(statusCode.value());
		return build(status, status.getReasonPhrase(), request);
	}

	// ---------- 500 ----------

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
		// Full details go to the server log only; the client never sees the stack trace.
		log.error("Unexpected error on {}", request.getRequestURI(), ex);
		return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
	}

	private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request) {
		return ResponseEntity.status(status).body(ApiError.of(status, message, request.getRequestURI()));
	}

	/**
	 * The business transaction has already rolled back when we get here, so the failure is written
	 * in a new transaction. If the audit write itself fails, we still return the original error.
	 */
	private void auditFailure(AuditAction action, String details) {
		try {
			auditService.recordFailure(currentActor(), action, details);
		} catch (DataAccessException e) {
			log.error("Could not write failure audit log for action {}", action, e);
		}
	}

	private String currentActor() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return authentication == null ? "ANONYMOUS" : authentication.getName();
	}
}
