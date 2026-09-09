package com.yasaswimandava.paymentlab.application;

import com.yasaswimandava.paymentlab.domain.Payment;
import com.yasaswimandava.paymentlab.domain.PaymentStatus;
import com.yasaswimandava.paymentlab.port.IdempotencyRecord;
import com.yasaswimandava.paymentlab.port.IdempotencyRepository;
import com.yasaswimandava.paymentlab.port.PaymentRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

public final class PaymentApplicationService implements PaymentOperations {

    private final PaymentRepository paymentRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final Clock clock;

    public PaymentApplicationService(
            PaymentRepository paymentRepository,
            IdempotencyRepository idempotencyRepository,
            Clock clock) {
        this.paymentRepository = Objects.requireNonNull(paymentRepository);
        this.idempotencyRepository = Objects.requireNonNull(idempotencyRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public CreatePaymentResult create(
            String idempotencyKey,
            CreatePaymentCommand command) {
        requireIdempotencyKey(idempotencyKey);
        Objects.requireNonNull(command, "command must not be null");

        String fingerprint = fingerprint(command);
        return idempotencyRepository.find(command.merchantId(), idempotencyKey)
                .map(record -> replay(record, fingerprint, idempotencyKey))
                .orElseGet(() -> createPayment(idempotencyKey, command, fingerprint));
    }

    @Override
    public Payment get(UUID paymentId) {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    private CreatePaymentResult replay(
            IdempotencyRecord record,
            String fingerprint,
            String idempotencyKey) {
        if (!record.requestFingerprint().equals(fingerprint)) {
            throw new IdempotencyConflictException(idempotencyKey);
        }

        Payment payment = paymentRepository.findById(record.paymentId())
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotency record points to a missing payment: " + record.paymentId()));
        return new CreatePaymentResult(payment, true);
    }

    private CreatePaymentResult createPayment(
            String idempotencyKey,
            CreatePaymentCommand command,
            String fingerprint) {
        Payment payment = new Payment(
                UUID.randomUUID(),
                command.merchantId(),
                command.amount(),
                command.currency(),
                PaymentStatus.RECEIVED,
                clock.instant());
        Payment savedPayment = paymentRepository.save(payment);
        idempotencyRepository.save(new IdempotencyRecord(
                command.merchantId(),
                idempotencyKey,
                fingerprint,
                savedPayment.id()));
        return new CreatePaymentResult(savedPayment, false);
    }

    private String fingerprint(CreatePaymentCommand command) {
        String canonicalRequest = String.join(
                "|",
                command.merchantId(),
                command.amount().stripTrailingZeros().toPlainString(),
                command.currency().getCurrencyCode());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalRequest.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
    }
}
