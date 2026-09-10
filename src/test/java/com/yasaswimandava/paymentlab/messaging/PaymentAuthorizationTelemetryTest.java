package com.yasaswimandava.paymentlab.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yasaswimandava.paymentlab.application.PaymentAuthorizationService;
import com.yasaswimandava.paymentlab.domain.Payment;
import com.yasaswimandava.paymentlab.domain.PaymentStatus;
import com.yasaswimandava.paymentlab.port.PaymentProvider;
import com.yasaswimandava.paymentlab.port.PaymentRepository;
import com.yasaswimandava.paymentlab.provider.ProviderAuthorization;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class PaymentAuthorizationTelemetryTest {

    @Test
    void recordsALowCardinalityAuthorizationOutcomeAndDuration() {
        PaymentRepository repository = org.mockito.Mockito.mock(PaymentRepository.class);
        PaymentProvider provider = org.mockito.Mockito.mock(PaymentProvider.class);
        Payment payment = new Payment(
                UUID.fromString("2daef927-8eb7-46ab-9a5a-49cd552a3210"),
                "merchant-1",
                new BigDecimal("42.50"),
                Currency.getInstance("USD"),
                PaymentStatus.RECEIVED,
                null,
                Instant.parse("2026-09-09T22:00:00Z"));
        when(repository.findById(payment.id())).thenReturn(Optional.of(payment));
        when(provider.authorize(org.mockito.ArgumentMatchers.any()))
                .thenReturn(ProviderAuthorization.approved("provider-123"));
        when(repository.completeAuthorization(
                        payment.id(),
                        PaymentStatus.RECEIVED,
                        PaymentStatus.AUTHORIZED,
                        "provider-123"))
                .thenReturn(true);
        PaymentAuthorizationService service = new PaymentAuthorizationService(repository, provider);
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        PaymentAuthorizationKafkaListener listener = new PaymentAuthorizationKafkaListener(
                new PaymentReceivedEventCodec(new ObjectMapper().findAndRegisterModules()),
                service,
                meters);

        listener.onPaymentReceived(record());

        assertThat(meters.get("payment.authorization.outcomes")
                        .tag("result", "authorized")
                        .counter()
                        .count())
                .isEqualTo(1.0);
        assertThat(meters.get("payment.authorization.duration")
                        .tag("result", "authorized")
                        .timer()
                        .count())
                .isEqualTo(1L);
    }

    private ConsumerRecord<String, String> record() {
        return new ConsumerRecord<>(
                "payments.received.v1",
                1,
                42L,
                "payment-1",
                """
                {
                  "eventId":"2aae639c-b7e4-44ed-a6ca-6b8340491222",
                  "paymentId":"2daef927-8eb7-46ab-9a5a-49cd552a3210",
                  "merchantId":"merchant-1",
                  "amount":42.50,
                  "currency":"USD",
                  "occurredAt":"2026-09-09T22:00:00Z"
                }
                """);
    }
}
