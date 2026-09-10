package com.yasaswimandava.paymentlab.messaging;

import com.yasaswimandava.paymentlab.application.EventProcessingResult;
import com.yasaswimandava.paymentlab.application.PaymentEventProcessor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;

public final class PaymentReceivedKafkaListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(PaymentReceivedKafkaListener.class);

    private final PaymentEventProcessor processor;

    public PaymentReceivedKafkaListener(PaymentEventProcessor processor) {
        this.processor = processor;
    }

    @KafkaListener(
            id = "payment-projection-listener",
            topics = "${payment.events.topic}",
            groupId = "${payment.events.consumer-group}")
    public void onPaymentReceived(ConsumerRecord<String, String> record) {
        EventProcessingResult result = processor.process(record.value());
        LOGGER.info(
                "Payment event consumed: topic={}, partition={}, offset={}, result={}",
                record.topic(),
                record.partition(),
                record.offset(),
                result);
    }
}
