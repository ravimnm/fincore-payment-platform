package com.fincore.backend.gateway.dto;

import java.math.BigDecimal;

public class GatewayOrderResponse {

    private String gateway;
    private String orderId;
    private Long walletId;
    private BigDecimal amount;
    private Long amountInPaise;
    private String currency;
    private String status;
    private String idempotencyKey;

    public GatewayOrderResponse(
            String gateway,
            String orderId,
            Long walletId,
            BigDecimal amount,
            Long amountInPaise,
            String currency,
            String status,
            String idempotencyKey) {

        this.gateway = gateway;
        this.orderId = orderId;
        this.walletId = walletId;
        this.amount = amount;
        this.amountInPaise = amountInPaise;
        this.currency = currency;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
    }

    public String getGateway() {
        return gateway;
    }

    public String getOrderId() {
        return orderId;
    }

    public Long getWalletId() {
        return walletId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Long getAmountInPaise() {
        return amountInPaise;
    }

    public String getCurrency() {
        return currency;
    }

    public String getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}