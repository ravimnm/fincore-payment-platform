package com.fincore.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fincore.backend.entity.LedgerEntry;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByWalletIdOrderByCreatedAtDesc(Long walletId);

    List<LedgerEntry> findByPaymentIdOrderByCreatedAtAsc(Long paymentId);
}