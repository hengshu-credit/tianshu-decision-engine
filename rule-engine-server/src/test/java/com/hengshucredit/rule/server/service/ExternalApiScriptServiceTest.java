package com.hengshucredit.rule.server.service;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ExternalApiScriptServiceTest {

    private final ExternalApiScriptService service = new ExternalApiScriptService();

    @Test
    @SuppressWarnings("unchecked")
    public void requestScriptCanMutateBodyHeadersAndQuery() {
        Map<String, Object> context = context();
        String script = "apiPut(body, \"sign\", apiMd5(mapGet(vars, \"appSecret\") + mapGet(body, \"mobile\")));"
                + "apiPut(headers, \"TIMESTAMP\", toStringValue(nowMillis));"
                + "apiPut(query, \"requestId\", requestId); body";

        Object result = service.executeRequest(script, context);

        Map<String, Object> body = (Map<String, Object>) result;
        assertEquals("c888ff3678a603e85898f9dae73a2782", body.get("sign"));
        assertEquals("1700000000000", ((Map<?, ?>) context.get("headers")).get("TIMESTAMP"));
        assertEquals("req-001", ((Map<?, ?>) context.get("query")).get("requestId"));
    }

    @Test
    public void nonNullScriptResultReplacesCurrentBody() {
        Map<String, Object> context = context();

        Object result = service.executeResponse("_result = newMap(); _result = mapPut(_result, \"score\", 720); _result", context);

        assertEquals(720, ((Number) ((Map<?, ?>) result).get("score")).intValue());
    }

    @Test
    public void scriptFailureDoesNotLeakConfiguredSecret() {
        Map<String, Object> context = context();
        String secret = String.valueOf(((Map<?, ?>) context.get("vars")).get("appSecret"));
        try {
            service.executeRequest("apiTripleDesEncryptBase64(\"payload\", mapGet(vars, \"appSecret\"))", context);
        } catch (IllegalArgumentException e) {
            assertFalse(e.getMessage().contains(secret));
            return;
        }
        throw new AssertionError("expected request script to fail");
    }

    @Test
    public void scriptVariablesAreParsedWithoutChangingNames() {
        String config = "{\"scriptVariables\":["
                + "{\"name\":\"appKey\",\"value\":\"A001\",\"sensitive\":false},"
                + "{\"name\":\"secret_key\",\"value\":\"S001\",\"sensitive\":true}]}";

        assertEquals(new LinkedHashMap<String, Object>() {{
            put("appKey", "A001");
            put("secret_key", "S001");
        }}, service.parseScriptVariables(config));
    }

    private Map<String, Object> context() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mobile", "13800138000");
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("appSecret", "secret-123");
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("input", new LinkedHashMap<>());
        context.put("body", body);
        context.put("headers", new LinkedHashMap<>());
        context.put("query", new LinkedHashMap<>());
        context.put("vars", vars);
        context.put("token", "");
        context.put("endpoint", "/score");
        context.put("method", "POST");
        context.put("nowMillis", 1700000000000L);
        context.put("requestId", "req-001");
        context.put("allowed", Arrays.asList("body", "headers", "query"));
        return context;
    }
}
