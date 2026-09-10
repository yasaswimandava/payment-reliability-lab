package com.yasaswimandava.paymentlab.api;

import com.yasaswimandava.paymentlab.application.CreatePaymentResult;
import com.yasaswimandava.paymentlab.domain.Payment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        String merchantId,
        BigDecimal amount,
        String currency,
        String status,
        String providerReference,
        Instant createdAt,
        boolean replayed) {

    static PaymentResponse from(CreatePaymentResult result) {
        return from(result.payment(), result.replayed());
    }

    static PaymentResponse from(Payment payment) {
        return from(payment, false);
    }

    private static PaymentResponse from(Payment payment, boolean replayed) {
        return new PaymentResponse(
                payment.id(),
                payment.merchantId(),
                payment.amount(),
                payment.currency().getCurrencyCode(),
                payment.status().name(),
                payment.providerReference(),
                payment.createdAt(),
                replayed);
    }
}
