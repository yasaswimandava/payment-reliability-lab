# Payment Reliability Lab

A production-minded reference system that demonstrates how a payment platform can
accept retries safely, process events reliably, and remain observable under failure.

## Reliability thesis

The transport may deliver a message more than once, but the business operation must
have exactly one effect.

## Implemented capabilities

- Create a payment through a Spring Boot API.
- Retrieve a payment by its ID.
- Require a merchant-scoped idempotency key.
- Replay the original result for an identical retry.
- Reject reuse of the same key with different payment details.
- Persist payments and idempotency records atomically in PostgreSQL.
- Resolve simultaneous identical requests to exactly one stored payment.
- Apply versioned database migrations with Flyway.
- Verify persistence and concurrency against real PostgreSQL with Testcontainers.

Later milestones add Kafka, the transactional outbox pattern, provider retries,
timeouts, circuit breakers, a dead-letter queue, OpenTelemetry, load tests, and a
small React operations console.

## Current architecture

```mermaid
flowchart LR
    Client -->|POST + idempotency key| API[Payment REST API]
    API --> Service[Payment application service]
    Service --> TX[Transaction boundary]
    TX --> Payments[PostgreSQL payment adapter]
    TX --> Keys[PostgreSQL idempotency adapter]
    Payments --> DB[(PostgreSQL)]
    Keys --> DB
```

The domain and application layers do not depend on a database. Repository ports
keep persistence replaceable, HTTP concerns remain in the API adapter, and a
transaction decorator commits the payment and idempotency record as one unit.

## Local prerequisites

- Java 17 or newer
- Maven 3.9+
- Docker Desktop

## Start PostgreSQL

Create your ignored local environment file, then start the database:

```bash
cp .env.example .env
docker compose up -d postgres
docker compose ps
```

The database listens only on `127.0.0.1`, and its data persists in a named Docker
volume across container restarts.

## Verify

```bash
mvn verify
```

The build fails if line coverage drops below 80%. Integration tests launch their own
isolated PostgreSQL container and apply every Flyway migration from an empty schema.

## Run the API

```bash
mvn spring-boot:run
```

Create a payment:

```bash
curl -i -X POST http://localhost:8080/api/v1/payments \
  -H 'Content-Type: application/json' \
  -H 'X-Merchant-Id: demo-merchant' \
  -H 'Idempotency-Key: demo-order-1001' \
  --data '{"amount":42.50,"currency":"USD"}'
```

Repeat the command without changing the body. The first response is `201 Created`
with `Idempotency-Replayed: false`; the retry is `200 OK` with
`Idempotency-Replayed: true` and the same payment ID.

Retrieve the payment using the `id` from either response:

```bash
curl -i http://localhost:8080/api/v1/payments/{payment-id}
```

An unknown payment ID returns an RFC 7807 problem response with HTTP `404`.

## Current scope

The project currently accepts and stores a payment in `RECEIVED` state. It does not
yet contact a payment-provider simulator or publish events. The next milestone adds
a transactional outbox so committing a payment and scheduling its Kafka event
cannot drift apart.

## Architecture decisions

- [ADR-001: Merchant-scoped idempotency](docs/decisions/ADR-001-merchant-scoped-idempotency.md)
- [ADR-002: Database-enforced concurrent idempotency](docs/decisions/ADR-002-database-enforced-concurrent-idempotency.md)
