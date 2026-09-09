# ADR-002: Database-enforced concurrent idempotency

- Status: Accepted
- Date: 2026-09-09

## Context

Two service instances can receive the same request before either instance observes
an idempotency record. An application-only check-then-insert is therefore subject
to a race and cannot guarantee a single business effect.

## Decision

PostgreSQL owns the final concurrency decision through the primary key on
`(merchant_id, idempotency_key)`.

Payment creation and idempotency insertion execute in one transaction. If two
transactions race:

1. Both may initially observe that the key is absent.
2. PostgreSQL allows only one idempotency row to commit.
3. The losing transaction receives a duplicate-key error and rolls back its payment.
4. The application retries once in a new transaction.
5. The retry finds the committed record and returns the winning payment as a replay.

## Consequences

- The guarantee works across threads and horizontally scaled service instances.
- Failed competing transactions cannot leave orphaned payments.
- A deterministic integration test forces both transactions to race at the save
  boundary and verifies one stored payment.
- The retry is intentionally limited to one attempt and only handles uniqueness
  conflicts; unrelated database failures still propagate.
