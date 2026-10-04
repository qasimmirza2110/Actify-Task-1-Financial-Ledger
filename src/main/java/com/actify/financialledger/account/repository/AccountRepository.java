package com.actify.financialledger.account.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.actify.financialledger.account.entity.Account;

import jakarta.persistence.LockModeType;

public interface AccountRepository extends JpaRepository<Account, Long> {

	/**
	 * SELECT ... FOR UPDATE. Other transactions that want to lock the same row wait until
	 * this transaction commits or rolls back, so two requests cannot both use the same old balance.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT a FROM Account a WHERE a.id = :id")
	Optional<Account> findByIdForUpdate(@Param("id") Long id);

	/**
	 * Returns only the id (not the entity) on purpose. If the entity were loaded here without a lock,
	 * a later lock query in the same transaction would give back this cached, possibly stale copy.
	 */
	@Query("SELECT a.id FROM Account a WHERE a.accountNumber = :accountNumber")
	Optional<Long> findIdByAccountNumber(@Param("accountNumber") String accountNumber);

	boolean existsByAccountNumber(String accountNumber);

	List<Account> findByOwnerIdOrderByIdAsc(Long ownerId);

	@Query("""
			SELECT a.id FROM Account a
			WHERE a.accountType = com.actify.financialledger.account.entity.AccountType.CUSTOMER
			  AND a.status = com.actify.financialledger.account.entity.AccountStatus.ACTIVE
			  AND a.balance > 0
			ORDER BY a.id
			""")
	List<Long> findIdsEligibleForInterest();
}
