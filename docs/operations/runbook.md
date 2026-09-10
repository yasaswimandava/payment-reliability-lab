# Payment Reliability Lab operator runbook

This runbook is for the local Compose environment. It demonstrates the same
questions and recovery decisions an operator would make in production without
claiming that the lab itself is a production payment processor.

## Start and verify the stack

```bash
cp .env.example .env
docker compose --profile application up --detach --build
docker compose --profile application ps
curl --fail http://localhost:8080/actuator/health
curl --fail http://localhost:8080/api/v1/operations/overview
```

The application is ready when `app`, `postgres`, and `redpanda` are healthy. Open
the operations console at <http://localhost:8080> and Jaeger at
<http://localhost:16686>.

## Fast triage

Start with `GET /api/v1/operations/overview` or the console dashboard.

| Signal | Meaning | First check |
| --- | --- | --- |
| `health=HEALTHY` | No durable work is stale and the provider circuit is not open. | Confirm recent authorization metrics remain normal. |
| `health=ATTENTION` with stale received payments | Authorization has not completed within 30 seconds. | Inspect provider circuit, DLT, and authorization traces. |
| Unpublished or processing outbox count grows | The relay cannot finish publication. | Check broker health, relay errors, and oldest unpublished time. |
| Failed outbox count is non-zero | Events exhausted their relay retry policy. | Inspect application logs and the affected database rows before recovery. |
| Provider circuit is open | Recent logical provider operations crossed the failure threshold. | Verify provider health and allow the configured open-state wait to pass. |

Use the response `X-Correlation-ID` to search ECS JSON logs. Use its associated
trace ID in Jaeger to follow HTTP and messaging spans. Metrics are available at
`/actuator/prometheus`; custom provider signals begin with
`payment_authorization_`.

## Kafka and dead-letter checks

List topics and inspect consumer groups:

```bash
docker compose exec redpanda rpk topic list
docker compose exec redpanda rpk group list
docker compose exec redpanda rpk group describe payment-provider-v1
docker compose exec redpanda rpk group describe payment-projection-v1
```

Inspect an authorization event that exhausted its retry budget:

```bash
docker compose exec redpanda \
  rpk topic consume payments.received.v1.dlt --num 1 --format '%v\n'
```

The DLT is evidence, not an automatic success path. A payment intentionally stays
`RECEIVED` when the provider outcome is ambiguous. Confirm the provider's outcome
using its stable payment idempotency key before changing payment state or replaying
the work.

## PostgreSQL checks

Open a database shell:

```bash
docker compose exec postgres \
  psql --username payment_lab --dbname payment_lab
```

Useful read-only queries:

```sql
select status, count(*) from payments group by status order by status;

select status, count(*), min(occurred_at) as oldest
from outbox_events
group by status
order by status;

select event_id, aggregate_id, status, attempt_count, available_at, last_error
from outbox_events
where status <> 'PUBLISHED'
order by occurred_at
limit 20;
```

Do not manually mark outbox rows published or payments authorized during ordinary
recovery. Preserve the event and provider evidence first.

## Exercise failure behavior

With the application healthy and `curl` plus `jq` installed:

```bash
./scripts/run-fault-scenarios.sh
```

The script verifies approval, transient recovery, immediate decline, bounded
outage retries, and timeout ambiguity. `UNAVAILABLE` and `TIMEOUT` must leave the
payment in `RECEIVED` and route the event to the DLT. In timeout mode the synthetic
provider may finish one idempotent effect after the client has stopped waiting;
that intentionally demonstrates why an unknown local outcome requires
reconciliation rather than blind replay.

## Load check

Install k6, then run:

```bash
k6 run performance/payment-api.js
```

Override the defaults when needed:

```bash
BASE_URL=http://localhost:8080 VUS=25 DURATION=60s \
  k6 run performance/payment-api.js
```

The test sends every logical operation twice and checks that the second request
returns the original payment as an idempotent replay. Default thresholds require
more than 99% successful checks, less than 1% failed HTTP requests, and sub-500 ms
p95 API latency. These are local demonstration thresholds, not an externally
committed service-level objective.

## Shutdown and data reset

Stop the processes while preserving PostgreSQL and Redpanda data:

```bash
docker compose --profile application down
```

For a deliberate clean-room reset, remove the named volumes as well:

```bash
docker compose --profile application down --volumes
```

The second command permanently deletes the lab's local database and broker data.
