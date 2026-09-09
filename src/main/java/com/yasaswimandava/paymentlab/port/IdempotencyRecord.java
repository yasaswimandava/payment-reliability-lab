package com.yasaswimandava.paymentlab.port;

import java.util.Objects;
import java.util.UUID;

public record IdempotencyRecord(
        String merchantId,
        String idempotencyKey,
        String requestFingerprint,
        UUID paymentId) {

    public IdempotencyRecord {
        Objects.requireNonNull(merchantId, "merchantId must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Objects.requireNonNull(requestFingerprint, "requestFingerprint must not be null");
        Objects.requireNonNull(paymentId, "paymentId must not be null");
    }
}

