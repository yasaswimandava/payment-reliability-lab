package com.yasaswimandava.paymentlab.domain;

import java.time.Instant;

public record OperationsSnapshot(
        long totalPayments,
        long receivedPayments,
        long staleReceivedPayments,
        long authorizedPayments,
        long declinedPayments,
        long unpublishedOutboxEvents,
        long staleUnpublishedOutboxEvents,
        long processingOutboxEvents,
        long failedOutboxEvents,
        Instant oldestUnpublishedAt) {
}
