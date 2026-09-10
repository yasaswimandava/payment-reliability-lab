package com.yasaswimandava.paymentlab.adapter.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yasaswimandava.paymentlab.domain.PaymentReceivedEvent;
import com.yasaswimandava.paymentlab.port.OutboxRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresOutboxRepository implements OutboxRepository {

    private static final String INSERT_EVENT = """
            insert into outbox_events (
                event_id,
                aggregate_type,
                aggregate_id,
                event_type,
                payload,
                occurred_at,
                available_at
            ) values (?, 'PAYMENT', ?, 'PAYMENT_RECEIVED', ?::jsonb, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PostgresOutboxRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(PaymentReceivedEvent event) {
        jdbcTemplate.update(
                INSERT_EVENT,
                event.eventId(),
                event.paymentId(),
                serialize(event),
                OffsetDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC));
    }

    private String serialize(PaymentReceivedEvent event) {
        PaymentReceivedPayload payload = new PaymentReceivedPayload(
                event.eventId(),
                event.paymentId(),
                event.merchantId(),
                event.amount(),
                event.currency().getCurrencyCode(),
                event.occurredAt());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "PaymentReceived event could not be serialized: " + event.eventId(),
                    exception);
        }
    }

    private record PaymentReceivedPayload(
            UUID eventId,
            UUID paymentId,
            String merchantId,
            BigDecimal amount,
            String currency,
            Instant occurredAt) {
    }
}
