package com.yasaswimandava.paymentlab.port;

import java.util.Optional;

public interface IdempotencyRepository {

    Optional<IdempotencyRecord> find(String merchantId, String idempotencyKey);

    void save(IdempotencyRecord record);
}

