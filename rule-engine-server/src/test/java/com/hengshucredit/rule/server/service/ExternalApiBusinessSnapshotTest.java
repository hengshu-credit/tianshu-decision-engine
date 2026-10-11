package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class ExternalApiBusinessSnapshotTest {
    @Test public void contextCopyKeepsEverySharedBusinessValueAndDetachesFromOriginal() {
        Map<String, Object> shared = business();
        Map<String, Object> source = aliases(shared);
        Map<String, Object> copy = ExternalApiTrialContext.copy(source);
        assertBusiness(copy);
        shared.put("taskId", "mutated-after-copy");
        assertEquals("task-123", ExternalApiRequestPlan.read(copy, "lookup.taskId"));
        assertSame(shared, source.get("lookup"));
    }

    @Test public void internalAsyncAndObservedResponseCopiesKeepSharedBusinessValues() {
        var invoker = new ExternalApiInvokeService();
        for (String method : List.of("copyMap", "copyResponse")) {
            Map<String, Object> copied = ReflectionTestUtils.invokeMethod(invoker, method, aliases(business()));
            assertBusiness(copied);
        }
    }

    @Test public void checkpointAndContextReloadKeepDetachedBusinessValues() {
        var snapshot = new JSONObject(true);
        snapshot.put("stepIndexes", Map.of()); snapshot.put("stepCallIds", Map.of());
        var stored = new AtomicReference<String>();
        String trialId = UUID.randomUUID().toString();
        Map<String, Object> shared = business();
        try (var ignored = new ExternalApiTrialContext(trialId, 1, System.currentTimeMillis() + 60_000,
                snapshot, Map.of(), () -> { }, value -> stored.set(CanonicalJson.write(value)))) {
            ExternalApiTrialContext.checkpoint(Map.of("kind", "WORKFLOW", "results", Map.of("submit", Map.of("body", aliases(shared)))));
            shared.put("taskId", "changed-after-checkpoint");
        }
        try (var restored = new ExternalApiTrialContext(trialId, 1, System.currentTimeMillis() + 60_000,
                snapshot, JSON.parseObject(stored.get()), () -> { }, value -> fail("只读恢复不能重写检查点"))) {
            Object restoredBody = ExternalApiRequestPlan.read(restored.state(), "continuation.results.submit.body");
            assertTrue(restoredBody instanceof Map<?, ?>);
            assertBusiness((Map<?, ?>) restoredBody);
        }
    }

    @Test public void copiesRejectActualCyclesButDoNotInterpretLiteralRefs() {
        Map<String, Object> cycle = new LinkedHashMap<>(); cycle.put("self", cycle);
        assertThrows(IllegalArgumentException.class, () -> ExternalApiTrialContext.copy(cycle));
        var invoker = new ExternalApiInvokeService();
        for (String method : List.of("copyMap", "copyResponse")) {
            assertThrows(IllegalArgumentException.class, () -> ReflectionTestUtils.invokeMethod(invoker, method, cycle));
        }
        Map<String, Object> literal = Map.of("schema", Map.of("$ref", "$.original.taskId"));
        assertEquals(literal, ExternalApiTrialContext.copy(literal));
    }

    private static Map<String, Object> business() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("taskId", "task-123");
        value.put("amount", new BigDecimal("9007199254740993.12345678901234567890"));
        value.put("integer", new BigInteger("9007199254740993123456789"));
        value.put("schema", Map.of("$ref", "#/definitions/task"));
        return value;
    }

    private static Map<String, Object> aliases(Map<String, Object> shared) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("original", shared); source.put("lookup", shared);
        source.put("items", List.of(shared, shared));
        return source;
    }

    private static void assertBusiness(Map<?, ?> value) {
        Map<String, Object> expected = business();
        assertEquals(expected, value.get("original"));
        assertEquals("重复引用位置不能变成$ref对象", expected, value.get("lookup"));
        assertEquals(List.of(expected, expected), value.get("items"));
    }
}
