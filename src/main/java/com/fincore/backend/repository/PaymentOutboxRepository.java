
package com.fincore.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fincore.backend.entity.PaymentOutbox;

public interface PaymentOutboxRepository
        extends JpaRepository<PaymentOutbox, UUID> {

    List<PaymentOutbox> findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
}
