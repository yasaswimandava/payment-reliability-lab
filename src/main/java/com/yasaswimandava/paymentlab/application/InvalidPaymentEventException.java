package com.yasaswimandava.paymentlab.application;

public final class InvalidPaymentEventException extends RuntimeException {

    public InvalidPaymentEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
