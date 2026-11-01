package com.fincore.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fincore.backend.dto.event.PaymentCompletedEvent;
import com.fincore.backend.repository.PaymentEventInboxRepository;

@Service
public class PaymentEventProcessingService {

    private static final Logger log =
            LoggerFactory.getLogger(PaymentEventProcessingService.class);

    private final PaymentEventInboxRepository inboxRepository;

    public PaymentEventProcessingService(
            PaymentEventInboxRepository inboxRepository) {
        this.inboxRepository = inboxRepository;
    }

    @Transactional
    public boolean process(
            PaymentCompletedEvent event,
            String payload) {

        if (event.eventId() == null) {
            throw new IllegalArgumentException("Event ID is missing");
        }

        if (event.paymentId() == null) {
            throw new IllegalArgumentException("Payment ID is missing");
        }

        if (!"PAYMENT_COMPLETED".equals(event.eventType())) {
            throw new IllegalArgumentException(
                    "Unsupported event type: " + event.eventType());
        }

        boolean inserted =
                inboxRepository.insertIfAbsent(event, payload);

        if (!inserted) {
            log.info(
                    "Duplicate event ignored. eventId={}",
                    event.eventId());

            return false;
        }

        log.info(
                """
                Payment completed event processed.
                eventId={}
                paymentId={}
                paymentReference={}
                senderWalletId={}
                receiverWalletId={}
                amount={}
                currency={}
                """,
                event.eventId(),
                event.paymentId(),
                event.paymentReference(),
                event.senderWalletId(),
                event.receiverWalletId(),
                event.amount(),
                event.currency()
        );

        return true;
    }
}