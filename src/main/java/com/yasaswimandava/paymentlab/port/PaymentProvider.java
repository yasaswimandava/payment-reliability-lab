package com.yasaswimandava.paymentlab.port;

import com.yasaswimandava.paymentlab.provider.ProviderAuthorization;
import com.yasaswimandava.paymentlab.provider.ProviderAuthorizationRequest;

@FunctionalInterface
public interface PaymentProvider {

    ProviderAuthorization authorize(ProviderAuthorizationRequest request);
}
