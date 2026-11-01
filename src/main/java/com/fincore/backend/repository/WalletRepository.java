
package com.fincore.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.fincore.backend.entity.Wallet;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByUserId(Long userId);

    Optional<Wallet> findByWalletNumber(String walletNumber);

    boolean existsByWalletNumber(String walletNumber);

    /**
     * Searches active wallets by:
     * - Username
     * - Full name
     * - Phone number (digits only)
     * - User ID
     * - Wallet number
     *
     * Uses native PostgreSQL SQL to avoid Hibernate 7
     * function return-type inference problems.
     */
    @Query(
        value = """
            SELECT w.*
            FROM wallets w
            INNER JOIN users u ON u.id = w.user_id
            WHERE w.status = 'ACTIVE'
              AND u.status = 'ACTIVE'
              AND u.id <> :excludeUserId
              AND (
                    LOWER(u.username) LIKE :text ESCAPE E'\\\\'
                 OR LOWER(
                        CONCAT(
                            COALESCE(u.first_name, ''),
                            ' ',
                            COALESCE(u.last_name, '')
                        )
                    ) LIKE :text ESCAPE E'\\\\'
                 OR REGEXP_REPLACE(
                        COALESCE(u.phone, ''),
                        '[^0-9]',
                        '',
                        'g'
                    ) LIKE :digits
                 OR CAST(u.id AS TEXT) = :exact
                 OR w.wallet_number = :exact
              )
            ORDER BY u.first_name, u.username
            """,
        countQuery = """
            SELECT COUNT(*)
            FROM wallets w
            INNER JOIN users u ON u.id = w.user_id
            WHERE w.status = 'ACTIVE'
              AND u.status = 'ACTIVE'
              AND u.id <> :excludeUserId
              AND (
                    LOWER(u.username) LIKE :text ESCAPE E'\\\\'
                 OR LOWER(
                        CONCAT(
                            COALESCE(u.first_name, ''),
                            ' ',
                            COALESCE(u.last_name, '')
                        )
                    ) LIKE :text ESCAPE E'\\\\'
                 OR REGEXP_REPLACE(
                        COALESCE(u.phone, ''),
                        '[^0-9]',
                        '',
                        'g'
                    ) LIKE :digits
                 OR CAST(u.id AS TEXT) = :exact
                 OR w.wallet_number = :exact
              )
            """,
        nativeQuery = true
    )
    List<Wallet> search(
        @Param("text") String text,
        @Param("digits") String digits,
        @Param("exact") String exact,
        @Param("excludeUserId") Long excludeUserId,
        Pageable pageable
    );

    /**
     * Acquires a pessimistic write lock on a wallet.
     * Used during balance-changing transactions.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        "SELECT w FROM Wallet w WHERE w.id = :walletId"
    )
    Optional<Wallet> findByIdForUpdate(
        @Param("walletId") Long walletId
    );
}
