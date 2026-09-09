# Payment Reliability Lab

A production-minded reference system that demonstrates how a payment platform can
accept retries safely, process events reliably, and remain observable under failure.

## Reliability thesis

The transport may deliver a message more than once, but the business operation must
have exactly one effect.

## First milestone

- Create a payment through a Spring Boot API.
- Require a merchant-scoped idempotency key.
- Replay the original result for an identical retry.
- Reject reuse of the same key with different payment details.

Later milestones add PostgreSQL, Kafka, the transactional outbox pattern, retries,
timeouts, circuit breakers, a dead-letter queue, OpenTelemetry, load tests, and a
small React operations console.

## Local prerequisites

- Java 17 or newer
- Maven 3.9+

## Verify

```bash
mvn verify
```
