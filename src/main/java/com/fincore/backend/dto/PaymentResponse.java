package com.fincore.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fincore.backend.enums.PaymentStatus;

public class PaymentResponse {

    private Long id;
    private String paymentReference;
    private String idempotencyKey;
    private Long senderWalletId;
    private Long receiverWalletId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    public PaymentResponse() {
    }

    public PaymentResponse(
            Long id,
            String paymentReference,
            String idempotencyKey,
            Long senderWalletId,
            Long receiverWalletId,
            BigDecimal amount,
            String currency,
            PaymentStatus status,
            String description,
            LocalDateTime createdAt,
            LocalDateTime completedAt) {

        this.id = id;
        this.paymentReference = paymentReference;
        this.idempotencyKey = idempotencyKey;
        this.senderWalletId = senderWalletId;
        this.receiverWalletId = receiverWalletId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.description = description;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public void setPaymentReference(String paymentReference) {
        this.paymentReference = paymentReference;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Long getSenderWalletId() {
        return senderWalletId;
    }

    public void setSenderWalletId(Long senderWalletId) {
        this.senderWalletId = senderWalletId;
    }

    public Long getReceiverWalletId() {
        return receiverWalletId;
    }

    public void setReceiverWalletId(Long receiverWalletId) {
        this.receiverWalletId = receiverWalletId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}