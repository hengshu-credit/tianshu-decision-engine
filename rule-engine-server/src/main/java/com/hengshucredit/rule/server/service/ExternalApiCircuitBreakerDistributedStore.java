package com.hengshucredit.rule.server.service;

/** Atomic cross node circuit-breaker state transitions. */
public interface ExternalApiCircuitBreakerDistributedStore {

    AcquireResult acquire(Long apiConfigId, int failureRate, int minCalls,
                          int windowSize, int openSeconds, int halfOpenCalls);

    void record(Long apiConfigId, boolean halfOpen, boolean success, int failureRate,
                int minCalls, int windowSize, int openSeconds, int halfOpenCalls);

    enum AcquireResult {
        CLOSED,
        HALF_OPEN,
        OPEN
    }
}
