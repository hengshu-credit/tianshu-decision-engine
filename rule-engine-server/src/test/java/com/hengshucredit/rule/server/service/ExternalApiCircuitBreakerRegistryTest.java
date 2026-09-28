package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ExternalApiCircuitBreakerRegistryTest {

    @Test
    public void opensAfterFailureThresholdAndClosesAfterSuccessfulHalfOpenProbe() {
        AtomicLong clock = new AtomicLong(1000L);
        ExternalApiCircuitBreakerRegistry registry =
                new ExternalApiCircuitBreakerRegistry(properties(), clock::get);
        RuleExternalApiConfig config = config();

        registry.acquire(config).failure();
        registry.acquire(config).failure();
        try {
            registry.acquire(config);
            fail("expected open circuit");
        } catch (ExternalApiCircuitBreakerRegistry.OpenException e) {
            assertEquals("OPEN", e.getState());
        }

        clock.addAndGet(1100L);
        registry.acquire(config).success();
        registry.acquire(config).success();
    }

    @Test
    public void usesDistributedStateWhenStoreIsConfigured() {
        RecordingStore store = new RecordingStore();
        ExternalApiCircuitBreakerRegistry registry = new ExternalApiCircuitBreakerRegistry(
                properties(), System::currentTimeMillis, store);
        ExternalApiCircuitBreakerRegistry.CircuitPermit permit = registry.acquire(config());
        assertEquals("HALF_OPEN", permit.getState());
        permit.success();
        assertEquals(1, store.acquireCalls);
        assertEquals(1, store.recordCalls);
        assertTrue(store.lastSuccess);
    }

    private RuleExternalApiConfig config() {
        RuleExternalApiConfig config = new RuleExternalApiConfig();
        config.setId(1L);
        config.setCircuitBreakerEnabled(1);
        config.setCircuitFailureRate(50);
        config.setCircuitMinCalls(2);
        config.setCircuitWindowSize(2);
        config.setCircuitOpenSeconds(1);
        config.setCircuitHalfOpenCalls(1);
        return config;
    }

    private ExternalCallProperties properties() {
        ExternalCallProperties properties = new ExternalCallProperties();
        properties.setApiGuardRegistryMaxEntries(8);
        return properties;
    }

    private static final class RecordingStore implements ExternalApiCircuitBreakerDistributedStore {
        private int acquireCalls;
        private int recordCalls;
        private boolean lastSuccess;

        @Override
        public AcquireResult acquire(Long apiConfigId, int failureRate, int minCalls,
                                     int windowSize, int openSeconds, int halfOpenCalls) {
            acquireCalls++;
            return AcquireResult.HALF_OPEN;
        }

        @Override
        public void record(Long apiConfigId, boolean halfOpen, boolean success,
                           int failureRate, int minCalls, int windowSize,
                           int openSeconds, int halfOpenCalls) {
            recordCalls++;
            lastSuccess = success;
        }
    }
}
