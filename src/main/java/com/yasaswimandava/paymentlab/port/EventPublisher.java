package com.yasaswimandava.paymentlab.port;

import com.yasaswimandava.paymentlab.domain.OutboxMessage;

@FunctionalInterface
public interface EventPublisher {

    void publish(OutboxMessage message);
}
