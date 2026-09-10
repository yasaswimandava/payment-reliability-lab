package com.yasaswimandava.paymentlab.port;

import com.yasaswimandava.paymentlab.domain.PaymentReceivedEvent;
import java.time.Instant;

@FunctionalInterface
public interface ProcessedPaymentEventRepository {

    boolean recordIfFirst(PaymentReceivedEvent event, Instant processedAt);
}
