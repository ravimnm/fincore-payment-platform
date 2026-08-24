package com.fincore.backend.gateway;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface GatewayTransactionRepository
        extends JpaRepository<GatewayTransaction, Long> {

    Optional<GatewayTransaction> findByGatewayOrderId(
            String gatewayOrderId);

    Optional<GatewayTransaction> findByIdempotencyKey(
            String idempotencyKey);

    Optional<GatewayTransaction> findByGatewayPaymentId(
            String gatewayPaymentId);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT g
        FROM GatewayTransaction g
        WHERE g.gatewayOrderId = :orderId
    """)
    Optional<GatewayTransaction> findByGatewayOrderIdForUpdate(
            @Param("orderId") String orderId);
}