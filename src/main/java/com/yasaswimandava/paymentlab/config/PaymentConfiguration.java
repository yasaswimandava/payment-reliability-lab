package com.yasaswimandava.paymentlab.config;

import com.yasaswimandava.paymentlab.adapter.inmemory.InMemoryIdempotencyRepository;
import com.yasaswimandava.paymentlab.adapter.inmemory.InMemoryPaymentRepository;
import com.yasaswimandava.paymentlab.application.PaymentApplicationService;
import com.yasaswimandava.paymentlab.port.IdempotencyRepository;
import com.yasaswimandava.paymentlab.port.PaymentRepository;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    PaymentRepository paymentRepository() {
        return new InMemoryPaymentRepository();
    }

    @Bean
    IdempotencyRepository idempotencyRepository() {
        return new InMemoryIdempotencyRepository();
    }

    @Bean
    PaymentApplicationService paymentApplicationService(
            PaymentRepository paymentRepository,
            IdempotencyRepository idempotencyRepository,
            Clock clock) {
        return new PaymentApplicationService(paymentRepository, idempotencyRepository, clock);
    }
}

