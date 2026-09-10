package com.yasaswimandava.paymentlab.messaging;

import com.yasaswimandava.paymentlab.application.AuthorizationProcessingResult;
import com.yasaswimandava.paymentlab.application.PaymentAuthorizationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;

public final class PaymentAuthorizationKafkaListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(PaymentAuthorizationKafkaListener.class);

    private final PaymentReceivedEventCodec eventCodec;
    private final PaymentAuthorizationService authorizationService;

    public PaymentAuthorizationKafkaListener(
            PaymentReceivedEventCodec eventCodec,
            PaymentAuthorizationService authorizationService) {
        this.eventCodec = eventCodec;
        this.authorizationService = authorizationService;
    }

    @KafkaListener(
            id = "payment-provider-listener",
            topics = "${payment.events.topic}",
            groupId = "${payment.provider.consumer-group}")
    public void onPaymentReceived(ConsumerRecord<String, String> record) {
        AuthorizationProcessingResult result = authorizationService.authorize(
                eventCodec.decode(record.value()));
        LOGGER.info(
                "Payment provider workflow completed: topic={}, partition={}, offset={}, result={}",
                record.topic(),
                record.partition(),
                record.offset(),
                result);
    }
}
