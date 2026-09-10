package com.yasaswimandava.paymentlab.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yasaswimandava.paymentlab.application.OutboxRelay;
import com.yasaswimandava.paymentlab.application.PaymentAuthorizationService;
import com.yasaswimandava.paymentlab.application.PaymentEventProcessor;
import com.yasaswimandava.paymentlab.messaging.KafkaEventPublisher;
import com.yasaswimandava.paymentlab.messaging.OutboxRelayScheduler;
import com.yasaswimandava.paymentlab.messaging.PaymentAuthorizationKafkaListener;
import com.yasaswimandava.paymentlab.messaging.PaymentReceivedEventCodec;
import com.yasaswimandava.paymentlab.messaging.PaymentReceivedKafkaListener;
import com.yasaswimandava.paymentlab.port.EventPublisher;
import com.yasaswimandava.paymentlab.port.OutboxRepository;
import com.yasaswimandava.paymentlab.port.ProcessedPaymentEventRepository;
import java.time.Clock;
import java.util.UUID;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({PaymentEventProperties.class, PaymentProviderProperties.class})
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
    NewTopic paymentDeadLetterTopic(PaymentProviderProperties properties) {
        return TopicBuilder.name(properties.deadLetterTopic()).partitions(3).replicas(1).build();
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

    @Bean
    PaymentReceivedEventCodec paymentReceivedEventCodec(ObjectMapper objectMapper) {
        return new PaymentReceivedEventCodec(objectMapper);
    }

    @Bean
    PaymentAuthorizationKafkaListener paymentAuthorizationKafkaListener(
            PaymentReceivedEventCodec eventCodec,
            PaymentAuthorizationService authorizationService) {
        return new PaymentAuthorizationKafkaListener(eventCodec, authorizationService);
    }

    @Bean
    CommonErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            PaymentProviderProperties properties) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(
                        properties.deadLetterTopic(), record.partition()));
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer, new FixedBackOff(0L, 0L));
        errorHandler.setCommitRecovered(true);
        return errorHandler;
    }
}
