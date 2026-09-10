package com.yasaswimandava.paymentlab.provider;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ProviderSimulatorController {

    private final AtomicReference<ProviderSimulatorMode> mode =
            new AtomicReference<>(ProviderSimulatorMode.HEALTHY);
    private final AtomicInteger attempts = new AtomicInteger();
    private final AtomicInteger successfulAuthorizations = new AtomicInteger();
    private final Map<UUID, AtomicInteger> attemptsByPayment = new ConcurrentHashMap<>();
    private final Map<UUID, ProviderAuthorization> completed = new ConcurrentHashMap<>();

    @GetMapping("/api/v1/simulator/provider")
    public ProviderSimulatorStatus status() {
        return snapshot();
    }

    @PutMapping("/api/v1/simulator/provider")
    public ProviderSimulatorStatus configure(
            @Valid @RequestBody ProviderSimulatorConfiguration configuration) {
        mode.set(configuration.mode());
        attempts.set(0);
        successfulAuthorizations.set(0);
        attemptsByPayment.clear();
        completed.clear();
        return snapshot();
    }

    @PostMapping("/simulator/v1/authorizations")
    public ProviderAuthorization authorize(
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody SimulatorAuthorizationRequest request) {
        if (!request.paymentId().equals(idempotencyKey)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must match paymentId");
        }
        ProviderAuthorization replay = completed.get(request.paymentId());
        if (replay != null) {
            return replay;
        }

        attempts.incrementAndGet();
        int paymentAttempt = attemptsByPayment
                .computeIfAbsent(request.paymentId(), ignored -> new AtomicInteger())
                .incrementAndGet();
        return switch (mode.get()) {
            case HEALTHY -> complete(request.paymentId(), ProviderDecision.APPROVED);
            case DECLINE -> complete(request.paymentId(), ProviderDecision.DECLINED);
            case TRANSIENT_THEN_SUCCESS -> {
                if (paymentAttempt <= 2) {
                    throw new ResponseStatusException(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "Synthetic transient provider failure");
                }
                yield complete(request.paymentId(), ProviderDecision.APPROVED);
            }
            case UNAVAILABLE -> throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Synthetic provider outage");
            case TIMEOUT -> {
                pauseBeyondClientTimeout();
                yield complete(request.paymentId(), ProviderDecision.APPROVED);
            }
        };
    }

    private ProviderAuthorization complete(UUID paymentId, ProviderDecision decision) {
        ProviderAuthorization authorization = new ProviderAuthorization(
                decision, "sim-" + paymentId.toString().substring(0, 8));
        ProviderAuthorization existing = completed.putIfAbsent(paymentId, authorization);
        if (existing != null) {
            return existing;
        }
        if (decision == ProviderDecision.APPROVED) {
            successfulAuthorizations.incrementAndGet();
        }
        return authorization;
    }

    private void pauseBeyondClientTimeout() {
        try {
            Thread.sleep(2_000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Synthetic timeout interrupted",
                    exception);
        }
    }

    private ProviderSimulatorStatus snapshot() {
        return new ProviderSimulatorStatus(
                mode.get(), attempts.get(), successfulAuthorizations.get());
    }

    public enum ProviderSimulatorMode {
        HEALTHY,
        TRANSIENT_THEN_SUCCESS,
        DECLINE,
        UNAVAILABLE,
        TIMEOUT
    }

    public record ProviderSimulatorConfiguration(
            @NotNull ProviderSimulatorMode mode) {
    }

    public record ProviderSimulatorStatus(
            ProviderSimulatorMode mode,
            int attempts,
            int successfulAuthorizations) {
    }

    public record SimulatorAuthorizationRequest(
            @NotNull UUID paymentId,
            @NotBlank String merchantId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency) {
    }
}
