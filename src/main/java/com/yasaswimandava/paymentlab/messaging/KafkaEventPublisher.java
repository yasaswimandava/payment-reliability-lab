package com.yasaswimandava.paymentlab.messaging;

import com.yasaswimandava.paymentlab.domain.OutboxMessage;
import com.yasaswimandava.paymentlab.port.EventPublisher;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;

public final class KafkaEventPublisher implements EventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final Duration acknowledgementTimeout;

    public KafkaEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            String topic,
            Duration acknowledgementTimeout) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate);
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic must not be blank");
        }
        if (acknowledgementTimeout == null
                || acknowledgementTimeout.isNegative()
                || acknowledgementTimeout.isZero()) {
            throw new IllegalArgumentException("acknowledgementTimeout must be positive");
        }
        this.topic = topic;
        this.acknowledgementTimeout = acknowledgementTimeout;
    }

    @Override
    public void publish(OutboxMessage message) {
        ProducerRecord<String, String> record = new ProducerRecord<>(
                topic,
                null,
                message.occurredAt().toEpochMilli(),
                message.aggregateId().toString(),
                message.payload(),
                java.util.List.of(new RecordHeader(
                        "event-id",
                        message.eventId().toString().getBytes(StandardCharsets.UTF_8))));
        try {
            kafkaTemplate.send(record).get(
                    acknowledgementTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EventPublicationException(
                    "Kafka publication was interrupted for event " + message.eventId(),
                    exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new EventPublicationException(
                    "Kafka did not acknowledge event " + message.eventId(),
                    rootCause(exception));
        }
    }

    private Throwable rootCause(Exception exception) {
        return exception instanceof ExecutionException && exception.getCause() != null
                ? exception.getCause()
                : exception;
    }
}
