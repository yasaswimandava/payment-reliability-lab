package com.yasaswimandava.paymentlab.application;

import java.util.UUID;

public final class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(UUID paymentId) {
        super("Payment was not found: " + paymentId);
    }
}
