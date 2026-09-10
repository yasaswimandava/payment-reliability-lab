package com.yasaswimandava.paymentlab.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yasaswimandava.paymentlab.domain.PaymentReceivedEvent;
import com.yasaswimandava.paymentlab.port.ProcessedPaymentEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentEventProcessorTest {

    private static final Instant NOW = Instant.parse("2026-09-09T22:00:00Z");

    @Mock
    private ProcessedPaymentEventRepository processedEventRepository;

    @Test
    void parsesPaymentEventsAndReportsDuplicateDeliveriesWithoutRepeatingTheEffect() {
        String payload = """
                {
                  "eventId": "2aae639c-b7e4-44ed-a6ca-6b8340491222",
                  "paymentId": "2daef927-8eb7-46ab-9a5a-49cd552a3210",
                  "merchantId": "merchant-1",
                  "amount": 42.50,
                  "currency": "USD",
                  "occurredAt": "2026-09-09T21:59:00Z"
                }
                """;
        when(processedEventRepository.recordIfFirst(any(), any()))
                .thenReturn(true)
                .thenReturn(false);
        PaymentEventProcessor processor = new PaymentEventProcessor(
                new ObjectMapper().findAndRegisterModules(),
                processedEventRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));

        EventProcessingResult first = processor.process(payload);
        EventProcessingResult duplicate = processor.process(payload);

        ArgumentCaptor<PaymentReceivedEvent> eventCaptor =
                ArgumentCaptor.forClass(PaymentReceivedEvent.class);
        verify(processedEventRepository, org.mockito.Mockito.times(2))
                .recordIfFirst(eventCaptor.capture(), org.mockito.Mockito.eq(NOW));
        assertThat(eventCaptor.getValue().merchantId()).isEqualTo("merchant-1");
        assertThat(eventCaptor.getValue().currency().getCurrencyCode()).isEqualTo("USD");
        assertThat(first).isEqualTo(EventProcessingResult.PROCESSED);
        assertThat(duplicate).isEqualTo(EventProcessingResult.DUPLICATE);
    }

    @Test
    void rejectsMalformedEventsBeforeTheyReachTheConsumerLedger() {
        PaymentEventProcessor processor = new PaymentEventProcessor(
                new ObjectMapper().findAndRegisterModules(),
                processedEventRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> processor.process("{not-json}"))
                .isInstanceOf(InvalidPaymentEventException.class)
                .hasMessage("Invalid PAYMENT_RECEIVED payload");
    }
}
