package com.yasaswimandava.paymentlab.application;

public final class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String idempotencyKey) {
        super("Idempotency key was already used for a different request: " + idempotencyKey);
    }
}

