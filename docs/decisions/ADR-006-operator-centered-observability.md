# ADR-006: Operator-centered observability with correlated signals

- Status: Accepted
- Date: 2026-09-09

## Context

Production support needs more than a health endpoint or a wall of JVM charts.
Operators must be able to answer whether payment processing is healthy, where work
is waiting, whether the provider is protected, and how one request moved through
HTTP, Kafka, and the provider boundary.

Telemetry must also avoid turning payment identifiers, merchant identifiers, or
other high-cardinality values into metric labels.

## Decision

The service exposes three complementary signal types:

1. Micrometer metrics are available in Prometheus format at
   `/actuator/prometheus`. Spring HTTP and Kafka observations provide platform
   latency and failure signals. Custom authorization counters and histograms use
   only the bounded `result` label.
2. Micrometer Tracing bridges observations to OpenTelemetry and exports OTLP spans
   to a local Jaeger instance. The instrumented Spring `RestClient.Builder` carries
   trace context across the provider HTTP boundary, and Kafka observation is
   enabled for producers and consumers.
3. Console logs use Elastic Common Schema JSON. Each HTTP response carries an
   `X-Correlation-ID`, and the same validated value is added to log context beside
   the trace and span IDs.

The operations overview reads current payment and outbox state from PostgreSQL and
combines it with the provider circuit state. The React console groups this into
system posture, awaiting authorization, business outcomes, unpublished events,
and provider protection. Authorization and outbox work older than 30 seconds is
marked stale and changes the system posture to `ATTENTION`. These panels answer
operating questions rather than duplicating every available metric.

Local development samples every trace to make demonstrations deterministic. The
sampling probability and exporter endpoint remain environment-configurable so a
production deployment can reduce cost and volume.

## Consequences

- An operator can move from a visible backlog or open circuit to Prometheus metrics,
  correlated ECS logs, and the relevant Jaeger trace.
- Durable-state counts survive process restarts and do not depend on an in-memory
  metric registry.
- Metrics avoid payment and merchant IDs as labels, preventing unbounded series
  growth and accidental identifier disclosure.
- Full local trace sampling is intentionally a lab default, not a production
  recommendation.
- Jaeger stores traces in memory in the local Compose environment; restarting it
  discards trace history.
- Alert routing and long-term telemetry retention remain deployment concerns.
