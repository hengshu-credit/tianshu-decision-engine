package com.hengshucredit.rule.server.service;

import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuleReferenceIntegrityServiceTest {

    @Test
    public void batchScanLoadsContentsOnceAndReusesProjectReferenceCatalog() {
        RuleReferenceIntegrityService service = new RuleReferenceIntegrityService();
        java.util.concurrent.atomic.AtomicInteger catalogLoads = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger contentLoads = new java.util.concurrent.atomic.AtomicInteger();
        ReflectionTestUtils.setField(service, "variableService", new RuleVariableService() {
            public Map<String, String> buildRefScriptNameMap(Long projectId) {
                catalogLoads.incrementAndGet();
                return Map.of("VARIABLE:9", "age");
            }
        });
        var definitions = new java.util.ArrayList<com.hengshucredit.rule.model.entity.RuleDefinition>();
        var contents = new java.util.ArrayList<com.hengshucredit.rule.model.entity.RuleDefinitionContent>();
        for (long id = 1; id <= 3; id++) {
            var definition = new com.hengshucredit.rule.model.entity.RuleDefinition();
            definition.setId(id);
            definition.setProjectId(1L);
            definitions.add(definition);
            var content = new com.hengshucredit.rule.model.entity.RuleDefinitionContent();
            content.setDefinitionId(id);
            content.setModelJson("{\"field\":{\"varCode\":\"renamed\",\"_varId\":9,\"_refType\":\"VARIABLE\"}}");
            contents.add(content);
        }
        var mapperType = com.hengshucredit.rule.server.mapper.RuleDefinitionContentMapper.class;
        Object mapper = java.lang.reflect.Proxy.newProxyInstance(mapperType.getClassLoader(), new Class<?>[]{mapperType},
                (proxy, method, args) -> { contentLoads.incrementAndGet(); return contents; });
        ReflectionTestUtils.setField(service, "contentMapper", mapper);
        var reports = service.scanDefinitions(definitions);
        assertEquals(3, reports.size());
        assertTrue(reports.stream().allMatch(RuleReferenceIntegrityService.AuditReport::isValid));
        assertEquals(1, catalogLoads.get());
        assertEquals(1, contentLoads.get());
    }

    @Test
    public void auditUsesOnlyIdAndRefTypeAndReportsExactLocation() {
        RuleReferenceIntegrityService service = service();
        String json = "{\"rules\":["
                + "{\"condition\":{\"varCode\":\"renamedAge\",\"_varId\":9,\"_refType\":\"VARIABLE\"}},"
                + "{\"condition\":{\"varCode\":\"age\",\"varLabel\":\"年龄\"}},"
                + "{\"condition\":{\"varCode\":\"score\",\"_varId\":999,\"_refType\":\"VARIABLE\"}}]}";

        RuleReferenceIntegrityService.AuditReport report = service.audit(7L, 1L, json);

        assertFalse(report.isValid());
        assertEquals(2, report.getIssueCount());
        assertEquals("$.rules[1].condition", report.getIssues().get(0).getPath());
        assertEquals("MISSING_CONTRACT", report.getIssues().get(0).getReason());
        assertEquals("$.rules[2].condition", report.getIssues().get(1).getPath());
        assertEquals("DANGLING_REFERENCE", report.getIssues().get(1).getReason());
    }

    @Test
    public void parentAnchoredManualPathIsValidWithoutRegisteredLeaf() {
        RuleReferenceIntegrityService service = service();
        String json = "{\"kind\":\"PATH\",\"value\":\"request.items[0].name\"," 
                + "\"code\":\"request.items[0].name\",\"refId\":41,\"refType\":\"DATA_OBJECT\"," 
                + "\"anchorPath\":\"request.items\",\"relativePath\":\"[0].name\",\"resolved\":true}";

        assertTrue(service.audit(7L, 1L, json).isValid());
    }

    @Test
    public void auditIgnoresGraphEdgeTargetNodeReference() {
        RuleReferenceIntegrityService service = service();
        String json = "{\"nodes\":[{\"id\":\"n1\",\"actionData\":["
                + "{\"type\":\"assign\",\"target\":\"currentAge\","
                + "\"_targetVarId\":9,\"_targetRefType\":\"VARIABLE\"}]}],"
                + "\"edges\":[{\"id\":\"e1\",\"source\":\"n1\",\"target\":\"n2\"}]}";

        assertTrue(service.audit(7L, 1L, json).isValid());
    }

    @Test
    public void auditStillRequiresVariableContractForActionTarget() {
        RuleReferenceIntegrityService service = service();
        String json = "{\"nodes\":[{\"id\":\"n1\",\"actionData\":["
                + "{\"type\":\"assign\",\"target\":\"currentAge\"}]}],"
                + "\"edges\":[{\"id\":\"e1\",\"source\":\"n1\",\"target\":\"n2\"}]}";

        RuleReferenceIntegrityService.AuditReport report = service.audit(7L, 1L, json);

        assertFalse(report.isValid());
        assertEquals(1, report.getIssueCount());
        assertEquals("$.nodes[0].actionData[0]", report.getIssues().get(0).getPath());
        assertEquals("MISSING_CONTRACT", report.getIssues().get(0).getReason());
    }

    private RuleReferenceIntegrityService service() {
        RuleReferenceIntegrityService service = new RuleReferenceIntegrityService();
        ReflectionTestUtils.setField(service, "variableService", new RuleVariableService() {
            @Override
            public Map<String, String> buildRefScriptNameMap(Long projectId) {
                Map<String, String> refs = new LinkedHashMap<>();
                refs.put("VARIABLE:9", "current_age");
                refs.put("DATA_OBJECT:41", "request.items");
                return refs;
            }

            @Override
            public Map<Long, String> buildRefConstantExpressionMap(Long projectId) {
                return Collections.emptyMap();
            }
        });
        return service;
    }
}
