package com.fincore.backend.messaging;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.fincore.backend.dto.event.PaymentCompletedEvent;
import com.fincore.backend.service.PaymentEventProcessingService;

import com.rabbitmq.client.Channel;

import tools.jackson.databind.ObjectMapper;

@Component
public class PaymentEventConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final PaymentEventProcessingService processingService;

    public PaymentEventConsumer(
            ObjectMapper objectMapper,
            PaymentEventProcessingService processingService) {

        this.objectMapper = objectMapper;
        this.processingService = processingService;
    }

    @RabbitListener(
            queues = RabbitMQConfig.PAYMENT_QUEUE
    )
    public void consume(
            Message message,
            Channel channel) throws IOException {

        long deliveryTag =
                message.getMessageProperties().getDeliveryTag();

        try {
            String payload = new String(
                    message.getBody(),
                    java.nio.charset.StandardCharsets.UTF_8
            );

            PaymentCompletedEvent event =
                    objectMapper.readValue(
                            message.getBody(),
                            PaymentCompletedEvent.class
                    );

            boolean processed =
                    processingService.process(event, payload);

            // Acknowledge only after the database transaction commits.
            channel.basicAck(deliveryTag, false);

            if (processed) {
                log.info(
                        "RabbitMQ message acknowledged. eventId={}",
                        event.eventId());
            }

        } catch (Exception exception) {

            log.error(
                    "Payment event processing failed. Sending message to DLQ. deliveryTag={}",
                    deliveryTag,
                    exception
            );

            // requeue=false: route to the configured dead-letter queue.
            channel.basicNack(deliveryTag, false, false);
        }
    }
}