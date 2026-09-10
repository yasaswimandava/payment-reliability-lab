package com.yasaswimandava.paymentlab.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yasaswimandava.paymentlab.domain.Payment;
import com.yasaswimandava.paymentlab.domain.PaymentReceivedEvent;
import com.yasaswimandava.paymentlab.domain.PaymentStatus;
import com.yasaswimandava.paymentlab.port.PaymentProvider;
import com.yasaswimandava.paymentlab.port.PaymentRepository;
import com.yasaswimandava.paymentlab.provider.ProviderAuthorization;
import com.yasaswimandava.paymentlab.provider.ProviderAuthorizationRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentAuthorizationServiceTest {

    private final PaymentRepository paymentRepository =
            org.mockito.Mockito.mock(PaymentRepository.class);
    private final PaymentProvider paymentProvider =
            org.mockito.Mockito.mock(PaymentProvider.class);
    private final PaymentAuthorizationService service =
            new PaymentAuthorizationService(paymentRepository, paymentProvider);

    @Test
    void authorizesAReceivedPaymentAndPersistsTheProviderReference() {
        Payment payment = payment(PaymentStatus.RECEIVED);
        ProviderAuthorizationRequest request = new ProviderAuthorizationRequest(
                payment.id(), payment.merchantId(), payment.amount(), payment.currency());
        when(paymentRepository.findById(payment.id())).thenReturn(Optional.of(payment));
        when(paymentProvider.authorize(request))
                .thenReturn(ProviderAuthorization.approved("provider-123"));
        when(paymentRepository.completeAuthorization(
                        payment.id(),
                        PaymentStatus.RECEIVED,
                        PaymentStatus.AUTHORIZED,
                        "provider-123"))
                .thenReturn(true);

        AuthorizationProcessingResult result = service.authorize(event(payment));

        assertThat(result).isEqualTo(AuthorizationProcessingResult.AUTHORIZED);
        verify(paymentRepository).completeAuthorization(
                payment.id(),
                PaymentStatus.RECEIVED,
                PaymentStatus.AUTHORIZED,
                "provider-123");
    }

    @Test
    void recordsAProviderDeclineAsAFinalBusinessOutcome() {
        Payment payment = payment(PaymentStatus.RECEIVED);
        when(paymentRepository.findById(payment.id())).thenReturn(Optional.of(payment));
        when(paymentProvider.authorize(org.mockito.ArgumentMatchers.any()))
                .thenReturn(ProviderAuthorization.declined("provider-456"));
        when(paymentRepository.completeAuthorization(
                        payment.id(),
                        PaymentStatus.RECEIVED,
                        PaymentStatus.DECLINED,
                        "provider-456"))
                .thenReturn(true);

        AuthorizationProcessingResult result = service.authorize(event(payment));

        assertThat(result).isEqualTo(AuthorizationProcessingResult.DECLINED);
    }

    @Test
    void makesARedeliveryANoOpAfterThePaymentReachedAFinalState() {
        Payment payment = payment(PaymentStatus.AUTHORIZED);
        when(paymentRepository.findById(payment.id())).thenReturn(Optional.of(payment));

        AuthorizationProcessingResult result = service.authorize(event(payment));

        assertThat(result).isEqualTo(AuthorizationProcessingResult.ALREADY_FINAL);
        verifyNoInteractions(paymentProvider);
    }

    private Payment payment(PaymentStatus status) {
        return new Payment(
                UUID.fromString("f1391146-e720-4fb8-a5d8-adc209083c59"),
                "merchant-provider-test",
                new BigDecimal("49.25"),
                Currency.getInstance("USD"),
                status,
                Instant.parse("2026-09-09T22:00:00Z"));
    }

    private PaymentReceivedEvent event(Payment payment) {
        return new PaymentReceivedEvent(
                UUID.fromString("ba02f921-c453-4163-8caf-03ce6788a2a8"),
                payment.id(),
                payment.merchantId(),
                payment.amount(),
                payment.currency(),
                payment.createdAt());
    }
}
