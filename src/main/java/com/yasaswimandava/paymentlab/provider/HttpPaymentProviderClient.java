package com.yasaswimandava.paymentlab.provider;

import com.yasaswimandava.paymentlab.port.PaymentProvider;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

public final class HttpPaymentProviderClient implements PaymentProvider {

    private final RestClient restClient;

    public HttpPaymentProviderClient(RestClient.Builder builder, String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        this.restClient = Objects.requireNonNull(builder).baseUrl(baseUrl).build();
    }

    @Override
    public ProviderAuthorization authorize(ProviderAuthorizationRequest request) {
        try {
            ProviderAuthorizationResponse response = restClient.post()
                    .uri("/simulator/v1/authorizations")
                    .header("Idempotency-Key", request.paymentId().toString())
                    .body(new ProviderAuthorizationPayload(
                            request.paymentId(),
                            request.merchantId(),
                            request.amount(),
                            request.currency().getCurrencyCode()))
                    .retrieve()
                    .onStatus(
                            HttpStatusCode::is5xxServerError,
                            (providerRequest, providerResponse) -> {
                                throw new TransientProviderException(
                                        "Provider returned " + providerResponse.getStatusCode());
                            })
                    .onStatus(
                            HttpStatusCode::is4xxClientError,
                            (providerRequest, providerResponse) -> {
                                throw new PermanentProviderException(
                                        "Provider rejected request with "
                                                + providerResponse.getStatusCode());
                            })
                    .body(ProviderAuthorizationResponse.class);
            if (response == null) {
                throw new TransientProviderException("Provider returned an empty response");
            }
            return new ProviderAuthorization(
                    response.decision(), response.providerReference());
        } catch (ResourceAccessException exception) {
            throw new TransientProviderException(
                    "Provider request timed out or could not connect", exception);
        }
    }

    private record ProviderAuthorizationPayload(
            UUID paymentId,
            String merchantId,
            BigDecimal amount,
            String currency) {
    }

    private record ProviderAuthorizationResponse(
            ProviderDecision decision,
            String providerReference) {
    }
}
