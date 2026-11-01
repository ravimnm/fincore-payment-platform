
package com.fincore.backend;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class RabbitMQConnectionTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    void shouldConnectToRabbitMQ() {

        Boolean connected = rabbitTemplate.execute(
                channel -> channel.isOpen()
        );

        assertTrue(Boolean.TRUE.equals(connected));
    }
}
