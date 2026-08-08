package com.fincore.backend.entity;

import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.Objects;

import com.fincore.backend.enums.LedgerEntryType;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "payment_id",
        nullable = false
    )
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "wallet_id",
        nullable = false
    )
    private Wallet wallet;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "entry_type", nullable = false)
    private LedgerEntryType entryType;

    @Column(
        name = "amount",
        nullable = false,
        precision = 19,
        scale = 2
    )
    private BigDecimal amount;

    @Column(
        name = "balance_after",
        nullable = false,
        precision = 19,
        scale = 2
    )
    private BigDecimal balanceAfter;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public LedgerEntry() {
    }

    public LedgerEntry(
        Payment payment,
        Wallet wallet,
        LedgerEntryType entryType,
        BigDecimal amount,
        BigDecimal balanceAfter
    ) {
        this.payment = payment;
        this.wallet = wallet;
        this.entryType = entryType;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
    }

    public LedgerEntry(
        Long id,
        Payment payment,
        Wallet wallet,
        LedgerEntryType entryType,
        BigDecimal amount,
        BigDecimal balanceAfter,
        LocalDateTime createdAt
    ) {
        this.id = id;
        this.payment = payment;
        this.wallet = wallet;
        this.entryType = entryType;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.createdAt = createdAt;
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

    public void setId(Long id) {
        this.id = id;
    }

    public Payment getPayment() {
        return payment;
    }

    public void setPayment(Payment payment) {
        this.payment = payment;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public void setWallet(Wallet wallet) {
        this.wallet = wallet;
    }

    public LedgerEntryType getEntryType() {
        return entryType;
    }

    public void setEntryType(LedgerEntryType entryType) {
        this.entryType = entryType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public void setBalanceAfter(BigDecimal balanceAfter) {
        this.balanceAfter = balanceAfter;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        LedgerEntry other = (LedgerEntry) obj;

        return id != null && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "LedgerEntry{" +
                "id=" + id +
                ", entryType=" + entryType +
                ", amount=" + amount +
                ", balanceAfter=" + balanceAfter +
                '}';
    }
}