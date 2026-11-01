
package com.fincore.backend.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fincore.backend.entity.PaymentOutbox;
import com.fincore.backend.repository.PaymentOutboxRepository;

@Service
public class PaymentOutboxPersistenceService {

    private final PaymentOutboxRepository repository;

    public PaymentOutboxPersistenceService(
            PaymentOutboxRepository repository
    ) {
        this.repository = repository;
    }

    @Transactional
    public void markPublished(UUID eventId) {

        PaymentOutbox event = repository.findById(eventId)
                .orElseThrow(() ->
                        new IllegalStateException("Outbox event not found")
                );

        event.markPublished();
    }

    @Transactional
    public void recordFailure(UUID eventId, String error) {

        PaymentOutbox event = repository.findById(eventId)
                .orElseThrow(() ->
                        new IllegalStateException("Outbox event not found")
                );

        event.recordFailure(
                error == null ? "Unknown publishing error" : error
        );
    }
}
