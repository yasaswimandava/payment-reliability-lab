# ADR-005: Bounded provider resilience with explicit dead-letter recovery

- Status: Accepted
- Date: 2026-09-09

## Context

Payment authorization crosses a network boundary that can fail before a request is
sent, while the provider is processing it, or after the provider commits an effect.
Retrying every failure can amplify an outage or create duplicate charges. Never
retrying leaves recoverable transient failures unresolved. Kafka delivery can also
repeat the authorization command.

The lab needs to demonstrate both safe retry mechanics and the limits of what a
caller can know after an ambiguous timeout.

## Decision

Authorization runs asynchronously from `payments.received.v1` in its own Kafka
consumer group. The application sends the stable payment UUID to the provider as
an `Idempotency-Key`; redelivery and HTTP retries therefore identify the same
logical provider operation.

The HTTP client uses explicit connection and response timeouts. Resilience4j
retries only transport failures and server-side `5xx` responses, with three total
attempts and a fixed wait. Client errors and valid business declines are not
retried. A circuit breaker wraps the complete retry operation, so its failure
window counts logical authorizations rather than individual HTTP attempts.

When the bounded retry policy or open circuit prevents completion, the Kafka error
handler publishes the original event to `payments.received.v1.dlt`. The payment
remains `RECEIVED`; it is not falsely marked declined or authorized. Operators can
inspect and later reconcile the durable event.

A controllable in-process provider simulator exposes healthy, transient recovery,
decline, unavailable, and timeout modes. Its completed-result cache is keyed by
payment UUID so repeated successful requests converge on one provider effect. It
contains synthetic data and exists only as a learning and demonstration boundary.

## Consequences

- A transient provider outage can recover without manual intervention, but retries
  are strictly bounded.
- A business decline is a successful provider response and changes the payment to
  `DECLINED` without retrying.
- Kafka redelivery and HTTP retry reuse one provider idempotency identity.
- The circuit opens on repeated failed logical operations and protects the provider
  from retry storms.
- Exhausted work remains visible in a DLT while payment state remains honest.
- A timeout can still be ambiguous: the provider may have committed after the
  caller stopped waiting. Idempotency prevents a second provider effect, but a
  production system still needs provider lookup, reconciliation, and DLT replay
  controls before automatically resolving the local payment.
- The simulator control endpoint must not be exposed in a real payment service.
