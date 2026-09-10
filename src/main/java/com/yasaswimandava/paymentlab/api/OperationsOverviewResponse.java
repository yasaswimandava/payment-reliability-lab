package com.yasaswimandava.paymentlab.api;

import com.yasaswimandava.paymentlab.domain.OperationsSnapshot;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Instant;

public record OperationsOverviewResponse(
        String health,
        Instant generatedAt,
        PaymentCounts payments,
        OutboxCounts outbox,
        ProviderState provider) {

    public static OperationsOverviewResponse from(
            OperationsSnapshot snapshot,
            CircuitBreaker.State circuitState,
            Instant generatedAt) {
        String health = snapshot.failedOutboxEvents() > 0
                        || snapshot.staleReceivedPayments() > 0
                        || snapshot.staleUnpublishedOutboxEvents() > 0
                        || circuitState == CircuitBreaker.State.OPEN
                        || circuitState == CircuitBreaker.State.FORCED_OPEN
                ? "ATTENTION"
                : "HEALTHY";
        return new OperationsOverviewResponse(
                health,
                generatedAt,
                new PaymentCounts(
                        snapshot.totalPayments(),
                        snapshot.receivedPayments(),
                        snapshot.staleReceivedPayments(),
                        snapshot.authorizedPayments(),
                        snapshot.declinedPayments()),
                new OutboxCounts(
                        snapshot.unpublishedOutboxEvents(),
                        snapshot.staleUnpublishedOutboxEvents(),
                        snapshot.processingOutboxEvents(),
                        snapshot.failedOutboxEvents(),
                        snapshot.oldestUnpublishedAt()),
                new ProviderState(circuitState.name()));
    }

    public record PaymentCounts(
            long total,
            long received,
            long stale,
            long authorized,
            long declined) {
    }

    public record OutboxCounts(
            long unpublished,
            long stale,
            long processing,
            long failed,
            Instant oldestUnpublishedAt) {
    }

    public record ProviderState(String circuitState) {
    }
}
