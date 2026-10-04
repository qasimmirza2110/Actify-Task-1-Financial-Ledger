package com.actify.financialledger.ledger.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.actify.financialledger.ledger.entity.LedgerEntry;

/**
 * Extends Repository instead of JpaRepository on purpose: there are no update or delete methods,
 * so ledger history is append-only.
 */
public interface LedgerEntryRepository extends Repository<LedgerEntry, Long> {

	<S extends LedgerEntry> List<S> saveAll(Iterable<S> entries);

	@Query("""
			SELECT e FROM LedgerEntry e
			JOIN FETCH e.account
			WHERE e.financialTransaction.id = :transactionId
			ORDER BY e.id
			""")
	List<LedgerEntry> findByTransactionId(@Param("transactionId") Long transactionId);

	// Only ids, so the accounts can be locked before any Account entity is loaded (see AccountRepository).
	@Query("SELECT DISTINCT e.account.id FROM LedgerEntry e WHERE e.financialTransaction.id = :transactionId")
	List<Long> findAccountIdsByTransactionId(@Param("transactionId") Long transactionId);
}
