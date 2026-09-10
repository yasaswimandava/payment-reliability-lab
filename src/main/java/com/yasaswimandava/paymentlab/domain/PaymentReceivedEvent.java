package com.yasaswimandava.paymentlab.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

public record PaymentReceivedEvent(
        UUID eventId,
        UUID paymentId,
        String merchantId,
        BigDecimal amount,
        Currency currency,
        Instant occurredAt) {

    public PaymentReceivedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(merchantId, "merchantId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    public static PaymentReceivedEvent from(Payment payment) {
        return new PaymentReceivedEvent(
                UUID.randomUUID(),
                payment.id(),
                payment.merchantId(),
                payment.amount(),
                payment.currency(),
                payment.createdAt());
    }
}
