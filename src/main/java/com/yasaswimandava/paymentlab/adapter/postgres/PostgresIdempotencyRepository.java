package com.yasaswimandava.paymentlab.adapter.postgres;

import com.yasaswimandava.paymentlab.port.IdempotencyRecord;
import com.yasaswimandava.paymentlab.port.IdempotencyRepository;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresIdempotencyRepository implements IdempotencyRepository {

    private static final String FIND_RECORD = """
            select merchant_id, idempotency_key, request_fingerprint, payment_id
            from idempotency_keys
            where merchant_id = ? and idempotency_key = ?
            """;
    private static final String INSERT_RECORD = """
            insert into idempotency_keys (
                merchant_id, idempotency_key, request_fingerprint, payment_id
            ) values (?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgresIdempotencyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<IdempotencyRecord> find(String merchantId, String idempotencyKey) {
        return jdbcTemplate.query(
                        FIND_RECORD,
                        (resultSet, rowNumber) -> new IdempotencyRecord(
                                resultSet.getString("merchant_id"),
                                resultSet.getString("idempotency_key"),
                                resultSet.getString("request_fingerprint"),
                                resultSet.getObject("payment_id", java.util.UUID.class)),
                        merchantId,
                        idempotencyKey)
                .stream()
                .findFirst();
    }

    @Override
    public void save(IdempotencyRecord record) {
        jdbcTemplate.update(
                INSERT_RECORD,
                record.merchantId(),
                record.idempotencyKey(),
                record.requestFingerprint(),
                record.paymentId());
    }
}
