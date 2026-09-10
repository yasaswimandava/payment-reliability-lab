package com.yasaswimandava.paymentlab.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yasaswimandava.paymentlab.domain.PaymentReceivedEvent;
import com.yasaswimandava.paymentlab.messaging.PaymentReceivedEventCodec;
import com.yasaswimandava.paymentlab.port.ProcessedPaymentEventRepository;
import java.time.Clock;
import java.util.Objects;

public final class PaymentEventProcessor {

    private final PaymentReceivedEventCodec eventCodec;
    private final ProcessedPaymentEventRepository processedEventRepository;
    private final Clock clock;

    public PaymentEventProcessor(
            ObjectMapper objectMapper,
            ProcessedPaymentEventRepository processedEventRepository,
            Clock clock) {
        this.eventCodec = new PaymentReceivedEventCodec(objectMapper);
        this.processedEventRepository = Objects.requireNonNull(processedEventRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    public EventProcessingResult process(String payload) {
        PaymentReceivedEvent event = eventCodec.decode(payload);
        boolean firstDelivery = processedEventRepository.recordIfFirst(event, clock.instant());
        return firstDelivery ? EventProcessingResult.PROCESSED : EventProcessingResult.DUPLICATE;
    }

}
