package com.yasaswimandava.paymentlab.provider;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProviderSimulatorController.class)
class ProviderSimulatorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesAControllableTransientFailureScenarioAndIdempotentApproval() throws Exception {
        mockMvc.perform(put("/api/v1/simulator/provider")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"TRANSIENT_THEN_SUCCESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("TRANSIENT_THEN_SUCCESS"))
                .andExpect(jsonPath("$.attempts").value(0));

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(providerRequest())
                    .andExpect(status().isServiceUnavailable());
        }

        String firstResponse = mockMvc.perform(providerRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("APPROVED"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String replayResponse = mockMvc.perform(providerRequest())
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.assertj.core.api.Assertions.assertThat(replayResponse).isEqualTo(firstResponse);
        mockMvc.perform(get("/api/v1/simulator/provider"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attempts").value(3))
                .andExpect(jsonPath("$.successfulAuthorizations").value(1));
    }

    @Test
    void canReturnADeterministicBusinessDecline() throws Exception {
        mockMvc.perform(put("/api/v1/simulator/provider")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"DECLINE\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(providerRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DECLINED"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            providerRequest() {
        return post("/simulator/v1/authorizations")
                .header(
                        "Idempotency-Key",
                        "f1391146-e720-4fb8-a5d8-adc209083c59")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "paymentId": "f1391146-e720-4fb8-a5d8-adc209083c59",
                          "merchantId": "merchant-provider-test",
                          "amount": 49.25,
                          "currency": "USD"
                        }
                        """);
    }
}
