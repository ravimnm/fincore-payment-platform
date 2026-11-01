
package com.fincore.backend.dto.event;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentCompletedEvent(
        UUID eventId,
        String eventType,
        Long paymentId,
        String paymentReference,
        Long senderWalletId,
        Long receiverWalletId,
        BigDecimal amount,
        String currency,
        String occurredAt
) {
}
