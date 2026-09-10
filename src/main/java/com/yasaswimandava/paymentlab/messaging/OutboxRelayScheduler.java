package com.yasaswimandava.paymentlab.messaging;

import com.yasaswimandava.paymentlab.application.OutboxRelay;
import com.yasaswimandava.paymentlab.application.RelayBatchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public final class OutboxRelayScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final OutboxRelay relay;

    public OutboxRelayScheduler(OutboxRelay relay) {
        this.relay = relay;
    }

    @Scheduled(
            fixedDelayString = "${payment.events.relay-interval:500ms}",
            initialDelayString = "${payment.events.relay-initial-delay:1s}")
    public void publishAvailableEvents() {
        RelayBatchResult result = relay.relayOnce();
        if (result.claimed() > 0) {
            LOGGER.info(
                    "Outbox relay batch completed: claimed={}, published={}, failed={}",
                    result.claimed(),
                    result.published(),
                    result.failed());
        }
    }
}
