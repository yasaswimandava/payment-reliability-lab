package com.yasaswimandava.paymentlab.application;

import com.yasaswimandava.paymentlab.domain.Payment;
import com.yasaswimandava.paymentlab.domain.PaymentReceivedEvent;
import com.yasaswimandava.paymentlab.domain.PaymentStatus;
import com.yasaswimandava.paymentlab.port.PaymentProvider;
import com.yasaswimandava.paymentlab.port.PaymentRepository;
import com.yasaswimandava.paymentlab.provider.ProviderAuthorization;
import com.yasaswimandava.paymentlab.provider.ProviderAuthorizationRequest;
import com.yasaswimandava.paymentlab.provider.ProviderDecision;
import java.util.Objects;

public final class PaymentAuthorizationService {

    private final PaymentRepository paymentRepository;
    private final PaymentProvider paymentProvider;

    public PaymentAuthorizationService(
            PaymentRepository paymentRepository,
            PaymentProvider paymentProvider) {
        this.paymentRepository = Objects.requireNonNull(paymentRepository);
        this.paymentProvider = Objects.requireNonNull(paymentProvider);
    }

    public AuthorizationProcessingResult authorize(PaymentReceivedEvent event) {
        Payment payment = paymentRepository.findById(event.paymentId())
                .orElseThrow(() -> new PaymentNotFoundException(event.paymentId()));
        if (payment.status() != PaymentStatus.RECEIVED) {
            return AuthorizationProcessingResult.ALREADY_FINAL;
        }

        ProviderAuthorization authorization = paymentProvider.authorize(
                new ProviderAuthorizationRequest(
                        payment.id(),
                        payment.merchantId(),
                        payment.amount(),
                        payment.currency()));
        PaymentStatus newStatus = authorization.decision() == ProviderDecision.APPROVED
                ? PaymentStatus.AUTHORIZED
                : PaymentStatus.DECLINED;
        boolean updated = paymentRepository.completeAuthorization(
                payment.id(),
                PaymentStatus.RECEIVED,
                newStatus,
                authorization.providerReference());
        if (!updated) {
            return AuthorizationProcessingResult.ALREADY_FINAL;
        }
        return newStatus == PaymentStatus.AUTHORIZED
                ? AuthorizationProcessingResult.AUTHORIZED
                : AuthorizationProcessingResult.DECLINED;
    }
}
