package com.yasaswimandava.paymentlab.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OutboxMessage(
        UUID eventId,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        String payload,
        Instant occurredAt,
        int attemptCount,
        String claimedBy) {

    public OutboxMessage {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(aggregateType, "aggregateType must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(claimedBy, "claimedBy must not be null");
    }
}
