package com.yasaswimandava.paymentlab.application;

import com.yasaswimandava.paymentlab.domain.Payment;
import java.util.Objects;

public record CreatePaymentResult(Payment payment, boolean replayed) {

    public CreatePaymentResult {
        Objects.requireNonNull(payment, "payment must not be null");
    }
}

