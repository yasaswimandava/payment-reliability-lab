package com.yasaswimandava.paymentlab.provider;

public final class ProviderCircuitOpenException extends RuntimeException {

    public ProviderCircuitOpenException(String message, Throwable cause) {
        super(message, cause);
    }
}
