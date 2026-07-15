package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.core.engine.RuntimeContextBridge;
import com.hengshucredit.rule.core.trace.TraceIdGenerator;
import com.hengshucredit.rule.model.entity.RuleProject;
import org.junit.After;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RuntimeTraceServiceTest {

    @After
    public void clear() {
        RuntimeContextBridge.clear();
    }

    @Test
    public void moduleTraceUsesCurrentRuleTraceAndAddsTraceEvent() {
        RuntimeTraceService service = new RuntimeTraceService();
        RecordingRegistryService registry = new RecordingRegistryService();
        ReflectionTestUtils.setField(service, "traceRegistryService", registry);
        ReflectionTestUtils.setField(service, "projectService", new ProjectService());

        String ruleTraceId = TraceIdGenerator.generate("RS", "P", "0001");
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("traceId", ruleTraceId);
        RuntimeContextBridge.setRuleContext(rule, Collections.<String>emptyList());
        List<Map<String, Object>> events = new ArrayList<>();
        RuntimeContextBridge.bindTraceEventListener(events::add);

        RuntimeTraceService.ModuleTrace trace = service.startModule(
                "DATABASE", 1L, 8L, "CREDIT_DB");
        service.completeModule(trace, true, null, 12L);

        assertTrue(trace.getTraceId().startsWith("DBP0001"));
        assertEquals(ruleTraceId, trace.getRuleTraceId());
        assertEquals(ruleTraceId, registry.parentTraceId);
        assertEquals(1, events.size());
        assertEquals("MODULE_CALL", events.get(0).get("type"));
        assertEquals("SUCCESS", events.get(0).get("status"));
        assertEquals(12L, events.get(0).get("durationMs"));
    }

    private static class RecordingRegistryService extends RuleTraceRegistryService {
        private String parentTraceId;

        @Override
        public String allocate(String typeCode, String scopeType, String scopeCode,
                               Long projectId, String resourceType, Long resourceId,
                               String resourceCode, String parentTraceId) {
            this.parentTraceId = parentTraceId;
            return TraceIdGenerator.generate(typeCode, scopeType, scopeCode);
        }
    }

    private static class ProjectService extends RuleProjectService {
        @Override
        public RuleProject getById(Serializable id) {
            RuleProject project = new RuleProject();
            project.setId(1L);
            project.setTraceScopeCode("0001");
            return project;
        }
    }
}
