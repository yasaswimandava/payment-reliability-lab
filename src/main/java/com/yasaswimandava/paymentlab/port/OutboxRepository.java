package com.yasaswimandava.paymentlab.port;

import com.yasaswimandava.paymentlab.domain.PaymentReceivedEvent;

public interface OutboxRepository {

    void save(PaymentReceivedEvent event);
}
