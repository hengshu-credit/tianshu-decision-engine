package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleTraceRegistry;
import org.junit.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

public class RuleTraceRegistryServiceTest {

    @Test
    public void clientReplayAllowsSameStableResourceAfterRenameButRejectsChangedIdentity() {
        RuleTraceRegistry original = registry();
        RuleTraceRegistryService service = new RuleTraceRegistryService() {
            @Override protected void insertRegistry(RuleTraceRegistry row) {
                throw new DuplicateKeyException("duplicate");
            }
            @Override protected RuleTraceRegistry findRegisteredTrace(String id) { return original; }
        };
        RuleTraceRegistry replay = registry();
        replay.setTraceId(original.getTraceId());
        replay.setResourceCode("RENAMED_RULE");
        assertFalse(service.registerClientTrace(replay));

        for (String field : java.util.List.of("projectId", "scopeType", "scopeCode", "traceType",
                "resourceType", "resourceId", "parentTraceId")) {
            RuleTraceRegistry conflict = registry();
            conflict.setTraceId(original.getTraceId());
            org.springframework.test.util.ReflectionTestUtils.setField(conflict, field,
                    field.endsWith("Id") && !field.equals("parentTraceId") ? 999L : "different");
            assertEquals(field, 409, assertThrows(ClientLogValidationException.class,
                    () -> service.registerClientTrace(conflict)).getStatusCode());
        }
    }

    private static RuleTraceRegistry registry() {
        RuleTraceRegistry row = new RuleTraceRegistry();
        row.setTraceId(com.hengshucredit.rule.core.trace.TraceIdGenerator.generate("QL", "P", "0001"));
        row.setTraceType("QL");
        row.setScopeType("P");
        row.setScopeCode("0001");
        row.setProjectId(1L);
        row.setResourceType("RULE");
        row.setResourceId(11L);
        row.setResourceCode("RULE");
        return row;
    }

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
