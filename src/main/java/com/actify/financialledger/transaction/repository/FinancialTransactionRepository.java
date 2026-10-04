package com.actify.financialledger.transaction.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.actify.financialledger.transaction.entity.FinancialTransaction;
import com.actify.financialledger.transaction.entity.TransactionType;

import jakarta.persistence.LockModeType;

public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, Long> {

	// Locks the original transaction during a reversal so two admins cannot reverse it at the same time.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT t FROM FinancialTransaction t WHERE t.id = :id")
	Optional<FinancialTransaction> findByIdForUpdate(@Param("id") Long id);

	boolean existsByReference(String reference);

	Optional<FinancialTransaction> findByReversalOfId(Long originalTransactionId);

	@Query(value = """
			SELECT t FROM FinancialTransaction t
			LEFT JOIN FETCH t.sourceAccount
			LEFT JOIN FETCH t.destinationAccount
			LEFT JOIN FETCH t.reversalOf
			WHERE t.sourceAccount.id = :accountId OR t.destinationAccount.id = :accountId
			ORDER BY t.createdAt DESC, t.id DESC
			""",
			countQuery = """
			SELECT COUNT(t) FROM FinancialTransaction t
			WHERE t.sourceAccount.id = :accountId OR t.destinationAccount.id = :accountId
			""")
	Page<FinancialTransaction> findByAccountId(@Param("accountId") Long accountId, Pageable pageable);

	// Used by the fraud velocity rule: how many outgoing transactions did this account make recently?
	@Query("""
			SELECT COUNT(t) FROM FinancialTransaction t
			WHERE t.sourceAccount.id = :accountId
			  AND t.transactionType IN :types
			  AND t.createdAt >= :since
			""")
	long countOutgoingSince(@Param("accountId") Long accountId, @Param("types") Collection<TransactionType> types,
			@Param("since") Instant since);
}
