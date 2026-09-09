package com.yasaswimandava.paymentlab.application;

import com.yasaswimandava.paymentlab.domain.Payment;
import java.util.UUID;

public interface PaymentOperations {

    CreatePaymentResult create(String idempotencyKey, CreatePaymentCommand command);

    Payment get(UUID paymentId);
}
