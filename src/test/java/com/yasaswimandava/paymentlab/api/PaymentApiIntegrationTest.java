package com.yasaswimandava.paymentlab.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentApiIntegrationTest {

    private static final String PAYMENTS_URL = "/api/v1/payments";
    private static final String MERCHANT_ID = "merchant-api-test";
    private static final String IDEMPOTENCY_KEY = "order-api-123";

    @Autowired
    private MockMvc mockMvc;

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

    private String paymentJson(String amount) {
        return """
                {
                  "amount": %s,
                  "currency": "USD"
                }
                """.formatted(amount);
    }
}
