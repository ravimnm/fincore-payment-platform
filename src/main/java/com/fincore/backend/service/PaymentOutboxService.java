
package com.fincore.backend.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;

import com.fincore.backend.dto.event.PaymentCompletedEvent;
import com.fincore.backend.entity.Payment;
import com.fincore.backend.entity.PaymentOutbox;
import com.fincore.backend.repository.PaymentOutboxRepository;

@Service
public class PaymentOutboxService {

    private final PaymentOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public PaymentOutboxService(
            PaymentOutboxRepository outboxRepository,
            ObjectMapper objectMapper
    ) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void recordPaymentCompleted(Payment payment) {

        UUID eventId = UUID.randomUUID();

        PaymentCompletedEvent event = new PaymentCompletedEvent(
                eventId,
                "PAYMENT_COMPLETED",
                payment.getId(),
                payment.getPaymentReference(),
                payment.getSenderWallet().getId(),
                payment.getReceiverWallet().getId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getCompletedAt().toString()
        );

        try {
            String payload = objectMapper.writeValueAsString(event);

            PaymentOutbox outbox = new PaymentOutbox(
                    eventId,
                    "PAYMENT",
                    payment.getId().toString(),
                    "PAYMENT_COMPLETED",
                    payload
            );

            outboxRepository.save(outbox);

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to create payment outbox event",
                    exception
            );
        }
    }
}
