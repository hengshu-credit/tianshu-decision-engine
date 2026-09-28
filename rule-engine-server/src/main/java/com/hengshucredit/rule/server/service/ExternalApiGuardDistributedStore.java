package com.hengshucredit.rule.server.service;

/**
 * Cross node state used by the external API rate/concurrency guard.
 *
 * <p>The registry deliberately keeps a local implementation for unit tests and
 * deployments that do not provide Redis. The Spring implementation is backed
 * by Redis Lua scripts so rate tokens and concurrency leases are decided in one
 * atomic operation.</p>
 */
public interface ExternalApiGuardDistributedStore {

    AcquireResult tryAcquire(Long apiConfigId, double qps, int burstCapacity,
                             int maxConcurrent, long leaseMillis, String permitId);

    void release(Long apiConfigId, String permitId);

    enum AcquireResult {
        ACQUIRED,
        RATE_LIMITED,
        CONCURRENCY_LIMITED
    }
}
