package com.hengshucredit.rule.core.compiler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.core.engine.RuntimeContextBridge;
import org.junit.After;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class RuleSideEffectActionTest {
    @After public void clear() { RuntimeContextBridge.clear(); }

    @Test public void externalCallAssignsCompleteResultAndOnlyRunsOnHit() {
        var calls = new AtomicInteger();
        var envelope = Map.of("request", Map.of("method", "POST"), "response", Map.of("httpStatus", 200),
                "body", Map.of("score", 91), "status", Map.of("billed", true));
        try (var scope = RuntimeContextBridge.bindExternalValueResolver((id, path) -> {
            assertEquals(Long.valueOf(12), id);
            assertEquals("$", path);
            calls.incrementAndGet();
            return envelope;
        })) {
            var actions = JSON.parseArray("[{\"type\":\"if-block\",\"branches\":[{\"type\":\"if\",\"condVar\":\"hit\",\"condOp\":\"==\",\"condValue\":\"true\",\"actions\":[{\"type\":\"api-call\",\"apiId\":12,\"targetOperand\":{\"kind\":\"PATH\",\"value\":\"apiResult\"}}]}]}]");
            var engine = new QLExpressEngine();
            var params = new LinkedHashMap<String, Object>();
            params.put("hit", false);
            var skipped = engine.execute(ActionDataCompiler.compile(actions), params);
            assertTrue(skipped.getErrorMessage(), skipped.isSuccess());
            assertEquals(0, calls.get());
            params.put("hit", true);
            var result = engine.execute(ActionDataCompiler.compile(actions) + "\napiResult", params);
            assertTrue(result.getErrorMessage(), result.isSuccess());
            assertEquals(envelope, result.getResult());
            assertEquals(1, calls.get());
        }
    }

    @Test public void listWriteCompilesTypedValuesAndRejectsIncompleteConfiguration() {
        JSONObject block = JSON.parseObject("{\"type\":\"list-write\",\"listIds\":[8,9],\"operation\":\"MERGE\",\"expiryMode\":\"DURATION\",\"duration\":30,\"durationUnit\":\"DAYS\",\"entries\":[{\"itemType\":\"MOBILE\",\"valueOperand\":{\"kind\":\"PATH\",\"value\":\"mobile\"}}]}");
        JSONArray actions = new JSONArray(); actions.add(block);
        String script = ActionDataCompiler.compile(actions);
        assertTrue(script, script.contains("writeRuleList("));
        assertTrue(script, script.contains("[mobile]"));
        block.put("listIds", new JSONArray());
        assertThrows(IllegalArgumentException.class, () -> ActionDataCompiler.compile(actions));
    }

    @Test public void externalCallRequiresStableApiIdentity() {
        assertThrows(IllegalArgumentException.class, () -> ActionDataCompiler.compile(JSON.parseArray("[{\"type\":\"api-call\",\"apiCode\":\"credit\"}]")));
    }

    @Test public void externalFieldsUseResultPathWithoutDisplayPrefix() {
        var field = JSON.parseObject("{\"kind\":\"REFERENCE\",\"refType\":\"EXTERNAL_API\",\"refId\":12,\"code\":\"credit.response.body\",\"relativePath\":\"response.body\"}");
        assertEquals("externalApiValue(12, \"response.body\")", OperandCompiler.compile(field, null));
        field.put("relativePath", "$");
        assertEquals("externalApiValue(12, \"$\")", OperandCompiler.compile(field, null));
    }
}
