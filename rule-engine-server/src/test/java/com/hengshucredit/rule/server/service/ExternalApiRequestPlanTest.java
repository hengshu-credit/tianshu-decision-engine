package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

public class ExternalApiRequestPlanTest {
    @Test public void quotedFieldNamesPreserveProviderNamesAndPreviewRejectsNetworkFunctions() {
        Map<String, Object> output = new java.util.LinkedHashMap<>();
        ExternalApiRequestPlan.write(output, "customer[\"Mobile.Number\"]", "111");
        assertEquals("111", ExternalApiRequestPlan.read(output, "customer[\"Mobile.Number\"]"));
        assertEquals(200, ExternalApiRequestPlan.evaluate(Map.of("kind", "PATH", "value", "authentication.0.response.httpStatus"),
                Map.of("authentication", java.util.List.of(Map.of("response", Map.of("httpStatus", 200)))), Map.of()));
        assertEquals("111", ExternalApiRequestPlan.evaluate(Map.of("kind", "PATH", "value", "response.body.customer[\"Mobile.Number\"]"),
                Map.of("response", Map.of("body", output)), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> ExternalApiRequestPlan.evaluate(
                Map.of("kind", "FUNCTION", "functionCode", "imageToBase64", "args", java.util.List.of()), Map.of(), Map.of()));
    }
    private RuleExternalApiConfig api() {
        RuleExternalApiConfig api = new RuleExternalApiConfig();
        api.setId(7L);
        api.setRequestMethod("POST");
        api.setExecutionConfig("{\"version\":2,\"requestFields\":[{\"id\":\"phone\",\"location\":\"JSON\",\"path\":\"mobile\",\"required\":true,\"overridable\":true,\"value\":{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\",\"refId\":1,\"value\":\"oldName\"}}]}");
        return api;
    }

    @Test public void stableReferenceAndOverrideProduceDifferentRequestIdentities() {
        var first = ExternalApiRequestPlan.prepare(api(), Map.of("applicant", "111", "contact", "222"),
                Map.of(), Map.of("VARIABLE:1", "applicant", "VARIABLE:2", "contact"));
        var second = ExternalApiRequestPlan.prepare(api(), Map.of("applicant", "111", "contact", "222"),
                JSON.parseObject("{\"phone\":{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\",\"refId\":2,\"value\":\"oldContact\"}}"),
                Map.of("VARIABLE:1", "applicant", "VARIABLE:2", "contact"));
        assertEquals("111", first.values().get("phone"));
        assertEquals("222", second.values().get("phone"));
        assertNotEquals(first.identity(), second.identity());
        var repeated = ExternalApiRequestPlan.prepare(api(), Map.of("applicant", "111", "unrelatedOutput", 999),
                Map.of(), Map.of("VARIABLE:1", "applicant"));
        assertEquals(first.identity(), repeated.identity());
    }

    @Test public void requiredAndUnresolvableReferencesFailBeforeExecution() {
        assertThrows(IllegalArgumentException.class, () -> ExternalApiRequestPlan.prepare(api(), Map.of(),
                Map.of(), Map.of("VARIABLE:1", "applicant")));
        assertThrows(IllegalArgumentException.class, () -> ExternalApiRequestPlan.prepare(api(), Map.of("oldName", "111"),
                Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> ExternalApiRequestPlan.prepare(api(), Map.of("applicant", "111"),
                Map.of("unknown", Map.of("kind", "LITERAL", "value", "222")), Map.of("VARIABLE:1", "applicant")));
    }

    @Test public void immutableParametersRejectOverridesAndExplicitNullPolicyIsPreserved() {
        RuleExternalApiConfig api = api();
        api.setExecutionConfig(api.getExecutionConfig().replace("\"overridable\":true", "\"overridable\":false"));
        assertThrows(IllegalArgumentException.class, () -> ExternalApiRequestPlan.prepare(api, Map.of("applicant", "111"),
                Map.of("phone", Map.of("kind", "LITERAL", "value", "222")), Map.of("VARIABLE:1", "applicant")));
        api.setExecutionConfig("{\"version\":2,\"requestFields\":[{\"id\":\"optional\",\"location\":\"JSON\",\"path\":\"optional\",\"nullPolicy\":\"NULL\",\"value\":{\"kind\":\"LITERAL\",\"valueType\":\"NULL\",\"value\":null}}]}");
        var plan = ExternalApiRequestPlan.prepare(api, Map.of(), Map.of(), Map.of());
        assertTrue(plan.values().containsKey("optional"));
        assertNull(plan.values().get("optional"));
    }
}
