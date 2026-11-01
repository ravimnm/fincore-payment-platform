package com.fincore.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fincore.backend.enums.PaymentStatus;

/**
 * One row of a wallet's payment history, seen from that wallet's side.
 */
public class PaymentHistoryItem {

    private final Long id;
    private final String paymentReference;
    private final String direction; // SENT or RECEIVED
    private final Long counterpartyWalletId;
    private final String counterpartyName;
    private final BigDecimal amount;
    private final String currency;
    private final PaymentStatus status;
    private final String description;
    private final LocalDateTime createdAt;
    private final LocalDateTime completedAt;

    public PaymentHistoryItem(
            Long id,
            String paymentReference,
            String direction,
            Long counterpartyWalletId,
            String counterpartyName,
            BigDecimal amount,
            String currency,
            PaymentStatus status,
            String description,
            LocalDateTime createdAt,
            LocalDateTime completedAt) {

        this.id = id;
        this.paymentReference = paymentReference;
        this.direction = direction;
        this.counterpartyWalletId = counterpartyWalletId;
        this.counterpartyName = counterpartyName;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.description = description;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
    }

    public Long getId() { return id; }
    public String getPaymentReference() { return paymentReference; }
    public String getDirection() { return direction; }
    public Long getCounterpartyWalletId() { return counterpartyWalletId; }
    public String getCounterpartyName() { return counterpartyName; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public String getDescription() { return description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
}
