package com.yasaswimandava.paymentlab.provider;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

public record ProviderAuthorizationRequest(
        UUID paymentId,
        String merchantId,
        BigDecimal amount,
        Currency currency) {

    public ProviderAuthorizationRequest {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        if (merchantId == null || merchantId.isBlank()) {
            throw new IllegalArgumentException("merchantId must not be blank");
        }
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
    }
}
