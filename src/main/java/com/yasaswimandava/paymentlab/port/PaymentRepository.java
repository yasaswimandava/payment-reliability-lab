package com.yasaswimandava.paymentlab.port;

import com.yasaswimandava.paymentlab.domain.Payment;
import com.yasaswimandava.paymentlab.domain.PaymentStatus;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(UUID paymentId);

    boolean completeAuthorization(
            UUID paymentId,
            PaymentStatus expectedStatus,
            PaymentStatus newStatus,
            String providerReference);
}
