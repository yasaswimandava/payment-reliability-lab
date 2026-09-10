package com.yasaswimandava.paymentlab.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("payment.events")
public record PaymentEventProperties(
        String topic,
        String consumerGroup,
        Duration acknowledgementTimeout,
        Duration lockTimeout,
        int batchSize) {

    public PaymentEventProperties {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("payment.events.topic must not be blank");
        }
        if (consumerGroup == null || consumerGroup.isBlank()) {
            throw new IllegalArgumentException("payment.events.consumer-group must not be blank");
        }
        if (acknowledgementTimeout == null
                || acknowledgementTimeout.isNegative()
                || acknowledgementTimeout.isZero()) {
            throw new IllegalArgumentException(
                    "payment.events.acknowledgement-timeout must be positive");
        }
        if (lockTimeout == null || lockTimeout.isNegative() || lockTimeout.isZero()) {
            throw new IllegalArgumentException("payment.events.lock-timeout must be positive");
        }
        if (batchSize < 1 || batchSize > 1_000) {
            throw new IllegalArgumentException(
                    "payment.events.batch-size must be between 1 and 1000");
        }
    }
}
