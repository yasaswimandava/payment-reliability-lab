package com.yasaswimandava.paymentlab.application;

import com.yasaswimandava.paymentlab.domain.OutboxMessage;
import com.yasaswimandava.paymentlab.port.EventPublisher;
import com.yasaswimandava.paymentlab.port.OutboxRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class OutboxRelay {

    private final OutboxRepository outboxRepository;
    private final EventPublisher eventPublisher;
    private final Clock clock;
    private final String workerId;
    private final int batchSize;
    private final Duration lockTimeout;

    public OutboxRelay(
            OutboxRepository outboxRepository,
            EventPublisher eventPublisher,
            Clock clock,
            String workerId,
            int batchSize,
            Duration lockTimeout) {
        this.outboxRepository = Objects.requireNonNull(outboxRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("workerId must not be blank");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        if (lockTimeout == null || lockTimeout.isNegative() || lockTimeout.isZero()) {
            throw new IllegalArgumentException("lockTimeout must be positive");
        }
        this.workerId = workerId;
        this.batchSize = batchSize;
        this.lockTimeout = lockTimeout;
    }

    public RelayBatchResult relayOnce() {
        Instant startedAt = clock.instant();
        List<OutboxMessage> claimed = outboxRepository.claimAvailable(
                workerId, batchSize, startedAt, lockTimeout);
        int published = 0;
        int failed = 0;

        for (OutboxMessage message : claimed) {
            try {
                eventPublisher.publish(message);
                if (outboxRepository.markPublished(
                        message.eventId(), workerId, clock.instant())) {
                    published++;
                } else {
                    failed++;
                }
            } catch (RuntimeException exception) {
                failed++;
                outboxRepository.reschedule(
                        message.eventId(),
                        workerId,
                        clock.instant().plus(retryDelay(message.attemptCount())),
                        conciseMessage(exception));
            }
        }

        return new RelayBatchResult(claimed.size(), published, failed);
    }

    private Duration retryDelay(int attemptCount) {
        long seconds = Math.min(300L, 1L << Math.min(Math.max(attemptCount - 1, 0), 8));
        return Duration.ofSeconds(seconds);
    }

    private String conciseMessage(RuntimeException exception) {
        String message = exception.getMessage();
        String detail = message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
        return detail.substring(0, Math.min(detail.length(), 2_000));
    }
}
