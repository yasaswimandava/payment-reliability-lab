# Payment Reliability Lab

A production-minded Java and Spring Boot reference system for building payment APIs
that remain safe when clients retry requests, responses are lost, or multiple service
instances receive the same operation concurrently.

> **Reliability thesis:** delivery may happen more than once, but the business
> operation must have exactly one effect within the service and database boundary.

This is an independent portfolio project. It uses synthetic payment data and does
not contain employer code, confidential designs, or real payment credentials.

## Why this project exists

Creating a payment is easy on the happy path. The harder production question is
what happens when a client times out after the server commits and sends the same
request again. Without an idempotency contract, a normal retry can become a second
charge.

Payment Reliability Lab makes that failure mode explicit and demonstrates:

- merchant-scoped idempotency with request fingerprinting;
- atomic persistence of a payment, its idempotency record, and an outbox event;
- database-enforced duplicate protection across service instances;
- durable event staging with exclusive, recoverable relay claims;
- acknowledged Kafka publication and idempotent event consumption;
- idempotent provider authorization with HTTP timeouts and bounded retries;
- circuit breaking, dead-letter routing, and controllable fault injection;
- Prometheus metrics, OpenTelemetry traces, and correlated ECS JSON logs;
- deterministic concurrency testing against real PostgreSQL;
- clear HTTP semantics for first attempts, safe replays, conflicts, and errors;
- hexagonal boundaries that keep domain logic independent of HTTP and PostgreSQL.

## Current capabilities

| Capability | Behavior |
| --- | --- |
| Create payment | Accepts an amount and ISO 4217 currency code and stores a payment in `RECEIVED` state. |
| Safe retry | An identical retry returns the original payment instead of creating another one. |
| Conflict detection | Reusing a key with different payment details returns `409 Conflict`. |
| Merchant isolation | The same idempotency key can be used independently by different merchants. |
| Concurrent duplicate protection | Competing requests converge on one committed payment, even across service instances. |
| Durable persistence | PostgreSQL stores payments and idempotency records in one transaction. |
| Schema management | Flyway applies versioned database migrations at startup and during tests. |
| Verification | Unit and integration tests run with JUnit 5, MockMvc, Testcontainers, and JaCoCo. |
| Operations console | A responsive React and TypeScript UI demonstrates creation, replay, conflict, and lookup behavior through the real API. |
| Transactional outbox | Every new payment stages one durable `PAYMENT_RECEIVED` event in the payment transaction; safe replays stage none. |
| Relay ownership | PostgreSQL workers claim batches exclusively, recover expired leases, and owner-check publication or rescheduling. |
| Kafka event pipeline | A scheduled relay publishes to a three-partition topic only after broker acknowledgment, then marks the outbox row published. |
| Idempotent consumer | A Kafka consumer records each event ID and creates a queryable payment projection atomically; redelivery becomes a no-op. |
| Provider authorization | A separate consumer calls an HTTP provider boundary with the payment UUID as its stable idempotency key. |
| Bounded recovery | Explicit connection/read timeouts, three total attempts for transient failures, and a circuit breaker prevent retry storms. |
| Dead-letter recovery | Exhausted or circuit-rejected authorization events are written to `payments.received.v1.dlt`; their payments remain honestly `RECEIVED`. |
| Fault lab | The UI controls a synthetic provider with healthy, transient, decline, outage, and timeout modes and exposes attempt counters. |
| Operations overview | A durable-state dashboard highlights payment backlog, authorization outcomes, unpublished events, and circuit state. |
| Correlated telemetry | Prometheus metrics, OTLP traces in local Jaeger, ECS logs, and `X-Correlation-ID` connect system behavior across boundaries. |

## Architecture

```mermaid
flowchart LR
    Console[React operations console] -->|Vite development proxy| Controller
    Client[API client] -->|POST + merchant ID<br/>+ idempotency key| Controller[Payment REST API]
    Controller --> Application[Payment application service]
    Application --> Transaction[Transaction boundary]
    Transaction --> PaymentPort[Payment repository port]
    Transaction --> KeyPort[Idempotency repository port]
    Transaction --> OutboxPort[Outbox repository port]
    PaymentPort --> PaymentAdapter[PostgreSQL payment adapter]
    KeyPort --> KeyAdapter[PostgreSQL idempotency adapter]
    OutboxPort --> OutboxAdapter[PostgreSQL outbox adapter]
    PaymentAdapter --> DB[(PostgreSQL)]
    KeyAdapter --> DB
    OutboxAdapter --> DB
    OutboxAdapter --> Relay[Scheduled outbox relay]
    Relay -->|acks=all| Kafka[(Redpanda / Kafka API)]
    Kafka --> Consumer[Payment event consumer]
    Consumer --> Ledger[Processed-event ledger + projection]
    Ledger --> DB
    Kafka --> Authorization[Authorization consumer]
    Authorization --> Resilience[Timeout + retry + circuit breaker]
    Resilience --> Provider[Idempotent HTTP provider simulator]
    Authorization -->|exhausted failure| DLT[(payments.received.v1.dlt)]
    Authorization -->|AUTHORIZED or DECLINED| PaymentAdapter
    Console --> Operations[Operations overview API]
    Operations --> DB
    Application -. metrics .-> Prometheus[Actuator / Prometheus]
    Application -. OTLP traces .-> Jaeger[Jaeger trace UI]
```

The domain and application layers do not depend on Spring MVC or PostgreSQL.
Repository ports keep persistence replaceable, the API adapter owns HTTP concerns,
and a transaction decorator commits the payment, idempotency record, and outbox
event as one unit.

### Idempotent request flow

```mermaid
sequenceDiagram
    participant C as Client
    participant A as Payment API
    participant D as PostgreSQL

    C->>A: POST payment (merchant, key, payload)
    A->>D: Find (merchant, key)
    alt Key is new
        A->>D: Insert payment + key + outbox event in one transaction
        D-->>A: Commit
        A-->>C: 201 Created, replayed=false
    else Same key and same fingerprint
        D-->>A: Original payment ID
        A-->>C: 200 OK, replayed=true
    else Same key and different fingerprint
        A-->>C: 409 Conflict
    end
```

For simultaneous new requests, both transactions may initially see no record.
PostgreSQL's composite primary key on `(merchant_id, idempotency_key)` selects one
winner. The losing transaction rolls back its provisional payment, retries once in
a new transaction, and returns the committed payment as a replay.

This is an exactly-once **business effect inside one PostgreSQL boundary**, not a
claim of global exactly-once delivery. Kafka publication is at-least-once and
therefore requires consumer-side deduplication by event ID, introduced in Phase 3.

### Outbox lifecycle

```mermaid
stateDiagram-v2
    [*] --> PENDING: payment transaction commits
    PENDING --> PROCESSING: worker claims row
    PROCESSING --> PUBLISHED: broker acknowledges event
    PROCESSING --> PENDING: publication fails and is rescheduled
    PROCESSING --> PROCESSING: expired lease is reclaimed
```

Workers claim ordered batches with `FOR UPDATE SKIP LOCKED`, which lets multiple
instances make progress without claiming the same row. Publication and rescheduling
are guarded by worker ownership. If a worker stops after claiming, another worker
can recover the event after its lease expires.

The stored event payload is deliberately small and contains only the stable data a
consumer needs:

```json
{
  "eventId": "f35714c7-e520-4260-aaf9-203680fa7403",
  "paymentId": "7c02e8fe-9c21-4e13-81bc-b85185203b19",
  "merchantId": "demo-merchant",
  "amount": 42.50,
  "currency": "USD",
  "occurredAt": "2026-09-09T23:30:00Z"
}
```

### Provider authorization flow

```mermaid
sequenceDiagram
    participant K as Kafka
    participant A as Authorization consumer
    participant R as Resilience policy
    participant P as Provider
    participant D as PostgreSQL
    participant X as Dead-letter topic

    K->>A: PAYMENT_RECEIVED
    A->>R: authorize(payment ID)
    loop At most 3 attempts for transient failures
        R->>P: POST authorization + Idempotency-Key
        P-->>R: approval, decline, 5xx, or timeout
    end
    alt Provider returns a final decision
        R-->>A: APPROVED or DECLINED
        A->>D: Conditional state transition
    else Retry budget exhausted or circuit open
        A-->>X: Original event + failure headers
        Note over D: Payment remains RECEIVED
    end
```

The circuit breaker wraps the complete retry operation. Four failed logical
authorizations can open it; individual attempts do not inflate the breaker window.
A valid decline is never retried. A timeout is deliberately treated as ambiguous:
the stable provider idempotency key prevents a second provider effect, while the
DLT retains evidence for a future reconciliation workflow.

## Technology stack

- Java 17
- Spring Boot 3.5
- Spring MVC, Bean Validation, JDBC, and Actuator
- Spring for Apache Kafka
- Resilience4j Retry and CircuitBreaker
- Micrometer, Prometheus, and OpenTelemetry tracing
- Jaeger 2 for local trace exploration
- PostgreSQL 17
- Redpanda 26.2 through the Kafka API
- Flyway
- Docker Compose
- JUnit 5, MockMvc, Testcontainers, AssertJ, and Mockito
- JaCoCo with an enforced 80% line-coverage floor
- Maven
- React 19 and TypeScript 6
- Vite 8
- Vitest, Testing Library, ESLint, and V8 coverage

## Quick start

### Prerequisites

- Java 17 or newer
- Maven 3.9 or newer
- Docker Desktop or another Docker-compatible runtime with Compose
- `curl` for the examples

### 1. Clone and configure

```bash
git clone https://github.com/yasaswimandava/payment-reliability-lab.git
cd payment-reliability-lab
cp .env.example .env
```

The example credentials are for local development only. `.env` is ignored by Git.

### 2. Start PostgreSQL and Redpanda

```bash
docker compose up -d
docker compose ps
```

PostgreSQL listens on `127.0.0.1:55432`, Redpanda's Kafka API listens on
`127.0.0.1:19092`, and Jaeger accepts OTLP/HTTP on `127.0.0.1:4318` with its trace
UI at [http://localhost:16686](http://localhost:16686). PostgreSQL and Redpanda use
named Docker volumes so local data survives container restarts; local Jaeger trace
history is intentionally ephemeral.

### 3. Run the application

```bash
mvn spring-boot:run
```

Flyway creates the schema automatically. When the application is ready, verify it:

```bash
curl http://localhost:8080/actuator/health
```

Expected result:

```json
{"status":"UP"}
```

### 4. Run the operations console

In a second terminal:

```bash
cd frontend
npm install
npm run dev
```

Open [http://localhost:5173](http://localhost:5173). Vite proxies `/api` and
`/actuator` requests to Spring Boot on port `8080`, so local development does not
require permissive cross-origin configuration in the backend.

The console is designed to make the reliability contract visible:

1. Send the prefilled request to create a payment.
2. Send it again without changing the key or payload to observe a safe replay.
3. Change the amount but retain the key to observe an intentional `409 Conflict`.
4. Use the returned UUID to retrieve the durable payment record.
5. In **Fault lab**, choose a provider behavior and apply the scenario.
6. Create a payment, then retrieve it again to observe `AUTHORIZED`, `DECLINED`,
   or a safely unresolved `RECEIVED` state.

Within about one relay interval, the payment's outbox row becomes `PUBLISHED` and
the consumer creates one row in `payment_event_projection`. Inspect the topic
directly with:

```bash
docker compose exec redpanda \
  rpk topic consume payments.received.v1 --num 1
```

Inspect one exhausted authorization event with:

```bash
docker compose exec redpanda \
  rpk topic consume payments.received.v1.dlt --num 1 --format '%v\n'
```

## Try the reliability behavior

### Create a payment

```bash
curl -i -X POST http://localhost:8080/api/v1/payments \
  -H 'Content-Type: application/json' \
  -H 'X-Merchant-Id: demo-merchant' \
  -H 'Idempotency-Key: demo-order-1001' \
  --data '{"amount":42.50,"currency":"USD"}'
```

The first request returns `201 Created` and `Idempotency-Replayed: false`:

```json
{
  "id": "7c02e8fe-9c21-4e13-81bc-b85185203b19",
  "merchantId": "demo-merchant",
  "amount": 42.50,
  "currency": "USD",
  "status": "RECEIVED",
  "providerReference": null,
  "createdAt": "2026-09-09T23:30:00Z",
  "replayed": false
}
```

Repeat the exact request. It returns `200 OK`,
`Idempotency-Replayed: true`, and the same payment ID.

Now reuse the key with a different amount:

```bash
curl -i -X POST http://localhost:8080/api/v1/payments \
  -H 'Content-Type: application/json' \
  -H 'X-Merchant-Id: demo-merchant' \
  -H 'Idempotency-Key: demo-order-1001' \
  --data '{"amount":99.00,"currency":"USD"}'
```

The API rejects the ambiguous operation with `409 Conflict` and an RFC 7807
problem response.

### Retrieve a payment

Replace `{payment-id}` with the ID returned by the create request:

```bash
curl -i http://localhost:8080/api/v1/payments/{payment-id}
```

An unknown UUID returns `404 Not Found` as an RFC 7807 problem response.

## API contract

### `POST /api/v1/payments`

Required headers:

| Header | Purpose |
| --- | --- |
| `X-Merchant-Id` | Identifies the merchant namespace for the operation. |
| `Idempotency-Key` | Stable client-generated key reused for retries of the same logical operation. |
| `Content-Type: application/json` | Declares the JSON request body. |

Request body:

| Field | Type | Rules |
| --- | --- | --- |
| `amount` | Decimal | Required, at least `0.01`, up to 12 integer digits and 2 fractional digits. |
| `currency` | String | Required, three uppercase letters and a currency recognized by the JVM. |

Responses:

| Status | Meaning |
| --- | --- |
| `201 Created` | A new payment was stored. |
| `200 OK` | An identical request was safely replayed. |
| `400 Bad Request` | Headers or body are invalid. |
| `409 Conflict` | The idempotency key was already used with different payment details. |

### `GET /api/v1/payments/{paymentId}`

| Status | Meaning |
| --- | --- |
| `200 OK` | The payment was found. |
| `404 Not Found` | No payment exists for the supplied UUID. |

### Provider fault-lab API

`GET /api/v1/simulator/provider` returns the active mode, total HTTP attempts, and
successful authorization count. `PUT /api/v1/simulator/provider` resets the
counters and activates one of these synthetic modes:

| Mode | Behavior |
| --- | --- |
| `HEALTHY` | Approves on the first attempt. |
| `TRANSIENT_THEN_SUCCESS` | Fails the first two calls for each payment, then approves. |
| `DECLINE` | Returns a final business decline without retrying. |
| `UNAVAILABLE` | Returns `503` until the retry budget is exhausted. |
| `TIMEOUT` | Responds after the configured client deadline to demonstrate ambiguity. |

```bash
curl -X PUT http://localhost:8080/api/v1/simulator/provider \
  -H 'Content-Type: application/json' \
  --data '{"mode":"TRANSIENT_THEN_SUCCESS"}'
```

These endpoints are a local teaching surface, not a production administration API.

### `GET /api/v1/operations/overview`

Returns a point-in-time operator view derived from durable PostgreSQL state and the
provider circuit breaker. It reports payment counts by status, unpublished,
processing, and failed outbox counts, the oldest unpublished event timestamp, and
the current circuit state. Work is considered stale after 30 seconds. `health`
becomes `ATTENTION` when stale authorization or publication work exists, an outbox
event has failed, or the provider circuit is open.

For deeper signals, use:

| Surface | Local URL | Operator question |
| --- | --- | --- |
| Prometheus exposition | [http://localhost:8080/actuator/prometheus](http://localhost:8080/actuator/prometheus) | What is the request, Kafka, JVM, and authorization rate or latency? |
| Jaeger UI | [http://localhost:16686](http://localhost:16686) | Where did one observed operation spend time or fail? |
| ECS console logs | Application stdout | Which trace, span, and correlation ID produced this event? |

## Configuration

| Variable | Default | Description |
| --- | --- | --- |
| `POSTGRES_DB` | `payment_lab` | Local database name. |
| `POSTGRES_USER` | `payment_lab` | Local database user. |
| `POSTGRES_PASSWORD` | `payment_lab_local` for the app | Database password; Compose requires it through `.env`. |
| `POSTGRES_PORT` | `55432` | Host port mapped to PostgreSQL. |
| `DB_URL` | `jdbc:postgresql://localhost:${POSTGRES_PORT}/${POSTGRES_DB}` | Full JDBC URL override. |
| `KAFKA_PORT` | `19092` | Host port mapped to Redpanda's Kafka API. |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:19092` | Broker addresses used by the application. |
| `PAYMENT_EVENTS_TOPIC` | `payments.received.v1` | Versioned payment event topic. |
| `PAYMENT_EVENTS_CONSUMER_GROUP` | `payment-projection-v1` | Idempotent projection consumer group. |
| `PAYMENT_PROVIDER_BASE_URL` | `http://localhost:8080` | HTTP provider boundary; defaults to the local simulator. |
| `PAYMENT_PROVIDER_CONNECT_TIMEOUT` | `300ms` | Maximum time to establish a provider connection. |
| `PAYMENT_PROVIDER_READ_TIMEOUT` | `750ms` | Maximum time to wait for a provider response. |
| `PAYMENT_PROVIDER_MAX_ATTEMPTS` | `3` | Total attempts for a retriable provider call. |
| `PAYMENT_PROVIDER_RETRY_WAIT` | `150ms` | Wait between provider retry attempts. |
| `PAYMENT_PROVIDER_CONSUMER_GROUP` | `payment-provider-v1` | Authorization consumer group. |
| `PAYMENT_PROVIDER_DLT` | `payments.received.v1.dlt` | Topic for exhausted authorization work. |
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | `http://localhost:4318/v1/traces` | OTLP/HTTP destination for spans. |
| `OTEL_TRACES_EXPORT_ENABLED` | `true` | Enables local OTLP trace export. |
| `TRACING_SAMPLING_PROBABILITY` | `1.0` | Local trace sample rate; lower this in production. |
| `LOGGING_STRUCTURED_FORMAT` | `ecs` | Spring Boot structured console-log format. |
| `DEPLOYMENT_ENVIRONMENT` | `local` | OpenTelemetry resource environment attribute. |
| `JAEGER_UI_PORT` | `16686` | Local Jaeger browser UI port. |
| `OTLP_HTTP_PORT` | `4318` | Local Jaeger OTLP/HTTP receiver port. |
| `EVENTS_ENABLED` | `true` | Enables topic creation, relay scheduling, and consumption. |

The Hikari connection pool is intentionally small for a local lab: maximum 10
connections, minimum 2 idle connections, and a 3-second connection timeout.

## Verification

Run the complete build:

```bash
mvn verify
```

The suite verifies:

- first-request creation and identical-request replay;
- rejection of a reused key with changed payment details;
- request validation and not-found behavior;
- durable payment and idempotency persistence;
- concurrent identical requests producing one stored payment;
- atomic creation of exactly one outbox event for a new payment and none for a replay;
- exclusive relay claims, lease recovery, rescheduling, and owner-checked publication;
- Kafka record construction, bounded acknowledgments, and failure propagation;
- consumer payload validation and duplicate delivery handling;
- atomic processed-event ledger and payment projection persistence;
- provider request idempotency, HTTP failure classification, and state transitions;
- retry selectivity, bounded attempt counts, and logical-operation circuit breaking;
- simulator control modes and asynchronous authorization consumption;
- dead-letter publication after an exhausted provider operation;
- correlation ID propagation and durable operations-overview reporting;
- low-cardinality authorization outcome and duration metrics;
- application behavior through unit and HTTP integration tests;
- every Flyway migration against an empty Testcontainers PostgreSQL database.

The build fails when line coverage drops below 80%. A Docker runtime must be
available because integration tests use a real PostgreSQL container rather than an
in-memory database.

Verify the frontend separately:

```bash
cd frontend
npm run test:coverage
npm run lint
npm run build
```

Frontend coverage has the same 80% minimum for statements, branches, functions,
and lines. Component tests exercise user-visible behavior, while API-client tests
verify request headers, replay detection, problem responses, and degraded errors.

## Project structure

```text
src/main/java/com/yasaswimandava/paymentlab/
├── api/                 REST controller, DTOs, and HTTP error mapping
├── application/         Use cases, idempotency policy, and application errors
├── domain/              Payment model and state
├── port/                Persistence interfaces
├── adapter/postgres/    JDBC repositories and transaction boundary
├── messaging/           Kafka publisher, listener, and scheduled outbox relay
├── provider/            HTTP provider adapter, resilience policy, and fault simulator
└── config/              Typed configuration and dependency wiring

src/main/resources/
├── application.yml      Runtime and actuator configuration
└── db/migration/        Versioned Flyway migrations

src/test/                Unit and PostgreSQL-backed integration tests
docs/decisions/          Architecture decision records
compose.yml              Local PostgreSQL, Redpanda, and Jaeger environment

frontend/
├── src/App.tsx          Operations console and user workflows
├── src/lib/             Typed backend API client
├── src/*.test.tsx       Component behavior tests
├── src/styles.css       Responsive visual system
└── vite.config.ts       Local proxy, testing, and coverage configuration
```

## Failure scenarios and guarantees

| Scenario | Current behavior |
| --- | --- |
| Client loses the first response and retries | Returns the original payment with `replayed=true`. |
| Client changes the payload but reuses the key | Returns `409 Conflict`; no second payment is created. |
| Two instances race on the same new key | PostgreSQL selects one winner; the other transaction rolls back and replays the winner. |
| Database write fails before commit | The transaction rolls back both records. |
| Service restarts after commit | The durable idempotency record still resolves the retry. |
| Service crashes after committing a payment | The event remains durable in the outbox for a relay worker to publish. |
| Relay worker crashes after claiming an event | Another worker can reclaim it after the lease expires. |
| Kafka accepts an event but the relay stops before updating PostgreSQL | The event may be published again; the consumer event ledger makes the duplicate a no-op. |
| Kafka is unavailable or does not acknowledge in time | The relay reschedules the outbox event with bounded exponential backoff. |
| Consumer restarts after applying its effect | Kafka may redeliver; the stable event ID prevents a second projection effect. |
| Provider returns a temporary `5xx` | Retries only that logical payment, up to three total attempts, using the same provider idempotency key. |
| Provider returns a valid decline | Persists `DECLINED` immediately; no retry is attempted. |
| Provider remains unavailable | Publishes the original event to the DLT and leaves the payment `RECEIVED` for reconciliation. |
| Repeated provider operations fail | The circuit opens after the configured logical-operation failure window and fails fast. |
| Provider processes a request but its response times out | Provider idempotency prevents a second effect; local state remains unresolved and the durable DLT event requires reconciliation. |
| PostgreSQL is unavailable | The request fails; resilience and operator-facing error mapping are future work. |

## Design decisions

- [ADR-001: Merchant-scoped idempotency](docs/decisions/ADR-001-merchant-scoped-idempotency.md)
- [ADR-002: Database-enforced concurrent idempotency](docs/decisions/ADR-002-database-enforced-concurrent-idempotency.md)
- [ADR-003: Transactional outbox for durable event publication](docs/decisions/ADR-003-transactional-outbox.md)
- [ADR-004: At-least-once Kafka delivery with idempotent consumers](docs/decisions/ADR-004-at-least-once-kafka-delivery.md)
- [ADR-005: Bounded provider resilience with explicit dead-letter recovery](docs/decisions/ADR-005-bounded-provider-resilience.md)
- [ADR-006: Operator-centered observability with correlated signals](docs/decisions/ADR-006-operator-centered-observability.md)

## Roadmap

The project is intentionally developed in reviewable milestones:

- [x] Payment API and domain model
- [x] Merchant-scoped idempotency and conflict detection
- [x] PostgreSQL persistence and Flyway migrations
- [x] Atomic writes and deterministic concurrency verification
- [x] React and TypeScript operations console
- [x] Transactional outbox and exclusive relay claims
- [x] Kafka event processing and consumer idempotency
- [x] Payment-provider simulator
- [x] Timeouts, bounded retries, circuit breaker, and dead-letter topic
- [x] OpenTelemetry traces, Prometheus metrics, structured logs, and operations dashboard
- [ ] Integration, fault-injection, and load-test scenarios
- [ ] CI pipeline and container image

Planned features are listed separately from implemented capabilities so the README
never overstates the system's current behavior.

## Production gaps

This lab focuses on reliability patterns, not a deployable payment processor. A
production system would also require authentication and authorization, secret
management, TLS, PCI-DSS controls, audit logging, rate limiting, data retention and
deletion policies, reconciliation, disaster recovery, multi-region design, and
formal operational service-level objectives.

Do not send cardholder data, bank details, credentials, or other sensitive
information to this application.

## Contributing

Small, focused changes are welcome. Before opening a pull request:

1. Add or update tests for behavior changes.
2. Run `mvn verify` with Docker available.
3. Update the README or add an ADR when a design decision changes.
4. Keep planned features clearly separated from implemented behavior.

## Author

Built by [Yasaswi Mandava](https://github.com/yasaswimandava) as a public system
design and backend reliability project.

- [Portfolio](https://yasaswimandava.github.io/portfolio/)
- [LinkedIn](https://www.linkedin.com/in/yasaswi-mandava-74a69a15a/)
