package com.yasaswimandava.paymentlab.adapter.inmemory;

import com.yasaswimandava.paymentlab.port.IdempotencyRecord;
import com.yasaswimandava.paymentlab.port.IdempotencyRepository;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryIdempotencyRepository implements IdempotencyRepository {

    private final Map<String, IdempotencyRecord> records = new ConcurrentHashMap<>();

    @Override
    public Optional<IdempotencyRecord> find(String merchantId, String idempotencyKey) {
        return Optional.ofNullable(records.get(scopedKey(merchantId, idempotencyKey)));
    }

    @Override
    public void save(IdempotencyRecord record) {
        records.putIfAbsent(
                scopedKey(record.merchantId(), record.idempotencyKey()),
                record);
    }

    private String scopedKey(String merchantId, String idempotencyKey) {
        return merchantId + ":" + idempotencyKey;
    }
}

