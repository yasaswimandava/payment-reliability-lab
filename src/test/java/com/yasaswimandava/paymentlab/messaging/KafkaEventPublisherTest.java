package com.yasaswimandava.paymentlab.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yasaswimandava.paymentlab.domain.OutboxMessage;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"rawtypes", "unchecked"})
class KafkaEventPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void publishesWithStableTopicKeyPayloadAndEventIdentity() {
        OutboxMessage message = message();
        when(kafkaTemplate.send(org.mockito.ArgumentMatchers.<ProducerRecord<String, String>>any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        KafkaEventPublisher publisher = new KafkaEventPublisher(
                kafkaTemplate, "payments.received.v1", Duration.ofSeconds(3));

        publisher.publish(message);

        ArgumentCaptor<ProducerRecord<String, String>> captor =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, String> record = captor.getValue();
        assertThat(record.topic()).isEqualTo("payments.received.v1");
        assertThat(record.key()).isEqualTo(message.aggregateId().toString());
        assertThat(record.value()).isEqualTo(message.payload());
        assertThat(new String(
                        record.headers().lastHeader("event-id").value(),
                        StandardCharsets.UTF_8))
                .isEqualTo(message.eventId().toString());
    }

    @Test
    void failsTheRelayAttemptWhenKafkaDoesNotAcknowledgePublication() {
        CompletableFuture<org.springframework.kafka.support.SendResult<String, String>> failed =
                CompletableFuture.failedFuture(new IllegalStateException("broker unavailable"));
        when(kafkaTemplate.send(org.mockito.ArgumentMatchers.<ProducerRecord<String, String>>any()))
                .thenReturn(failed);
        KafkaEventPublisher publisher = new KafkaEventPublisher(
                kafkaTemplate, "payments.received.v1", Duration.ofSeconds(3));

        assertThatThrownBy(() -> publisher.publish(message()))
                .isInstanceOf(EventPublicationException.class)
                .hasMessageContaining("Kafka did not acknowledge");
    }

    private OutboxMessage message() {
        UUID eventId = UUID.fromString("2aae639c-b7e4-44ed-a6ca-6b8340491222");
        UUID paymentId = UUID.fromString("2daef927-8eb7-46ab-9a5a-49cd552a3210");
        return new OutboxMessage(
                eventId,
                "PAYMENT",
                paymentId,
                "PAYMENT_RECEIVED",
                "{\"eventId\":\"%s\"}".formatted(eventId),
                Instant.parse("2026-09-09T22:00:00Z"),
                1,
                "relay-test");
    }
}
