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
