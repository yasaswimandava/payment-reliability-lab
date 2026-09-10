package com.yasaswimandava.paymentlab.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yasaswimandava.paymentlab.port.EventPublisher;
import com.yasaswimandava.paymentlab.port.OutboxRepository;
import com.yasaswimandava.paymentlab.port.ProcessedPaymentEventRepository;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

@SuppressWarnings("unchecked")
class EventMessagingConfigurationTest {

    @Test
    void wiresTheKafkaRelayAndConsumerComponentsFromTypedProperties() {
        PaymentEventProperties properties = validProperties();
        EventMessagingConfiguration configuration = new EventMessagingConfiguration();
        KafkaTemplate<String, String> kafkaTemplate = org.mockito.Mockito.mock(KafkaTemplate.class);
        OutboxRepository outboxRepository = org.mockito.Mockito.mock(OutboxRepository.class);
        ProcessedPaymentEventRepository processedRepository =
                org.mockito.Mockito.mock(ProcessedPaymentEventRepository.class);
        Clock clock = Clock.systemUTC();

        EventPublisher publisher = configuration.eventPublisher(kafkaTemplate, properties);
        var relay = configuration.outboxRelay(
                outboxRepository, publisher, clock, properties);
        var processor = configuration.paymentEventProcessor(
                new ObjectMapper().findAndRegisterModules(), processedRepository, clock);

        assertThat(configuration.paymentReceivedTopic(properties).name())
                .isEqualTo("payments.received.v1");
        assertThat(configuration.outboxRelayScheduler(relay)).isNotNull();
        assertThat(configuration.paymentReceivedKafkaListener(processor)).isNotNull();
    }

    @Test
    void rejectsUnsafeMessagingConfiguration() {
        assertThatThrownBy(() -> new PaymentEventProperties(
                        " ",
                        "projection-v1",
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(30),
                        25))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topic");
        assertThatThrownBy(() -> new PaymentEventProperties(
                        "payments.received.v1",
                        " ",
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(30),
                        25))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("consumer-group");
        assertThatThrownBy(() -> new PaymentEventProperties(
                        "payments.received.v1",
                        "projection-v1",
                        Duration.ZERO,
                        Duration.ofSeconds(30),
                        25))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("acknowledgement-timeout");
        assertThatThrownBy(() -> new PaymentEventProperties(
                        "payments.received.v1",
                        "projection-v1",
                        Duration.ofSeconds(3),
                        Duration.ZERO,
                        25))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lock-timeout");
        assertThatThrownBy(() -> new PaymentEventProperties(
                        "payments.received.v1",
                        "projection-v1",
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(30),
                        0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batch-size");
    }

    private PaymentEventProperties validProperties() {
        return new PaymentEventProperties(
                "payments.received.v1",
                "projection-v1",
                Duration.ofSeconds(3),
                Duration.ofSeconds(30),
                25);
    }
}
