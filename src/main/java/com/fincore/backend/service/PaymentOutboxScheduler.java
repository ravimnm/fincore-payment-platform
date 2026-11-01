
package com.fincore.backend.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fincore.backend.repository.PaymentOutboxRepository;

@Component
public class PaymentOutboxScheduler {

    private final PaymentOutboxRepository repository;
    private final PaymentOutboxRelayService relayService;

    public PaymentOutboxScheduler(
            PaymentOutboxRepository repository,
            PaymentOutboxRelayService relayService
    ) {
        this.repository = repository;
        this.relayService = relayService;
    }

    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {
        System.out.println("[OUTBOX] Scheduler triggered");

        var events = repository
                .findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();

        for (var event : events) {
            relayService.publish(event);
        }
        

        System.out.println("[OUTBOX] Pending events: " + events.size());
    }
}
