package com.yasaswimandava.paymentlab.port;

import com.yasaswimandava.paymentlab.domain.OperationsSnapshot;

@FunctionalInterface
public interface OperationsRepository {

    OperationsSnapshot snapshot();
}
