package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleTraceRegistry;
import org.junit.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RuleTraceRegistryServiceTest {

    @Test
    public void retriesWhenDatabasePrimaryKeyCollides() {
        RecordingRegistryService service = new RecordingRegistryService(true);

        String traceId = service.allocate("RS", "P", "0001", 1L,
                "RULE", 10L, "RISK_RULE", null);

        assertEquals(2, service.attempts);
        assertEquals(36, traceId.length());
        assertTrue(traceId.startsWith("RSP0001"));
        assertEquals(traceId, service.saved.getTraceId());
    }

    @Test
    public void experimentChildDoesNotRequireParentTrace() {
        RecordingRegistryService service = new RecordingRegistryService(false);

        service.allocate("TB", "P", "0001", 1L,
                "RULE", 11L, "GROUP_RULE", null);

        assertNull(service.saved.getParentTraceId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsClientTraceAlreadyRegisteredInDatabase() {
        RecordingRegistryService service = new RecordingRegistryService(true);
        RuleTraceRegistry registry = new RuleTraceRegistry();
        registry.setTraceId(com.hengshucredit.rule.core.trace.TraceIdGenerator.generate(
                "QL", "P", "0001"));

        service.registerExisting(registry);
    }

    private static class RecordingRegistryService extends RuleTraceRegistryService {
        private final boolean collideFirst;
        private int attempts;
        private RuleTraceRegistry saved;

        private RecordingRegistryService(boolean collideFirst) {
            this.collideFirst = collideFirst;
        }

        @Override
        protected void insertRegistry(RuleTraceRegistry registry) {
            attempts++;
            if (collideFirst && attempts == 1) {
                throw new DuplicateKeyException("duplicate trace id");
            }
            saved = registry;
        }
    }
}
