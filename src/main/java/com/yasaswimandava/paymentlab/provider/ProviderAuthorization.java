package com.yasaswimandava.paymentlab.provider;

import java.util.Objects;

public record ProviderAuthorization(ProviderDecision decision, String providerReference) {

    public ProviderAuthorization {
        Objects.requireNonNull(decision, "decision must not be null");
        if (providerReference == null || providerReference.isBlank()) {
            throw new IllegalArgumentException("providerReference must not be blank");
        }
    }

    public static ProviderAuthorization approved(String providerReference) {
        return new ProviderAuthorization(ProviderDecision.APPROVED, providerReference);
    }

    public static ProviderAuthorization declined(String providerReference) {
        return new ProviderAuthorization(ProviderDecision.DECLINED, providerReference);
    }
}
