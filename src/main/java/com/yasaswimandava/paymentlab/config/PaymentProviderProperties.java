package com.yasaswimandava.paymentlab.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("payment.provider")
public record PaymentProviderProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        int maxAttempts,
        Duration retryWait,
        float failureRateThreshold,
        int minimumCalls,
        int slidingWindowSize,
        Duration openStateWait,
        String consumerGroup,
        String deadLetterTopic) {

    public PaymentProviderProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("payment.provider.base-url must not be blank");
        }
        requirePositive(connectTimeout, "connect-timeout");
        requirePositive(readTimeout, "read-timeout");
        if (maxAttempts < 1 || maxAttempts > 10) {
            throw new IllegalArgumentException(
                    "payment.provider.max-attempts must be between 1 and 10");
        }
        if (retryWait == null || retryWait.isNegative()) {
            throw new IllegalArgumentException("payment.provider.retry-wait must not be negative");
        }
        if (failureRateThreshold <= 0 || failureRateThreshold > 100) {
            throw new IllegalArgumentException(
                    "payment.provider.failure-rate-threshold must be in (0, 100]");
        }
        if (minimumCalls < 1 || slidingWindowSize < minimumCalls) {
            throw new IllegalArgumentException(
                    "payment.provider sliding window must cover minimum calls");
        }
        requirePositive(openStateWait, "open-state-wait");
        if (consumerGroup == null || consumerGroup.isBlank()) {
            throw new IllegalArgumentException(
                    "payment.provider.consumer-group must not be blank");
        }
        if (deadLetterTopic == null || deadLetterTopic.isBlank()) {
            throw new IllegalArgumentException(
                    "payment.provider.dead-letter-topic must not be blank");
        }
    }

    private static void requirePositive(Duration duration, String name) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("payment.provider." + name + " must be positive");
        }
    }
}
