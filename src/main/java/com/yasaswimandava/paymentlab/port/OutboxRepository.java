package com.yasaswimandava.paymentlab.port;

import com.yasaswimandava.paymentlab.domain.OutboxMessage;
import com.yasaswimandava.paymentlab.domain.PaymentReceivedEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository {

    void save(PaymentReceivedEvent event);

    List<OutboxMessage> claimAvailable(
            String workerId,
            int batchSize,
            Instant claimAt,
            Duration lockTimeout);

    boolean markPublished(UUID eventId, String workerId, Instant publishedAt);

    boolean reschedule(
            UUID eventId,
            String workerId,
            Instant availableAt,
            String error);
}
