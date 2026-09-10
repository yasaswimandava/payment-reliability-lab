package com.yasaswimandava.paymentlab.config;

import com.yasaswimandava.paymentlab.adapter.postgres.PostgresIdempotencyRepository;
import com.yasaswimandava.paymentlab.adapter.postgres.PostgresPaymentRepository;
import com.yasaswimandava.paymentlab.adapter.postgres.PostgresOutboxRepository;
import com.yasaswimandava.paymentlab.adapter.postgres.TransactionalPaymentOperations;
import com.yasaswimandava.paymentlab.application.PaymentApplicationService;
import com.yasaswimandava.paymentlab.application.PaymentOperations;
import com.yasaswimandava.paymentlab.port.IdempotencyRepository;
import com.yasaswimandava.paymentlab.port.PaymentRepository;
import com.yasaswimandava.paymentlab.port.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class PaymentConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    PaymentRepository paymentRepository(JdbcTemplate jdbcTemplate) {
        return new PostgresPaymentRepository(jdbcTemplate);
    }

    @Bean
    IdempotencyRepository idempotencyRepository(JdbcTemplate jdbcTemplate) {
        return new PostgresIdempotencyRepository(jdbcTemplate);
    }

    @Bean
    OutboxRepository outboxRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        return new PostgresOutboxRepository(jdbcTemplate, objectMapper);
    }

    @Bean
    PaymentApplicationService paymentApplicationService(
            PaymentRepository paymentRepository,
            IdempotencyRepository idempotencyRepository,
            OutboxRepository outboxRepository,
            Clock clock) {
        return new PaymentApplicationService(
                paymentRepository,
                idempotencyRepository,
                outboxRepository,
                clock);
    }

    @Bean
    @Primary
    PaymentOperations transactionalPaymentOperations(
            PaymentApplicationService delegate,
            PlatformTransactionManager transactionManager) {
        return new TransactionalPaymentOperations(delegate, transactionManager);
    }
}
