package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

public class ExternalApiRequestPlanTest {
    @Test public void stepAuthenticationFromChainInputAlsoIsolatesTheResponseCache() {
        var api = ExternalApiTestConfig.api();
        api.setId(9L); api.setAuthMode("NONE");
        api.setExecutionConfig(com.hengshucredit.rule.server.artifact.CanonicalJson.write(Map.of("version", 2,
                "requestFields", java.util.List.of(ExternalApiTestConfig.field("token", "JSON", "token", ExternalApiTestConfig.reference(7L, "credential"))),
                "steps", java.util.List.of(Map.of("id", "submit", "type", "HTTP", "authMode", "BEARER", "authApiConfig",
                        JSON.toJSONString(Map.of("token", ExternalApiTestConfig.path("input.__apiFields.token"))))))));
        var paths = Map.of("VARIABLE:7", "credential");
        var a = ExternalApiRequestPlan.prepare(api, Map.of("credential", "tenant-A", "customerId", "C1"), Map.of(), paths);
        var b = ExternalApiRequestPlan.prepare(api, Map.of("credential", "tenant-B", "customerId", "C1"), Map.of(), paths);
        var service = new ExternalApiInvokeService();
        String cache = "{\"components\":[{\"path\":\"customerId\"}]}";
        assertNotEquals(service.buildResponseCacheKey(9L, cache, a.params()), service.buildResponseCacheKey(9L, cache, b.params()));
    }
    @Test public void inlineStepAuthenticationAlsoIsolatesTheResponseCache() {
        var api = ExternalApiTestConfig.api();
        api.setId(9L); api.setAuthMode("NONE");
        var auth = JSON.toJSONString(Map.of("token", ExternalApiTestConfig.reference(7L, "credential")));
        api.setExecutionConfig(com.hengshucredit.rule.server.artifact.CanonicalJson.write(Map.of("version", 2, "requestFields", java.util.List.of(),
                "steps", java.util.List.of(Map.of("id", "submit", "type", "HTTP", "authMode", "BEARER", "authApiConfig", auth)))));
        var paths = Map.of("VARIABLE:7", "credential");
        var a = ExternalApiRequestPlan.prepare(api, Map.of("credential", "tenant-A", "customerId", "C1"), Map.of(), paths);
        var b = ExternalApiRequestPlan.prepare(api, Map.of("credential", "tenant-B", "customerId", "C1"), Map.of(), paths);
        String cache = "{\"components\":[{\"path\":\"customerId\"}]}";
        var service = new ExternalApiInvokeService();
        assertNotEquals(a.identity(), b.identity());
        assertNotEquals(service.buildResponseCacheKey(9L, cache, a.params()), service.buildResponseCacheKey(9L, cache, b.params()));
    }
    @Test public void authenticationSubfieldsOfOneResourceHaveIndependentIdentities() {
        RuleExternalApiConfig api = ExternalApiTestConfig.api();
        api.setId(9L); api.setAuthMode("BASIC");
        var username = Map.of("kind", "PATH", "refType", "VARIABLE", "refId", 7,
                "relativePath", ".username", "value", "credentials.username");
        var password = Map.of("kind", "PATH", "refType", "VARIABLE", "refId", 7,
                "relativePath", ".password", "value", "credentials.password");
        api.setAuthApiConfig(JSON.toJSONString(Map.of("username", username, "password", password)));
        var paths = Map.of("VARIABLE:7", "credentials");
        Map<String, Object> first = Map.of("credentials", Map.of("username", "tenant-A", "password", "same-test-password"), "customerId", "C1");
        Map<String, Object> second = Map.of("credentials", Map.of("username", "tenant-B", "password", "same-test-password"), "customerId", "C1");
        assertEquals("tenant-A", ExternalApiRequestPlan.evaluate(username, first, paths));
        assertEquals("tenant-B", ExternalApiRequestPlan.evaluate(username, second, paths));
        var a = ExternalApiRequestPlan.prepare(api, first, Map.of(), paths);
        var b = ExternalApiRequestPlan.prepare(api, second, Map.of(), paths);
        assertNotEquals(a.identity(), b.identity());
        var service = new ExternalApiInvokeService();
        String cache = "{\"components\":[{\"path\":\"customerId\"}]}";
        assertNotEquals(service.buildResponseCacheKey(9L, cache, a.params()), service.buildResponseCacheKey(9L, cache, b.params()));
        assertEquals(a.identity(), ExternalApiRequestPlan.prepare(api, first, Map.of(), paths).identity());
    }

    @Test public void stepSubfieldsCannotOverwriteEachOtherInRequestIdentity() {
        var api = ExternalApiTestConfig.api();
        var step = Map.of("id", "submit", "type", "HTTP", "requestFields", java.util.List.of(
                ExternalApiTestConfig.field("first", "JSON", "first", Map.of("kind", "PATH", "refType", "VARIABLE", "refId", 7, "relativePath", ".first")),
                ExternalApiTestConfig.field("second", "JSON", "second", Map.of("kind", "PATH", "refType", "VARIABLE", "refId", 7, "relativePath", ".second"))));
        api.setExecutionConfig(com.hengshucredit.rule.server.artifact.CanonicalJson.write(Map.of("version", 2, "requestFields", java.util.List.of(), "steps", java.util.List.of(step))));
        var paths = Map.of("VARIABLE:7", "inputObject");
        var a = ExternalApiRequestPlan.prepare(api, Map.of("inputObject", Map.of("first", "A", "second", "unchanged")), Map.of(), paths);
        var b = ExternalApiRequestPlan.prepare(api, Map.of("inputObject", Map.of("first", "B", "second", "unchanged")), Map.of(), paths);
        assertNotEquals(a.identity(), b.identity());
    }
    @Test public void linkedAuthenticationChangesNeverReuseAnotherCredentialsRequestIdentity() {
        RuleExternalApiConfig api = api();
        Map<String, Object> linked = Map.of("authApiConfigId:9", Map.of("authApiConfig", JSON.toJSONString(
                Map.of("token", ExternalApiTestConfig.reference(2, "oldToken")))));
        var paths = Map.of("VARIABLE:1", "applicant", "VARIABLE:2", "token");
        var first = ExternalApiRequestPlan.prepare(api, Map.of("applicant", "111", "token", "one"), Map.of(), paths, null, linked);
        var second = ExternalApiRequestPlan.prepare(api, Map.of("applicant", "111", "token", "two"), Map.of(), paths, null, linked);
        assertNotEquals(first.identity(), second.identity());
        assertNotEquals(first.params().get("__apiAuthIdentity"), second.params().get("__apiAuthIdentity"));
    }
    @Test public void stepAuthenticationInputsParticipateInIdentityAndRootPathIsSelectable() {
        RuleExternalApiConfig api = api();
        var config = JSON.parseObject(api.getExecutionConfig());
        config.put("steps", java.util.List.of(Map.of("id", "step", "authApiConfig", JSON.toJSONString(Map.of("token", ExternalApiTestConfig.reference(2L, "oldToken"))))));
        api.setExecutionConfig(JSON.toJSONString(config));
        var paths = Map.of("VARIABLE:1", "applicant", "VARIABLE:2", "token");
        assertNotEquals(ExternalApiRequestPlan.prepare(api, Map.of("applicant", "111", "token", "one"), Map.of(), paths).identity(),
                ExternalApiRequestPlan.prepare(api, Map.of("applicant", "111", "token", "two"), Map.of(), paths).identity());
        assertEquals(Map.of("body", 1), ExternalApiRequestPlan.evaluate(ExternalApiTestConfig.path("$"), Map.of("body", 1), Map.of()));
    }
    @Test public void authenticationReferencesParticipateInInvocationIdentity() {
        RuleExternalApiConfig api = api();
        api.setAuthMode("BEARER");
        api.setAuthApiConfig("{\"token\":{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\",\"refId\":2,\"code\":\"oldToken\"}}");
        Map<String, String> paths = Map.of("VARIABLE:1", "applicant", "VARIABLE:2", "tenantToken");
        var one = ExternalApiRequestPlan.prepare(api, Map.of("applicant", "111", "tenantToken", "one"), Map.of(), paths);
        var two = ExternalApiRequestPlan.prepare(api, Map.of("applicant", "111", "tenantToken", "two"), Map.of(), paths);
        assertNotEquals(one.identity(), two.identity());
    }
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

    @Test @SuppressWarnings("unchecked") public void cookieFieldsAreKeptOutOfBodyAndBoundToRequestMapping() {
        RuleExternalApiConfig api = api();
        api.setExecutionConfig("{\"version\":2,\"requestFields\":["
                + "{\"id\":\"session\",\"location\":\"COOKIE\",\"path\":\"session_id\",\"value\":{\"kind\":\"LITERAL\",\"value\":\"s-1\"}},"
                + "{\"id\":\"query\",\"location\":\"QUERY\",\"path\":\"q\",\"value\":{\"kind\":\"LITERAL\",\"value\":\"risk\"}}]}" );
        var plan = ExternalApiRequestPlan.prepare(api, Map.of(), Map.of(), Map.of());
        Map<String, Object> mapping = JSON.parseObject(plan.config().getRequestMapping());
        Map<String, Object> cookies = (Map<String, Object>) mapping.get("_cookies");
        Map<String, Object> query = JSON.parseObject(plan.config().getQueryConfig());
        assertEquals("$.__apiFields.session", cookies.get("session_id"));
        assertEquals("$.__apiFields.query", query.get("q"));
    }
}
