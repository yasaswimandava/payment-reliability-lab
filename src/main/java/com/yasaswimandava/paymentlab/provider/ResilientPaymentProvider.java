package com.yasaswimandava.paymentlab.provider;

import com.yasaswimandava.paymentlab.port.PaymentProvider;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

public final class ResilientPaymentProvider implements PaymentProvider {

    private final PaymentProvider delegate;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    public ResilientPaymentProvider(
            PaymentProvider delegate,
            int maxAttempts,
            Duration retryWait,
            float failureRateThreshold,
            int minimumCalls,
            int slidingWindowSize,
            Duration openStateWait) {
        this.delegate = Objects.requireNonNull(delegate);
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .waitDuration(retryWait)
                .retryExceptions(TransientProviderException.class)
                .failAfterMaxAttempts(true)
                .build();
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(failureRateThreshold)
                .minimumNumberOfCalls(minimumCalls)
                .slidingWindowSize(slidingWindowSize)
                .waitDurationInOpenState(openStateWait)
                .recordExceptions(
                        TransientProviderException.class,
                        PermanentProviderException.class)
                .build();
        this.retry = Retry.of("payment-provider", retryConfig);
        this.circuitBreaker = CircuitBreaker.of(
                "payment-provider", circuitBreakerConfig);
    }

    @Override
    public ProviderAuthorization authorize(ProviderAuthorizationRequest request) {
        Supplier<ProviderAuthorization> retried = Retry.decorateSupplier(
                retry, () -> delegate.authorize(request));
        Supplier<ProviderAuthorization> guarded = CircuitBreaker.decorateSupplier(
                circuitBreaker, retried);
        try {
            return guarded.get();
        } catch (CallNotPermittedException exception) {
            throw new ProviderCircuitOpenException(
                    "Payment provider circuit is open", exception);
        }
    }

    public CircuitBreaker.State circuitState() {
        return circuitBreaker.getState();
    }
}
