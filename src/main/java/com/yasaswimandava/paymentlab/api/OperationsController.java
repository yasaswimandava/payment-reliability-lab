package com.yasaswimandava.paymentlab.api;

import com.yasaswimandava.paymentlab.port.OperationsRepository;
import com.yasaswimandava.paymentlab.provider.ResilientPaymentProvider;
import java.time.Clock;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations")
public class OperationsController {

    private final OperationsRepository operationsRepository;
    private final ResilientPaymentProvider paymentProvider;
    private final Clock clock;

    public OperationsController(
            OperationsRepository operationsRepository,
            ResilientPaymentProvider paymentProvider,
            Clock clock) {
        this.operationsRepository = operationsRepository;
        this.paymentProvider = paymentProvider;
        this.clock = clock;
    }

    @GetMapping("/overview")
    public OperationsOverviewResponse overview() {
        return OperationsOverviewResponse.from(
                operationsRepository.snapshot(),
                paymentProvider.circuitState(),
                clock.instant());
    }
}
