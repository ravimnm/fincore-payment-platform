package com.fincore.backend.repository;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fincore.backend.dto.event.PaymentCompletedEvent;

@Repository
public class PaymentEventInboxRepository {

    private final JdbcTemplate jdbcTemplate;

    public PaymentEventInboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean insertIfAbsent(
            PaymentCompletedEvent event,
            String payload) {

        String sql = """
                INSERT INTO payment_event_inbox (
                    event_id,
                    event_type,
                    payment_id,
                    payload
                )
                VALUES (?, ?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """;

        int rowsInserted = jdbcTemplate.update(
                sql,
                event.eventId(),
                event.eventType(),
                event.paymentId(),
                payload
        );

        return rowsInserted == 1;
    }

    public boolean existsByEventId(UUID eventId) {

        Boolean exists = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM payment_event_inbox
                    WHERE event_id = ?
                )
                """,
                Boolean.class,
                eventId
        );

        return Boolean.TRUE.equals(exists);
    }
}