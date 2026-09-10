package com.yasaswimandava.paymentlab.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.redpanda.RedpandaContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "payment.events.relay-initial-delay=100ms",
            "payment.events.relay-interval=100ms",
            "management.otlp.tracing.export.enabled=false"
        })
@Testcontainers
class PaymentPipelineEndToEndTest {

    private static final MockWebServer PROVIDER = startProvider();

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.11-bookworm");

    @Container
    private static final RedpandaContainer REDPANDA = new RedpandaContainer(
            DockerImageName.parse(
                    "docker.redpanda.com/redpandadata/redpanda:v26.2.2"));

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void messagingProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", REDPANDA::getBootstrapServers);
        registry.add("payment.provider.base-url", () -> PROVIDER.url("/").toString());
    }

    @AfterAll
    static void stopProvider() throws IOException {
        PROVIDER.shutdown();
    }

    @Test
    void commitsPublishesConsumesAndAuthorizesOnePayment() throws Exception {
        PROVIDER.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"decision":"APPROVED","providerReference":"e2e-provider-123"}
                        """));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Merchant-Id", "merchant-e2e");
        headers.set("Idempotency-Key", "order-e2e-1");
        ResponseEntity<PaymentResponse> created = restTemplate.exchange(
                "/api/v1/payments",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("amount", 64.25, "currency", "USD"), headers),
                PaymentResponse.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        PaymentResponse accepted = created.getBody();

        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    PaymentResponse payment = restTemplate.getForObject(
                            "/api/v1/payments/{id}",
                            PaymentResponse.class,
                            accepted.id());
                    assertThat(payment.status()).isEqualTo("AUTHORIZED");
                    assertThat(payment.providerReference()).isEqualTo("e2e-provider-123");
                    assertThat(jdbcTemplate.queryForObject(
                                    "select status from outbox_events where aggregate_id = ?",
                                    String.class,
                                    accepted.id()))
                            .isEqualTo("PUBLISHED");
                    assertThat(jdbcTemplate.queryForObject(
                                    "select count(*) from payment_event_projection where payment_id = ?",
                                    Long.class,
                                    accepted.id()))
                            .isEqualTo(1L);
                });

        var providerRequest = PROVIDER.takeRequest();
        assertThat(providerRequest.getHeader("Idempotency-Key"))
                .isEqualTo(accepted.id().toString());
    }

    private static MockWebServer startProvider() {
        MockWebServer server = new MockWebServer();
        try {
            server.start();
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
