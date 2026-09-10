package com.yasaswimandava.paymentlab.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yasaswimandava.paymentlab.application.InvalidPaymentEventException;
import com.yasaswimandava.paymentlab.domain.PaymentReceivedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

public final class PaymentReceivedEventCodec {

    private final ObjectMapper objectMapper;

    public PaymentReceivedEventCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public PaymentReceivedEvent decode(String payload) {
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
