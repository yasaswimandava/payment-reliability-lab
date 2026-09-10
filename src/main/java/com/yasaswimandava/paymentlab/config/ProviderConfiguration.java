package com.yasaswimandava.paymentlab.config;

import com.yasaswimandava.paymentlab.application.PaymentAuthorizationService;
import com.yasaswimandava.paymentlab.port.PaymentProvider;
import com.yasaswimandava.paymentlab.port.PaymentRepository;
import com.yasaswimandava.paymentlab.provider.HttpPaymentProviderClient;
import com.yasaswimandava.paymentlab.provider.ResilientPaymentProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(PaymentProviderProperties.class)
public class ProviderConfiguration {

    @Bean("rawPaymentProvider")
    PaymentProvider rawPaymentProvider(PaymentProviderProperties properties) {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return new HttpPaymentProviderClient(
                RestClient.builder().requestFactory(requestFactory),
                properties.baseUrl());
    }

    @Bean
    @Primary
    ResilientPaymentProvider resilientPaymentProvider(
            @Qualifier("rawPaymentProvider") PaymentProvider delegate,
            PaymentProviderProperties properties) {
        return new ResilientPaymentProvider(
                delegate,
                properties.maxAttempts(),
                properties.retryWait(),
                properties.failureRateThreshold(),
                properties.minimumCalls(),
                properties.slidingWindowSize(),
                properties.openStateWait());
    }

    @Bean
    PaymentAuthorizationService paymentAuthorizationService(
            PaymentRepository paymentRepository,
            ResilientPaymentProvider paymentProvider) {
        return new PaymentAuthorizationService(paymentRepository, paymentProvider);
    }
}
