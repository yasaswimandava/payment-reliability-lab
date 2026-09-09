package com.yasaswimandava.paymentlab.application;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

public record CreatePaymentCommand(
        String merchantId,
        BigDecimal amount,
        Currency currency) {

    public CreatePaymentCommand {
        Objects.requireNonNull(merchantId, "merchantId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");

        if (merchantId.isBlank()) {
            throw new IllegalArgumentException("merchantId must not be blank");
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}

