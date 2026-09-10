package com.yasaswimandava.paymentlab.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yasaswimandava.paymentlab.application.AuthorizationProcessingResult;
import com.yasaswimandava.paymentlab.application.PaymentAuthorizationService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class PaymentAuthorizationTelemetryTest {

    @Test
    void recordsALowCardinalityAuthorizationOutcomeAndDuration() {
        PaymentAuthorizationService service =
                org.mockito.Mockito.mock(PaymentAuthorizationService.class);
        when(service.authorize(org.mockito.ArgumentMatchers.any()))
                .thenReturn(AuthorizationProcessingResult.AUTHORIZED);
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
