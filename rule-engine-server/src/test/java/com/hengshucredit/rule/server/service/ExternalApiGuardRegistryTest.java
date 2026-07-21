package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ExternalApiGuardRegistryTest {

    @Test
    public void rejectsRequestsAboveConfiguredBurst() {
        ExternalApiGuardRegistry registry = new ExternalApiGuardRegistry(properties());
        RuleExternalApiConfig config = config();
        config.setQpsLimit(BigDecimal.ONE);
        config.setBurstCapacity(1);

        registry.acquire(config).close();
        try {
            registry.acquire(config);
            fail("expected rate limit rejection");
        } catch (ExternalApiGuardRegistry.RejectedException e) {
            assertEquals("API_RATE_LIMITED", e.getCode());
        }
    }

    @Test
    public void releasesConcurrentPermitAfterRequestCompletes() {
        ExternalApiGuardRegistry registry = new ExternalApiGuardRegistry(properties());
        RuleExternalApiConfig config = config();
        config.setMaxConcurrent(1);
        ExternalApiGuardRegistry.Permit first = registry.acquire(config);
        try {
            registry.acquire(config);
            fail("expected concurrency rejection");
        } catch (ExternalApiGuardRegistry.RejectedException e) {
            assertEquals("API_MAX_CONCURRENT", e.getCode());
        }
        first.close();
        registry.acquire(config).close();
    }

    @Test
    public void rejectsNewGuardWhenBoundedRegistryContainsOnlyActiveEntries() {
        ExternalCallProperties properties = properties();
        properties.setApiGuardRegistryMaxEntries(1);
        ExternalApiGuardRegistry registry = new ExternalApiGuardRegistry(properties);
        ExternalApiGuardRegistry.Permit active = registry.acquire(config());
        try {
            RuleExternalApiConfig another = config();
            another.setId(2L);
            registry.acquire(another);
            fail("expected bounded registry rejection");
        } catch (ExternalApiGuardRegistry.RejectedException e) {
            assertEquals("API_GUARD_REGISTRY_FULL", e.getCode());
        } finally {
            active.close();
        }
        assertEquals(1, registry.size());
    }

    private RuleExternalApiConfig config() {
        RuleExternalApiConfig config = new RuleExternalApiConfig();
        config.setId(1L);
        config.setMaxConcurrent(50);
        config.setConcurrentWaitTimeoutMs(0);
        return config;
    }

    private ExternalCallProperties properties() {
        ExternalCallProperties properties = new ExternalCallProperties();
        properties.setApiGuardRegistryMaxEntries(8);
        return properties;
    }
}
