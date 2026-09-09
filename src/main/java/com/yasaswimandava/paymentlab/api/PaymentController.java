package com.yasaswimandava.paymentlab.api;

import com.yasaswimandava.paymentlab.application.CreatePaymentCommand;
import com.yasaswimandava.paymentlab.application.CreatePaymentResult;
import com.yasaswimandava.paymentlab.application.PaymentOperations;
import jakarta.validation.Valid;
import java.util.Currency;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private static final String REPLAY_HEADER = "Idempotency-Replayed";

    private final PaymentOperations paymentService;

    public PaymentController(PaymentOperations paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/{paymentId}")
    PaymentResponse get(@PathVariable UUID paymentId) {
        return PaymentResponse.from(paymentService.get(paymentId));
    }

    @PostMapping
    ResponseEntity<PaymentResponse> create(
            @RequestHeader("X-Merchant-Id") String merchantId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {
        CreatePaymentCommand command = new CreatePaymentCommand(
                merchantId,
                request.amount(),
                Currency.getInstance(request.currency()));
        CreatePaymentResult result = paymentService.create(idempotencyKey, command);
        HttpStatus responseStatus = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;

        return ResponseEntity.status(responseStatus)
                .header(REPLAY_HEADER, Boolean.toString(result.replayed()))
                .body(PaymentResponse.from(result));
    }
}
