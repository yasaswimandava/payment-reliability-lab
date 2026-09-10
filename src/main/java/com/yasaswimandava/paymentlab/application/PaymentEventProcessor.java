package com.yasaswimandava.paymentlab.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yasaswimandava.paymentlab.domain.PaymentReceivedEvent;
import com.yasaswimandava.paymentlab.port.ProcessedPaymentEventRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

public final class PaymentEventProcessor {

    private final ObjectMapper objectMapper;
    private final ProcessedPaymentEventRepository processedEventRepository;
    private final Clock clock;

    public PaymentEventProcessor(
            ObjectMapper objectMapper,
            ProcessedPaymentEventRepository processedEventRepository,
            Clock clock) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.processedEventRepository = Objects.requireNonNull(processedEventRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    public EventProcessingResult process(String payload) {
        PaymentReceivedEvent event = parse(payload);
        boolean firstDelivery = processedEventRepository.recordIfFirst(event, clock.instant());
        return firstDelivery ? EventProcessingResult.PROCESSED : EventProcessingResult.DUPLICATE;
    }

    private PaymentReceivedEvent parse(String payload) {
        try {
            PaymentReceivedPayload event = objectMapper.readValue(
                    payload, PaymentReceivedPayload.class);
            return new PaymentReceivedEvent(
                    event.eventId(),
                    event.paymentId(),
                    event.merchantId(),
                    event.amount(),
                    Currency.getInstance(event.currency()),
                    event.occurredAt());
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new InvalidPaymentEventException("Invalid PAYMENT_RECEIVED payload", exception);
        }
    }

    private record PaymentReceivedPayload(
            UUID eventId,
            UUID paymentId,
            String merchantId,
            BigDecimal amount,
            String currency,
            Instant occurredAt) {
    }
}
