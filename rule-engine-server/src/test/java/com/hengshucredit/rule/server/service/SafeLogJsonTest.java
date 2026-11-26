package com.hengshucredit.rule.server.service;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SafeLogJsonTest {
    @Test
    public void outputPreservesExplicitNullsAndSharedObjectsAtEachBusinessPath() throws Exception {
        var box = Map.of("width",90,"height",100);
        var business = new java.util.LinkedHashMap<String,Object>();
        business.put("prepared",box);
        business.put("result",box);
        business.put("missingScore",null);
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(SafeLogJson.output(business));
        assertEquals(90,json.at("/prepared/width").intValue());
        assertEquals(json.get("prepared"),json.get("result"));
        assertTrue(json.get("missingScore").isNull());
    }
    @Test
    public void structuredLogsEncodeStringsButRuntimeBodiesKeepTheirOriginalJson() {
        String raw = "{\"value\":0}";
        assertEquals("\"{\\\"value\\\":0}\"", SafeLogJson.toJsonSafely(raw));
        assertEquals(raw, new RuleRuntimeCallLogService().toJson(raw));
        assertEquals("{\"value\":0}", new RuleRuntimeCallLogService().toJson(Map.of("value", 0)));
        assertNull(SafeLogJson.toJsonSafely(null));
        assertNull(new RuleRuntimeCallLogService().toJson(null));
    }

    @Test
    public void serializationFailureKeepsTheExistingLogFallback() {
        String failure = SafeLogJson.toJsonSafely(new Unserializable());
        assertTrue(failure.startsWith("{\"error\":\"JSON_SERIALIZE_FAILED\",\"message\":"));
        assertEquals(failure, new RuleRuntimeCallLogService().toJson(new Unserializable()));
    }

    public static class Unserializable {
        public String getValue() { throw new IllegalStateException("unavailable"); }
    }
}
