package com.yasaswimandava.paymentlab.api;

import com.yasaswimandava.paymentlab.application.CreatePaymentResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        String merchantId,
        BigDecimal amount,
        String currency,
        String status,
        Instant createdAt,
        boolean replayed) {

    static PaymentResponse from(CreatePaymentResult result) {
        return new PaymentResponse(
                result.payment().id(),
                result.payment().merchantId(),
                result.payment().amount(),
                result.payment().currency().getCurrencyCode(),
                result.payment().status().name(),
                result.payment().createdAt(),
                result.replayed());
    }
}

