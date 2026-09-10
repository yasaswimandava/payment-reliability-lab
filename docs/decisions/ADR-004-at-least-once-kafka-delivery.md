# ADR-004: At-least-once Kafka delivery with idempotent consumers

- Status: Accepted
- Date: 2026-09-09

## Context

The outbox closes the PostgreSQL and broker dual-write gap, but it cannot make the
database update and Kafka acknowledgment one atomic operation. If the relay
publishes successfully and stops before marking the row published, the same event
will be delivered again. Kafka can also redeliver a record after consumer failure
or a group rebalance.

## Decision

The relay publishes each outbox payload to `payments.received.v1`, keyed by payment
ID to preserve per-payment partition ordering. The stable event ID is present in
both the payload and the `event-id` Kafka header. The producer uses `acks=all` and
Kafka producer idempotence, and the relay waits for a bounded broker acknowledgment
before marking an outbox row `PUBLISHED`.

The consumer treats delivery as at-least-once. It records the event ID and creates
its payment projection in one PostgreSQL statement. A primary-key conflict on the
event ID turns a redelivery into a no-op, so committing the Kafka offset again is
safe. Invalid payloads fail processing rather than being silently acknowledged.

## Consequences

- A broker timeout leaves the event eligible for a later relay attempt.
- Producer idempotence reduces duplicates within one producer session but is not
  presented as an end-to-end exactly-once guarantee.
- Consumer business effects are protected independently from Kafka offsets.
- A consumer restart or rebalance can redeliver records without duplicating state.
- Event IDs are part of the public event contract and must remain stable.
- Schema compatibility and poison-event dead-letter handling remain explicit work.
