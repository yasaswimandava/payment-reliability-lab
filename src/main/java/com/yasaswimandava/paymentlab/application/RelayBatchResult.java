package com.yasaswimandava.paymentlab.application;

public record RelayBatchResult(int claimed, int published, int failed) {
}
