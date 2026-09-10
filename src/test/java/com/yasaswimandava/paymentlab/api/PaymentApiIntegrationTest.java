package com.yasaswimandava.paymentlab.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.yasaswimandava.paymentlab.application.CreatePaymentCommand;
import com.yasaswimandava.paymentlab.application.CreatePaymentResult;
import com.yasaswimandava.paymentlab.application.PaymentOperations;
import com.yasaswimandava.paymentlab.domain.Payment;
import com.yasaswimandava.paymentlab.port.PaymentRepository;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(PaymentApiIntegrationTest.ConcurrencyTestConfiguration.class)
class PaymentApiIntegrationTest {

    private static final String PAYMENTS_URL = "/api/v1/payments";
    private static final String MERCHANT_ID = "merchant-api-test";
    private static final String IDEMPOTENCY_KEY = "order-api-123";

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.11-bookworm");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PaymentOperations paymentOperations;

    @Test
    void createsAPaymentAndReplaysAnIdenticalRequest() throws Exception {
        MvcResult original = mockMvc.perform(post(PAYMENTS_URL)
                        .header("X-Merchant-Id", MERCHANT_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson("42.50")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(jsonPath("$.merchantId").value(MERCHANT_ID))
                .andExpect(jsonPath("$.amount").value(42.50))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andReturn();

        String originalPaymentId = JsonPath.read(
                original.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post(PAYMENTS_URL)
                        .header("X-Merchant-Id", MERCHANT_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson("42.50")))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(originalPaymentId));
    }

    @Test
    void returnsConflictWhenTheKeyIsReusedForDifferentDetails() throws Exception {
        String conflictKey = "order-api-conflict";
        mockMvc.perform(post(PAYMENTS_URL)
                        .header("X-Merchant-Id", MERCHANT_ID)
                        .header("Idempotency-Key", conflictKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson("42.50")))
                .andExpect(status().isCreated());

        mockMvc.perform(post(PAYMENTS_URL)
                        .header("X-Merchant-Id", MERCHANT_ID)
                        .header("Idempotency-Key", conflictKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson("50.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Idempotency key conflict"))
                .andExpect(jsonPath("$.detail").value(
                        "Idempotency key was already used for a different request: " + conflictKey));
    }

    @Test
    void rejectsAnInvalidPaymentRequest() throws Exception {
        mockMvc.perform(post(PAYMENTS_URL)
                        .header("X-Merchant-Id", MERCHANT_ID)
                        .header("Idempotency-Key", "invalid-amount")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson("0")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retrievesAnExistingPayment() throws Exception {
        MvcResult created = mockMvc.perform(post(PAYMENTS_URL)
                        .header("X-Merchant-Id", MERCHANT_ID)
                        .header("Idempotency-Key", "order-to-retrieve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson("84.25")))
                .andExpect(status().isCreated())
                .andReturn();
        String paymentId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get(PAYMENTS_URL + "/" + paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId))
                .andExpect(jsonPath("$.amount").value(84.25))
                .andExpect(jsonPath("$.replayed").value(false));
    }

    @Test
    void returnsNotFoundForAnUnknownPayment() throws Exception {
        String missingPaymentId = "81e13cdc-cceb-47d4-8871-c49cd69aa344";

        mockMvc.perform(get(PAYMENTS_URL + "/" + missingPaymentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Payment not found"))
                .andExpect(jsonPath("$.detail").value(
                        "Payment was not found: " + missingPaymentId));
    }

    @Test
    void persistsThePaymentAndItsIdempotencyRecord() throws Exception {
        String persistenceKey = "order-persistence-test";
        MvcResult created = mockMvc.perform(post(PAYMENTS_URL)
                        .header("X-Merchant-Id", MERCHANT_ID)
                        .header("Idempotency-Key", persistenceKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson("125.75")))
                .andExpect(status().isCreated())
                .andReturn();
        String paymentId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        Integer paymentCount = jdbcTemplate.queryForObject(
                "select count(*) from payments where id = ?::uuid",
                Integer.class,
                paymentId);
        Integer idempotencyCount = jdbcTemplate.queryForObject(
                """
                select count(*) from idempotency_keys
                where merchant_id = ? and idempotency_key = ?
                """,
                Integer.class,
                MERCHANT_ID,
                persistenceKey);

        org.assertj.core.api.Assertions.assertThat(paymentCount).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(idempotencyCount).isEqualTo(1);
    }

    @Test
    void stagesExactlyOnePaymentReceivedEventForANewPaymentAndItsReplay() throws Exception {
        String outboxKey = "order-outbox-test";
        MvcResult created = mockMvc.perform(post(PAYMENTS_URL)
                        .header("X-Merchant-Id", MERCHANT_ID)
                        .header("Idempotency-Key", outboxKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson("315.40")))
                .andExpect(status().isCreated())
                .andReturn();
        String paymentId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post(PAYMENTS_URL)
                        .header("X-Merchant-Id", MERCHANT_ID)
                        .header("Idempotency-Key", outboxKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson("315.40")))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"));

        Long eventCount = jdbcTemplate.queryForObject(
                "select count(*) from outbox_events where aggregate_id = ?::uuid",
                Long.class,
                paymentId);
        String eventType = jdbcTemplate.queryForObject(
                "select event_type from outbox_events where aggregate_id = ?::uuid",
                String.class,
                paymentId);
        String payloadPaymentId = jdbcTemplate.queryForObject(
                "select payload ->> 'paymentId' from outbox_events where aggregate_id = ?::uuid",
                String.class,
                paymentId);

        org.assertj.core.api.Assertions.assertThat(eventCount).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(eventType).isEqualTo("PAYMENT_RECEIVED");
        org.assertj.core.api.Assertions.assertThat(payloadPaymentId).isEqualTo(paymentId);
    }

    @Test
    void concurrentIdenticalRequestsHaveExactlyOneBusinessEffect() throws Exception {
        String merchantId = "merchant-concurrency-test";
        String idempotencyKey = "concurrent-order-1";
        CreatePaymentCommand command = new CreatePaymentCommand(
                merchantId,
                new BigDecimal("250.00"),
                Currency.getInstance("USD"));
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            List<Future<CreatePaymentResult>> futures = List.of(
                    executor.submit(() -> paymentOperations.create(idempotencyKey, command)),
                    executor.submit(() -> paymentOperations.create(idempotencyKey, command)));

            CreatePaymentResult first = futures.get(0).get(10, TimeUnit.SECONDS);
            CreatePaymentResult second = futures.get(1).get(10, TimeUnit.SECONDS);
            long storedPayments = jdbcTemplate.queryForObject(
                    "select count(*) from payments where merchant_id = ?",
                    Long.class,
                    merchantId);
            long stagedEvents = jdbcTemplate.queryForObject(
                    """
                    select count(*) from outbox_events
                    where aggregate_id = ?::uuid and event_type = 'PAYMENT_RECEIVED'
                    """,
                    Long.class,
                    first.payment().id());

            org.assertj.core.api.Assertions.assertThat(
                            List.of(first.payment().id(), second.payment().id()))
                    .containsOnly(first.payment().id());
            org.assertj.core.api.Assertions.assertThat(
                            List.of(first.replayed(), second.replayed()))
                    .containsExactlyInAnyOrder(false, true);
            org.assertj.core.api.Assertions.assertThat(storedPayments).isEqualTo(1);
            org.assertj.core.api.Assertions.assertThat(stagedEvents).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private String paymentJson(String amount) {
        return """
                {
                  "amount": %s,
                  "currency": "USD"
                }
                """.formatted(amount);
    }

    @TestConfiguration
    static class ConcurrencyTestConfiguration {

        @Bean
        @Primary
        PaymentRepository barrierPaymentRepository(
                @Qualifier("paymentRepository") PaymentRepository delegate) {
            return new BarrierPaymentRepository(delegate);
        }
    }

    private static final class BarrierPaymentRepository implements PaymentRepository {

        private static final String CONCURRENT_MERCHANT = "merchant-concurrency-test";

        private final PaymentRepository delegate;
        private final CyclicBarrier saveBarrier = new CyclicBarrier(2);

        private BarrierPaymentRepository(PaymentRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public Payment save(Payment payment) {
            if (CONCURRENT_MERCHANT.equals(payment.merchantId())) {
                awaitConcurrentSave();
            }
            return delegate.save(payment);
        }

        @Override
        public Optional<Payment> findById(UUID paymentId) {
            return delegate.findById(paymentId);
        }

        private void awaitConcurrentSave() {
            try {
                saveBarrier.await(5, TimeUnit.SECONDS);
            } catch (Exception exception) {
                throw new IllegalStateException("Concurrent requests did not reach the barrier", exception);
            }
        }
    }
}
