package com.fincore.backend.gateway;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fincore.backend.entity.LedgerEntry;
import com.fincore.backend.entity.Wallet;

import jakarta.persistence.*;

@Entity
@Table(
    name = "gateway_transactions",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_gateway_order_id", columnNames = "gateway_order_id"),
        @UniqueConstraint(name = "uk_gateway_idempotency", columnNames = "idempotency_key")
    }
)
public class GatewayTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "gateway", nullable = false, length = 30)
    private String gateway;

    @Column(name = "gateway_order_id", nullable = false, unique = true)
    private String gatewayOrderId;

    @Column(name = "gateway_payment_id", unique = true)
    private String gatewayPaymentId;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "razorpay_signature")
    private String razorpaySignature;
    
    @OneToMany(
            mappedBy = "gatewayTransaction",
            fetch = FetchType.LAZY
    )
    private List<LedgerEntry> ledgerEntries =
            new ArrayList<>();

    public GatewayTransaction() {
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getGateway() {
        return gateway;
    }

    public void setGateway(String gateway) {
        this.gateway = gateway;
    }

    public String getGatewayOrderId() {
        return gatewayOrderId;
    }

    public void setGatewayOrderId(String gatewayOrderId) {
        this.gatewayOrderId = gatewayOrderId;
    }

    public String getGatewayPaymentId() {
        return gatewayPaymentId;
    }

    public void setGatewayPaymentId(String gatewayPaymentId) {
        this.gatewayPaymentId = gatewayPaymentId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public void setWallet(Wallet wallet) {
        this.wallet = wallet;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

	public String getRazorpaySignature() {
		return razorpaySignature;
	}

	public void setRazorpaySignature(String razorpaySignature) {
		this.razorpaySignature = razorpaySignature;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
	
	public List<LedgerEntry> getLedgerEntries() {
	    return ledgerEntries;
	}

	public void setLedgerEntries(List<LedgerEntry> ledgerEntries) {
	    this.ledgerEntries = ledgerEntries;
	}
}