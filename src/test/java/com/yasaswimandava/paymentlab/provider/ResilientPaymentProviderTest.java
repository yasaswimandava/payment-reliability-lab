package com.yasaswimandava.paymentlab.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yasaswimandava.paymentlab.port.PaymentProvider;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Currency;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ResilientPaymentProviderTest {

    @Test
    void retriesTransientFailuresWithinABoundedAttemptBudget() {
        AtomicInteger attempts = new AtomicInteger();
        PaymentProvider unstable = request -> {
            if (attempts.incrementAndGet() < 3) {
                throw new TransientProviderException("upstream 503");
            }
            return ProviderAuthorization.approved("provider-123");
        };
        ResilientPaymentProvider provider = resilient(unstable);

        ProviderAuthorization result = provider.authorize(request());

        assertThat(result.decision()).isEqualTo(ProviderDecision.APPROVED);
        assertThat(attempts).hasValue(3);
    }

    @Test
    void doesNotRetryPermanentProviderDecisions() {
        AtomicInteger attempts = new AtomicInteger();
        PaymentProvider invalid = request -> {
            attempts.incrementAndGet();
            throw new PermanentProviderException("invalid merchant");
        };
        ResilientPaymentProvider provider = resilient(invalid);

        assertThatThrownBy(() -> provider.authorize(request()))
                .isInstanceOf(PermanentProviderException.class);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void opensTheCircuitAfterRepeatedLogicalFailures() {
        AtomicInteger attempts = new AtomicInteger();
        PaymentProvider unavailable = request -> {
            attempts.incrementAndGet();
            throw new PermanentProviderException("provider unavailable");
        };
        ResilientPaymentProvider provider = resilient(unavailable);

        for (int failure = 0; failure < 4; failure++) {
            assertThatThrownBy(() -> provider.authorize(request()))
                    .isInstanceOf(PermanentProviderException.class);
        }
        assertThatThrownBy(() -> provider.authorize(request()))
                .isInstanceOf(ProviderCircuitOpenException.class);
        assertThat(attempts).hasValue(4);
    }

    private ResilientPaymentProvider resilient(PaymentProvider delegate) {
        return new ResilientPaymentProvider(
                delegate,
                3,
                Duration.ZERO,
                50.0f,
                4,
                4,
                Duration.ofSeconds(30));
    }

    private ProviderAuthorizationRequest request() {
        return new ProviderAuthorizationRequest(
                UUID.fromString("f1391146-e720-4fb8-a5d8-adc209083c59"),
                "merchant-provider-test",
                new BigDecimal("49.25"),
                Currency.getInstance("USD"));
    }
}
