package com.yasaswimandava.paymentlab.adapter.postgres;

import com.yasaswimandava.paymentlab.application.CreatePaymentCommand;
import com.yasaswimandava.paymentlab.application.CreatePaymentResult;
import com.yasaswimandava.paymentlab.application.PaymentOperations;
import com.yasaswimandava.paymentlab.domain.Payment;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class TransactionalPaymentOperations implements PaymentOperations {

    private final PaymentOperations delegate;
    private final TransactionTemplate writeTransaction;
    private final TransactionTemplate readTransaction;

    public TransactionalPaymentOperations(
            PaymentOperations delegate,
            PlatformTransactionManager transactionManager) {
        this.delegate = Objects.requireNonNull(delegate);
        this.writeTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
    }

    @Override
    public CreatePaymentResult create(
            String idempotencyKey,
            CreatePaymentCommand command) {
        try {
            return createInTransaction(idempotencyKey, command);
        } catch (DuplicateKeyException concurrentClaim) {
            return createInTransaction(idempotencyKey, command);
        }
    }

    @Override
    public Payment get(UUID paymentId) {
        return requireResult(readTransaction.execute(status -> delegate.get(paymentId)));
    }

    private <T> T requireResult(T result) {
        return Objects.requireNonNull(result, "transaction returned no result");
    }

    private CreatePaymentResult createInTransaction(
            String idempotencyKey,
            CreatePaymentCommand command) {
        return requireResult(writeTransaction.execute(
                status -> delegate.create(idempotencyKey, command)));
    }
}
