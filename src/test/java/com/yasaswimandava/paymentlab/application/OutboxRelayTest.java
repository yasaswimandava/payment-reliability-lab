package com.yasaswimandava.paymentlab.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yasaswimandava.paymentlab.domain.OutboxMessage;
import com.yasaswimandava.paymentlab.port.EventPublisher;
import com.yasaswimandava.paymentlab.port.OutboxRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-09-09T22:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private EventPublisher eventPublisher;

    @Test
    void publishesClaimedEventsAndMarksThemPublishedAfterBrokerAcknowledgement() {
        OutboxMessage message = message();
        when(outboxRepository.claimAvailable(
                        "relay-test", 25, NOW, Duration.ofSeconds(30)))
                .thenReturn(List.of(message));
        when(outboxRepository.markPublished(message.eventId(), "relay-test", NOW))
                .thenReturn(true);
        OutboxRelay relay = new OutboxRelay(
                outboxRepository,
                eventPublisher,
                CLOCK,
                "relay-test",
                25,
                Duration.ofSeconds(30));

        RelayBatchResult result = relay.relayOnce();

        verify(eventPublisher).publish(message);
        verify(outboxRepository).markPublished(message.eventId(), "relay-test", NOW);
        assertThat(result).isEqualTo(new RelayBatchResult(1, 1, 0));
    }

    private OutboxMessage message() {
        UUID eventId = UUID.fromString("2aae639c-b7e4-44ed-a6ca-6b8340491222");
        UUID paymentId = UUID.fromString("2daef927-8eb7-46ab-9a5a-49cd552a3210");
        return new OutboxMessage(
                eventId,
                "PAYMENT",
                paymentId,
                "PAYMENT_RECEIVED",
                "{\"eventId\":\"%s\",\"paymentId\":\"%s\",\"merchantId\":\"merchant-1\","
                        .formatted(eventId, paymentId)
                        + "\"amount\":42.50,\"currency\":\"USD\","
                        + "\"occurredAt\":\"2026-09-09T22:00:00Z\"}",
                NOW,
                1,
                "relay-test");
    }
}
