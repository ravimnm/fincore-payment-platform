package com.fincore.backend.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fincore.backend.enums.PaymentStatus;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
        name = "payment_reference",
        nullable = false,
        unique = true,
        length = 50
    )
    private String paymentReference;

    @Column(
        name = "idempotency_key",
        nullable = false,
        unique = true,
        length = 100
    )
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "sender_wallet_id",
        nullable = false
    )
    private Wallet senderWallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "receiver_wallet_id",
        nullable = false
    )
    private Wallet receiverWallet;

    @Column(
        name = "amount",
        nullable = false,
        precision = 19,
        scale = 2
    )
    private BigDecimal amount;

    @Column(
        name = "currency",
        nullable = false,
        length = 3
    )
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(
        name = "status",
        nullable = false
    )
    private PaymentStatus status = PaymentStatus.CREATED;

    @Column(
        name = "description",
        columnDefinition = "TEXT"
    )
    private String description;

    @Column(
        name = "created_at",
        nullable = false
    )
    private LocalDateTime createdAt;

    @Column(
        name = "completed_at"
    )
    private LocalDateTime completedAt;

    @OneToMany(
        mappedBy = "payment",
        fetch = FetchType.LAZY,
        cascade = CascadeType.ALL
    )
    private List<LedgerEntry> ledgerEntries = new ArrayList<>();

    public Payment() {
    }

    public Payment(
        String paymentReference,
        String idempotencyKey,
        Wallet senderWallet,
        Wallet receiverWallet,
        BigDecimal amount,
        String currency,
        String description
    ) {
        this.paymentReference = paymentReference;
        this.idempotencyKey = idempotencyKey;
        this.senderWallet = senderWallet;
        this.receiverWallet = receiverWallet;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
        this.status = PaymentStatus.CREATED;
    }

    public Payment(
        Long id,
        String paymentReference,
        String idempotencyKey,
        Wallet senderWallet,
        Wallet receiverWallet,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String description,
        LocalDateTime createdAt,
        LocalDateTime completedAt
    ) {
        this.id = id;
        this.paymentReference = paymentReference;
        this.idempotencyKey = idempotencyKey;
        this.senderWallet = senderWallet;
        this.receiverWallet = receiverWallet;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.description = description;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
    }

    @PrePersist
    protected void onCreate() {

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if (currency == null) {
            currency = "INR";
        }

        if (status == null) {
            status = PaymentStatus.CREATED;
        }
    }

    public void addLedgerEntry(LedgerEntry ledgerEntry) {

        ledgerEntries.add(ledgerEntry);

        ledgerEntry.setPayment(this);
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

    public Wallet getSenderWallet() {
        return senderWallet;
    }

    public void setSenderWallet(Wallet senderWallet) {
        this.senderWallet = senderWallet;
    }

    public Wallet getReceiverWallet() {
        return receiverWallet;
    }

    public void setReceiverWallet(Wallet receiverWallet) {
        this.receiverWallet = receiverWallet;
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

    public List<LedgerEntry> getLedgerEntries() {
        return ledgerEntries;
    }

    public void setLedgerEntries(List<LedgerEntry> ledgerEntries) {
        this.ledgerEntries = ledgerEntries;
    }

    @Override
    public boolean equals(Object obj) {

        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        Payment other = (Payment) obj;

        return id != null && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {

        return getClass().hashCode();
    }

    @Override
    public String toString() {

        return "Payment{" +
                "id=" + id +
                ", paymentReference='" + paymentReference + '\'' +
                ", idempotencyKey='" + idempotencyKey + '\'' +
                ", amount=" + amount +
                ", currency='" + currency + '\'' +
                ", status=" + status +
                ", description='" + description + '\'' +
                ", createdAt=" + createdAt +
                ", completedAt=" + completedAt +
                '}';
    }
}