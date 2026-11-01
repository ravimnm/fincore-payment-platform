
package com.fincore.backend.service;

import java.util.concurrent.TimeUnit;

import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import com.fincore.backend.entity.PaymentOutbox;
import com.fincore.backend.messaging.RabbitMQConfig;

@Service
public class PaymentOutboxRelayService {

    private final RabbitTemplate rabbitTemplate;
    private final PaymentOutboxPersistenceService persistenceService;

    public PaymentOutboxRelayService(
            RabbitTemplate rabbitTemplate,
            PaymentOutboxPersistenceService persistenceService
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.persistenceService = persistenceService;
    }

    public void publish(PaymentOutbox event) {

        CorrelationData correlationData =
                new CorrelationData(event.getId().toString());

        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.PAYMENT_EXCHANGE,
                    "payment.completed",
                    event.getPayload(),
                    message -> {
                        message.getMessageProperties().setContentType(
                                MessageProperties.CONTENT_TYPE_JSON
                        );

                        message.getMessageProperties().setMessageId(
                                event.getId().toString()
                        );

                        message.getMessageProperties().setDeliveryMode(
                                MessageDeliveryMode.PERSISTENT
                        );

                        return message;
                    },
                    correlationData
            );

            CorrelationData.Confirm confirm = correlationData
                    .getFuture()
                    .get(10, TimeUnit.SECONDS);

            if (!confirm.ack()) {
                throw new IllegalStateException(
                        "RabbitMQ rejected event: " + confirm.reason()
                );
            }

            if (correlationData.getReturned() != null) {
                throw new IllegalStateException(
                        "RabbitMQ returned an unroutable event"
                );
            }

            persistenceService.markPublished(event.getId());

        } catch (Exception exception) {

            persistenceService.recordFailure(
                    event.getId(),
                    exception.toString()
            );
        }
    }
}
