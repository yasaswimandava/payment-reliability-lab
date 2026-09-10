package com.yasaswimandava.paymentlab.adapter.postgres;

import com.yasaswimandava.paymentlab.domain.Payment;
import com.yasaswimandava.paymentlab.domain.PaymentStatus;
import com.yasaswimandava.paymentlab.port.PaymentRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresPaymentRepository implements PaymentRepository {

    private static final String INSERT_PAYMENT = """
            insert into payments (id, merchant_id, amount, currency, status, created_at)
            values (?, ?, ?, ?, ?, ?)
            """;
    private static final String FIND_PAYMENT = """
            select id, merchant_id, amount, currency, status, provider_reference, created_at
            from payments
            where id = ?
            """;
    private static final String COMPLETE_AUTHORIZATION = """
            update payments
            set status = ?,
                provider_reference = ?,
                updated_at = current_timestamp
            where id = ? and status = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgresPaymentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Payment save(Payment payment) {
        jdbcTemplate.update(
                INSERT_PAYMENT,
                payment.id(),
                payment.merchantId(),
                payment.amount(),
                payment.currency().getCurrencyCode(),
                payment.status().name(),
                OffsetDateTime.ofInstant(payment.createdAt(), ZoneOffset.UTC));
        return payment;
    }

    @Override
    public Optional<Payment> findById(UUID paymentId) {
        return jdbcTemplate.query(FIND_PAYMENT, this::mapPayment, paymentId)
                .stream()
                .findFirst();
    }

    @Override
    public boolean completeAuthorization(
            UUID paymentId,
            PaymentStatus expectedStatus,
            PaymentStatus newStatus,
            String providerReference) {
        return jdbcTemplate.update(
                COMPLETE_AUTHORIZATION,
                newStatus.name(),
                providerReference,
                paymentId,
                expectedStatus.name()) == 1;
    }

    private Payment mapPayment(ResultSet resultSet, int rowNumber) throws SQLException {
        return new Payment(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("merchant_id"),
                resultSet.getBigDecimal("amount"),
                Currency.getInstance(resultSet.getString("currency")),
                PaymentStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("provider_reference"),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
