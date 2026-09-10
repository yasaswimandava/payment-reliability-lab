package com.yasaswimandava.paymentlab.messaging;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yasaswimandava.paymentlab.application.OutboxRelay;
import com.yasaswimandava.paymentlab.application.PaymentEventProcessor;
import com.yasaswimandava.paymentlab.port.EventPublisher;
import com.yasaswimandava.paymentlab.port.OutboxRepository;
import com.yasaswimandava.paymentlab.port.ProcessedPaymentEventRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class MessagingComponentsTest {

    @Test
    void schedulerDelegatesOneBatchToTheRelay() {
        OutboxRepository repository = org.mockito.Mockito.mock(OutboxRepository.class);
        EventPublisher publisher = org.mockito.Mockito.mock(EventPublisher.class);
        Instant now = Instant.parse("2026-09-09T22:00:00Z");
        when(repository.claimAvailable(
                        "relay-test", 25, now, Duration.ofSeconds(30)))
                .thenReturn(List.of());
        OutboxRelay relay = new OutboxRelay(
                repository,
                publisher,
                Clock.fixed(now, ZoneOffset.UTC),
                "relay-test",
                25,
                Duration.ofSeconds(30));
        OutboxRelayScheduler scheduler = new OutboxRelayScheduler(relay);

        scheduler.publishAvailableEvents();

        verify(repository).claimAvailable(
                "relay-test", 25, now, Duration.ofSeconds(30));
    }

    @Test
    void listenerPassesTheKafkaPayloadToTheIdempotentProcessor() {
        ProcessedPaymentEventRepository repository =
                org.mockito.Mockito.mock(ProcessedPaymentEventRepository.class);
        when(repository.recordIfFirst(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);
        PaymentEventProcessor processor = new PaymentEventProcessor(
                new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules(),
                repository,
                Clock.systemUTC());
        PaymentReceivedKafkaListener listener = new PaymentReceivedKafkaListener(processor);
        String payload = """
                {
                  "eventId":"2aae639c-b7e4-44ed-a6ca-6b8340491222",
                  "paymentId":"2daef927-8eb7-46ab-9a5a-49cd552a3210",
                  "merchantId":"merchant-1",
                  "amount":42.50,
                  "currency":"USD",
                  "occurredAt":"2026-09-09T22:00:00Z"
                }
                """;
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "payments.received.v1",
                1,
                42L,
                "payment-1",
                payload);

        listener.onPaymentReceived(record);

        verify(repository).recordIfFirst(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }
}
