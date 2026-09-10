# ADR-003: Transactional outbox for durable event publication

- Status: Accepted
- Date: 2026-09-09

## Context

A payment service commonly needs to persist business state in PostgreSQL and publish
an event to Kafka. Performing those writes independently creates a dual-write gap:
the payment can commit while publication fails, or an event can be published for a
payment transaction that later rolls back.

## Decision

The service stores one `PAYMENT_RECEIVED` outbox row in the same PostgreSQL
transaction as the payment and idempotency record. The row contains a stable event
ID, aggregate identity, event type, JSONB payload, occurrence and availability
timestamps, delivery state, attempt count, lease ownership, and the last error.

A unique constraint on aggregate type, aggregate ID, and event type prevents the
same business transition from staging duplicate events. Idempotent API replays
return the original payment and do not stage a new event.

Relay workers claim available rows using `FOR UPDATE SKIP LOCKED`, transition them
to `PROCESSING`, and record a worker-owned lease. Only the current owner can mark a
row `PUBLISHED` or reschedule it after failure. An expired processing lease can be
reclaimed so a worker crash cannot strand an event permanently.

## Consequences

- A committed new payment always has a durable event ready for publication.
- A rolled-back payment transaction cannot leave an orphan event.
- PostgreSQL remains the source of truth until Kafka acknowledges publication.
- Multiple relay workers can claim work without an external distributed lock.
- Publication is at-least-once, so consumers must deduplicate by event ID.
- Published-event retention and cleanup need an explicit operational policy.
- Phase 3 will add the Kafka publisher, bounded backoff, and an idempotent consumer.
