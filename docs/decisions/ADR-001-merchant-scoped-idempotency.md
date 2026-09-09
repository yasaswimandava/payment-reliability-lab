# ADR-001: Merchant-scoped idempotency

- Status: Accepted
- Date: 2026-09-09

## Context

Clients retry payment requests when connections time out or responses are lost. A
retry must not create an additional business effect. Different merchants must be
able to use the same client-generated key without colliding.

## Decision

Each payment creation request requires an `Idempotency-Key`. The logical unique key
is the pair `(merchant_id, idempotency_key)`.

The service stores a SHA-256 fingerprint of the canonical request together with the
created payment ID:

- The first request creates the payment and stores the idempotency record.
- An identical retry returns the original payment and marks the response as replayed.
- Reuse of the key with a different fingerprint returns HTTP `409 Conflict`.

The fingerprint includes merchant ID, normalized amount, and ISO currency code. It
does not contain confidential payment credentials.

## Consequences

- Network retries are safe for clients that preserve their idempotency key.
- Accidental key reuse with changed details is visible instead of silently accepted.
- PostgreSQL atomically persists the payment and idempotency record.
- A composite primary key enforces uniqueness across instances.
- Future request fields that affect the business operation must be added to the
  canonical fingerprint.
