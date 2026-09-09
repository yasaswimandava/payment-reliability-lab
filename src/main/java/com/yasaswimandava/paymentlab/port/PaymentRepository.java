package com.yasaswimandava.paymentlab.port;

import com.yasaswimandava.paymentlab.domain.Payment;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(UUID paymentId);
}

