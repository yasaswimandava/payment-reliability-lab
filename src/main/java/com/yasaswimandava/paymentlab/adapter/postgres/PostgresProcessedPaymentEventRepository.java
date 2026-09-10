package com.yasaswimandava.paymentlab.adapter.postgres;

import com.yasaswimandava.paymentlab.domain.PaymentReceivedEvent;
import com.yasaswimandava.paymentlab.port.ProcessedPaymentEventRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresProcessedPaymentEventRepository
        implements ProcessedPaymentEventRepository {

    private static final String RECORD_IF_FIRST = """
            with accepted_event as (
                insert into processed_payment_events (
                    event_id,
                    payment_id,
                    event_type,
                    consumer_name,
                    processed_at
                ) values (?, ?, 'PAYMENT_RECEIVED', 'payment-projection-v1', ?)
                on conflict (event_id) do nothing
                returning event_id
            )
            insert into payment_event_projection (
                payment_id,
                source_event_id,
                merchant_id,
                amount,
                currency,
                received_at,
                projected_at
            )
            select ?, event_id, ?, ?, ?, ?, ?
            from accepted_event
            on conflict (payment_id) do nothing
            returning source_event_id
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgresProcessedPaymentEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean recordIfFirst(PaymentReceivedEvent event, Instant processedAt) {
        OffsetDateTime projectedAt = OffsetDateTime.ofInstant(processedAt, ZoneOffset.UTC);
        return !jdbcTemplate.query(
                        RECORD_IF_FIRST,
                        (resultSet, rowNumber) -> resultSet.getObject(1),
                        event.eventId(),
                        event.paymentId(),
                        projectedAt,
                        event.paymentId(),
                        event.merchantId(),
                        event.amount(),
                        event.currency().getCurrencyCode(),
                        OffsetDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC),
                        projectedAt)
                .isEmpty();
    }
}
