package com.yasaswimandava.paymentlab.api;

import com.yasaswimandava.paymentlab.application.CreatePaymentCommand;
import com.yasaswimandava.paymentlab.application.CreatePaymentResult;
import com.yasaswimandava.paymentlab.application.PaymentApplicationService;
import jakarta.validation.Valid;
import java.util.Currency;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
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

    private final PaymentApplicationService paymentService;

    public PaymentController(PaymentApplicationService paymentService) {
        this.paymentService = paymentService;
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

