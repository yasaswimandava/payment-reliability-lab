package com.yasaswimandava.paymentlab.adapter.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yasaswimandava.paymentlab.domain.OutboxMessage;
import com.yasaswimandava.paymentlab.domain.PaymentReceivedEvent;
import com.yasaswimandava.paymentlab.port.OutboxRepository;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
    private static final String CLAIM_AVAILABLE = """
            with candidates as (
                select event_id
                from outbox_events
                where (status = 'PENDING' and available_at <= ?)
                   or (status = 'PROCESSING' and locked_at < ?)
                order by available_at, occurred_at, event_id
                for update skip locked
                limit ?
            )
            update outbox_events event
            set status = 'PROCESSING',
                locked_at = ?,
                lock_owner = ?,
                attempt_count = event.attempt_count + 1,
                last_error = null
            from candidates
            where event.event_id = candidates.event_id
            returning event.event_id,
                      event.aggregate_type,
                      event.aggregate_id,
                      event.event_type,
                      event.payload::text as payload,
                      event.occurred_at,
                      event.attempt_count,
                      event.lock_owner
            """;
    private static final String MARK_PUBLISHED = """
            update outbox_events
            set status = 'PUBLISHED',
                published_at = ?,
                locked_at = null,
                lock_owner = null,
                last_error = null
            where event_id = ?
              and status = 'PROCESSING'
              and lock_owner = ?
            """;
    private static final String RESCHEDULE = """
            update outbox_events
            set status = 'PENDING',
                available_at = ?,
                locked_at = null,
                lock_owner = null,
                last_error = ?
            where event_id = ?
              and status = 'PROCESSING'
              and lock_owner = ?
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

    @Override
    public List<OutboxMessage> claimAvailable(
            String workerId,
            int batchSize,
            Instant claimAt,
            Duration lockTimeout) {
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("workerId must not be blank");
        }
        if (batchSize < 1 || batchSize > 1_000) {
            throw new IllegalArgumentException("batchSize must be between 1 and 1000");
        }
        if (lockTimeout == null || lockTimeout.isNegative() || lockTimeout.isZero()) {
            throw new IllegalArgumentException("lockTimeout must be positive");
        }

        OffsetDateTime claimedAt = OffsetDateTime.ofInstant(claimAt, ZoneOffset.UTC);
        OffsetDateTime staleBefore = OffsetDateTime.ofInstant(
                claimAt.minus(lockTimeout), ZoneOffset.UTC);
        return jdbcTemplate.query(
                CLAIM_AVAILABLE,
                this::mapMessage,
                claimedAt,
                staleBefore,
                batchSize,
                claimedAt,
                workerId);
    }

    @Override
    public boolean markPublished(UUID eventId, String workerId, Instant publishedAt) {
        return jdbcTemplate.update(
                MARK_PUBLISHED,
                OffsetDateTime.ofInstant(publishedAt, ZoneOffset.UTC),
                eventId,
                workerId) == 1;
    }

    @Override
    public boolean reschedule(
            UUID eventId,
            String workerId,
            Instant availableAt,
            String error) {
        return jdbcTemplate.update(
                RESCHEDULE,
                OffsetDateTime.ofInstant(availableAt, ZoneOffset.UTC),
                error,
                eventId,
                workerId) == 1;
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

    private OutboxMessage mapMessage(ResultSet resultSet, int rowNumber) throws SQLException {
        return new OutboxMessage(
                resultSet.getObject("event_id", UUID.class),
                resultSet.getString("aggregate_type"),
                resultSet.getObject("aggregate_id", UUID.class),
                resultSet.getString("event_type"),
                resultSet.getString("payload"),
                resultSet.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                resultSet.getInt("attempt_count"),
                resultSet.getString("lock_owner"));
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
