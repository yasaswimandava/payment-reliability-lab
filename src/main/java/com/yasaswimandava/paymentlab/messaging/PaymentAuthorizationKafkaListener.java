package com.yasaswimandava.paymentlab.messaging;

import com.yasaswimandava.paymentlab.application.AuthorizationProcessingResult;
import com.yasaswimandava.paymentlab.application.PaymentAuthorizationService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Locale;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;

public final class PaymentAuthorizationKafkaListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(PaymentAuthorizationKafkaListener.class);

    private final PaymentReceivedEventCodec eventCodec;
    private final PaymentAuthorizationService authorizationService;
    private final MeterRegistry meterRegistry;

    public PaymentAuthorizationKafkaListener(
            PaymentReceivedEventCodec eventCodec,
            PaymentAuthorizationService authorizationService,
            MeterRegistry meterRegistry) {
        this.eventCodec = eventCodec;
        this.authorizationService = authorizationService;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(
            id = "payment-provider-listener",
            topics = "${payment.events.topic}",
            groupId = "${payment.provider.consumer-group}")
    public void onPaymentReceived(ConsumerRecord<String, String> record) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "failed";
        try {
            AuthorizationProcessingResult result = authorizationService.authorize(
                    eventCodec.decode(record.value()));
            outcome = result.name().toLowerCase(Locale.ROOT);
            LOGGER.info(
                    "Payment provider workflow completed: "
                            + "topic={}, partition={}, offset={}, result={}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    result);
        } finally {
            Counter.builder("payment.authorization.outcomes")
                    .description("Completed payment authorization workflow outcomes")
                    .tag("result", outcome)
                    .register(meterRegistry)
                    .increment();
            sample.stop(Timer.builder("payment.authorization.duration")
                    .description("Payment authorization workflow duration")
                    .tag("result", outcome)
                    .publishPercentileHistogram()
                    .register(meterRegistry));
        }
    }
}
