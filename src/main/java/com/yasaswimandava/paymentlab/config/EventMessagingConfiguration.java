package com.yasaswimandava.paymentlab.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yasaswimandava.paymentlab.application.OutboxRelay;
import com.yasaswimandava.paymentlab.application.PaymentEventProcessor;
import com.yasaswimandava.paymentlab.messaging.KafkaEventPublisher;
import com.yasaswimandava.paymentlab.messaging.OutboxRelayScheduler;
import com.yasaswimandava.paymentlab.messaging.PaymentReceivedKafkaListener;
import com.yasaswimandava.paymentlab.port.EventPublisher;
import com.yasaswimandava.paymentlab.port.OutboxRepository;
import com.yasaswimandava.paymentlab.port.ProcessedPaymentEventRepository;
import java.time.Clock;
import java.util.UUID;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(PaymentEventProperties.class)
@ConditionalOnProperty(
        prefix = "payment.events",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class EventMessagingConfiguration {

    @Bean
    NewTopic paymentReceivedTopic(PaymentEventProperties properties) {
        return TopicBuilder.name(properties.topic()).partitions(3).replicas(1).build();
    }

    @Bean
    EventPublisher eventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            PaymentEventProperties properties) {
        return new KafkaEventPublisher(
                kafkaTemplate,
                properties.topic(),
                properties.acknowledgementTimeout());
    }

    @Bean
    OutboxRelay outboxRelay(
            OutboxRepository outboxRepository,
            EventPublisher eventPublisher,
            Clock clock,
            PaymentEventProperties properties) {
        return new OutboxRelay(
                outboxRepository,
                eventPublisher,
                clock,
                "payment-relay-" + UUID.randomUUID(),
                properties.batchSize(),
                properties.lockTimeout());
    }

    @Bean
    OutboxRelayScheduler outboxRelayScheduler(OutboxRelay relay) {
        return new OutboxRelayScheduler(relay);
    }

    @Bean
    PaymentEventProcessor paymentEventProcessor(
            ObjectMapper objectMapper,
            ProcessedPaymentEventRepository processedEventRepository,
            Clock clock) {
        return new PaymentEventProcessor(objectMapper, processedEventRepository, clock);
    }

    @Bean
    PaymentReceivedKafkaListener paymentReceivedKafkaListener(
            PaymentEventProcessor processor) {
        return new PaymentReceivedKafkaListener(processor);
    }
}
