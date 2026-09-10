package com.yasaswimandava.paymentlab.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpPaymentProviderClientTest {

    private MockRestServiceServer server;
    private HttpPaymentProviderClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpPaymentProviderClient(builder, "http://provider.test");
    }

    @Test
    void sendsThePaymentIdAsProviderIdempotencyKeyAndParsesApproval() {
        ProviderAuthorizationRequest request = request();
        server.expect(requestTo("http://provider.test/simulator/v1/authorizations"))
                .andExpect(header("Idempotency-Key", request.paymentId().toString()))
                .andRespond(withSuccess(
                        "{\"decision\":\"APPROVED\",\"providerReference\":\"sim-123\"}",
                        MediaType.APPLICATION_JSON));

        ProviderAuthorization result = client.authorize(request);

        assertThat(result).isEqualTo(ProviderAuthorization.approved("sim-123"));
        server.verify();
    }

    @Test
    void classifiesServerFailuresAsTransient() {
        server.expect(requestTo("http://provider.test/simulator/v1/authorizations"))
                .andRespond(withServiceUnavailable());

        assertThatThrownBy(() -> client.authorize(request()))
                .isInstanceOf(TransientProviderException.class);
    }

    @Test
    void classifiesRejectedRequestsAsPermanent() {
        server.expect(requestTo("http://provider.test/simulator/v1/authorizations"))
                .andRespond(withBadRequest());

        assertThatThrownBy(() -> client.authorize(request()))
                .isInstanceOf(PermanentProviderException.class);
    }

    private ProviderAuthorizationRequest request() {
        return new ProviderAuthorizationRequest(
                UUID.fromString("f1391146-e720-4fb8-a5d8-adc209083c59"),
                "merchant-provider-test",
                new BigDecimal("49.25"),
                Currency.getInstance("USD"));
    }
}
