package com.yasaswimandava.paymentlab.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yasaswimandava.paymentlab.domain.OutboxMessage;
import com.yasaswimandava.paymentlab.domain.Payment;
import com.yasaswimandava.paymentlab.domain.PaymentReceivedEvent;
import com.yasaswimandava.paymentlab.domain.PaymentStatus;
import com.yasaswimandava.paymentlab.port.IdempotencyRecord;
import com.yasaswimandava.paymentlab.port.IdempotencyRepository;
import com.yasaswimandava.paymentlab.port.PaymentRepository;
import com.yasaswimandava.paymentlab.port.OutboxRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentApplicationServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-09-09T16:00:00Z"), ZoneOffset.UTC);
    private static final String MERCHANT_ID = "merchant-123";
    private static final String IDEMPOTENCY_KEY = "checkout-session-456";

    private InMemoryPaymentRepository paymentRepository;
    private InMemoryOutboxRepository outboxRepository;
    private PaymentApplicationService service;

    @BeforeEach
    void setUp() {
        paymentRepository = new InMemoryPaymentRepository();
        outboxRepository = new InMemoryOutboxRepository();
        service = new PaymentApplicationService(
                paymentRepository,
                new InMemoryIdempotencyRepository(),
                outboxRepository,
                FIXED_CLOCK);
    }

    @Test
    void createsPaymentForANewIdempotencyKey() {
        CreatePaymentResult result = service.create(IDEMPOTENCY_KEY, validCommand());

        assertThat(result.payment().merchantId()).isEqualTo(MERCHANT_ID);
        assertThat(result.payment().amount()).isEqualByComparingTo("42.50");
        assertThat(result.payment().currency()).isEqualTo(Currency.getInstance("USD"));
        assertThat(result.payment().status()).isEqualTo(PaymentStatus.RECEIVED);
        assertThat(result.payment().createdAt()).isEqualTo(FIXED_CLOCK.instant());
        assertThat(result.replayed()).isFalse();
        assertThat(paymentRepository.savedCount()).isEqualTo(1);
        assertThat(outboxRepository.savedCount()).isEqualTo(1);
    }

    @Test
    void returnsOriginalPaymentWhenTheSameRequestIsRetried() {
        CreatePaymentResult original = service.create(IDEMPOTENCY_KEY, validCommand());
        CreatePaymentResult retry = service.create(IDEMPOTENCY_KEY, validCommand());

        assertThat(retry.payment()).isEqualTo(original.payment());
        assertThat(retry.replayed()).isTrue();
        assertThat(paymentRepository.savedCount()).isEqualTo(1);
        assertThat(outboxRepository.savedCount()).isEqualTo(1);
    }

    @Test
    void rejectsAnIdempotencyKeyReusedWithDifferentPaymentDetails() {
        service.create(IDEMPOTENCY_KEY, validCommand());
        CreatePaymentCommand changedAmount = new CreatePaymentCommand(
                MERCHANT_ID,
                new BigDecimal("43.00"),
                Currency.getInstance("USD"));

        assertThatThrownBy(() -> service.create(IDEMPOTENCY_KEY, changedAmount))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining(IDEMPOTENCY_KEY);
        assertThat(paymentRepository.savedCount()).isEqualTo(1);
        assertThat(outboxRepository.savedCount()).isEqualTo(1);
    }

    @Test
    void rejectsNonPositivePaymentAmounts() {
        assertThatThrownBy(() -> new CreatePaymentCommand(
                MERCHANT_ID,
                BigDecimal.ZERO,
                Currency.getInstance("USD")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void retrievesAnExistingPaymentById() {
        Payment created = service.create(IDEMPOTENCY_KEY, validCommand()).payment();

        Payment found = service.get(created.id());

        assertThat(found).isEqualTo(created);
    }

    @Test
    void reportsWhenAPaymentDoesNotExist() {
        UUID missingPaymentId = UUID.fromString("3d8ef55a-20d3-4c2e-a98e-b3ad1a70fc22");

        assertThatThrownBy(() -> service.get(missingPaymentId))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining(missingPaymentId.toString());
    }

    private CreatePaymentCommand validCommand() {
        return new CreatePaymentCommand(
                MERCHANT_ID,
                new BigDecimal("42.50"),
                Currency.getInstance("USD"));
    }

    private static final class InMemoryPaymentRepository implements PaymentRepository {

        private final Map<UUID, Payment> payments = new HashMap<>();

        @Override
        public Payment save(Payment payment) {
            payments.put(payment.id(), payment);
            return payment;
        }

        @Override
        public Optional<Payment> findById(UUID paymentId) {
            return Optional.ofNullable(payments.get(paymentId));
        }

        int savedCount() {
            return payments.size();
        }
    }

    private static final class InMemoryIdempotencyRepository implements IdempotencyRepository {

        private final Map<String, IdempotencyRecord> records = new HashMap<>();

        @Override
        public Optional<IdempotencyRecord> find(String merchantId, String idempotencyKey) {
            return Optional.ofNullable(records.get(scopedKey(merchantId, idempotencyKey)));
        }

        @Override
        public void save(IdempotencyRecord record) {
            records.put(scopedKey(record.merchantId(), record.idempotencyKey()), record);
        }

        private String scopedKey(String merchantId, String idempotencyKey) {
            return merchantId + ":" + idempotencyKey;
        }
    }

    private static final class InMemoryOutboxRepository implements OutboxRepository {

        private final Map<UUID, PaymentReceivedEvent> events = new HashMap<>();

        @Override
        public void save(PaymentReceivedEvent event) {
            events.put(event.eventId(), event);
        }

        @Override
        public List<OutboxMessage> claimAvailable(
                String workerId,
                int batchSize,
                Instant claimAt,
                Duration lockTimeout) {
            throw new UnsupportedOperationException("Not required by this unit test");
        }

        @Override
        public boolean markPublished(UUID eventId, String workerId, Instant publishedAt) {
            throw new UnsupportedOperationException("Not required by this unit test");
        }

        @Override
        public boolean reschedule(
                UUID eventId,
                String workerId,
                Instant availableAt,
                String error) {
            throw new UnsupportedOperationException("Not required by this unit test");
        }

        int savedCount() {
            return events.size();
        }
    }
}
