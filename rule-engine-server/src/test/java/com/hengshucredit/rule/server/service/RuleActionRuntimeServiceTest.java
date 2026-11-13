package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.core.engine.RuntimeContextBridge;
import org.junit.After;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Map;
import static org.junit.Assert.*;

public class RuleActionRuntimeServiceTest {
    @After public void clear() { RuntimeContextBridge.clear(); }

    @Test public void invalidContentIsRejectedBeforeEnteringRecoveryQueue() throws Exception {
        try (var actions = new AsyncListActionService(null, Files.createTempDirectory("list-action-invalid-").toString())) {
            actions.start();
            var service = new RuleActionRuntimeService();
            ReflectionTestUtils.setField(service, "listActions", actions);
            var events = new ArrayList<Map<String, Object>>();
            RuntimeContextBridge.bindTraceEventListener(events::add);
            String config = "{\"listIds\":[8],\"itemTypes\":[\"OTHER\"],\"operation\":\"MERGE\",\"expiryMode\":\"PERMANENT\"}";
            try (var scope = service.bindListWrites(1L, VariableResolveOptions.defaults(), null)) {
                var result = new QLExpressEngine().execute("writeRuleList(" + JSON.toJSONString(config) + ", [jsonParse(\"{\\\"key\\\":1}\")]); 7", new java.util.HashMap<>());
                assertFalse(result.isSuccess());
                assertEquals("REJECTED", ((Map<?, ?>) events.get(0).get("result")).get("status"));
                assertEquals(0, ((Number) actions.snapshot().get("pending")).intValue());
            }
        }
    }

    @Test public void experimentActionsRespectRootOutcomeAndFreezeSourceAndValues() throws Exception {
        var written = new java.util.concurrent.LinkedBlockingQueue<com.alibaba.fastjson.JSONObject>();
        var writer = new RuleListActionWriter(null, null) {
            @Override int write(String taskId, Long projectId, com.alibaba.fastjson.JSONObject config,
                                java.util.List<?> values, java.time.LocalDateTime at, com.alibaba.fastjson.JSONObject rule) {
                rule.put("written", values.get(0));
                written.add(rule);
                return 1;
            }
        };
        try (var queue = new AsyncListActionService(writer, Files.createTempDirectory("list-policies-").toString())) {
            queue.start();
            var runtime = new RuleActionRuntimeService();
            ReflectionTestUtils.setField(runtime, "listActions", queue);
            var options = VariableResolveOptions.defaults();
            options.setInvocationCache(new VariableResolutionInvocationCache());
            var cache = options.getInvocationCache();
            cache.setOrderTrace("root");
            cache.listActions().experiment = new VariableResolveOptions.ExperimentGroup(7L, 8L, "TEST", "exp");
            options.setDryRun(true);
            var ctx = RuntimeContextBridge.currentContext();
            ctx.setRuleContext(Map.of("traceId", "root"), java.util.List.of());
            ctx.setRuleContext(Map.of("id", 3L, "traceId", "child", "revisionId", 4L, "version", 6), java.util.List.of());
            var config = JSON.parseObject("{\"listIds\":[8],\"itemTypes\":[\"OTHER\"],\"operation\":\"MERGE\",\"expiryMode\":\"PERMANENT\"}");
            var values = new ArrayList<>(java.util.List.of("original"));
            try (var scope = runtime.bindListWrites(1L, options, null)) {
                assertEquals("SKIPPED_DRY_RUN", ((Map<?, ?>) ctx.writeRuleList(config.toJSONString(), values)).get("status"));
                config.put("writeInTestGroup", true);
                config.put("writeOnFailure", false);
                ctx.writeRuleList(config.toJSONString(), values);
                config.put("writeOnFailure", true);
                var accepted = (Map<?, ?>) ctx.writeRuleList(config.toJSONString(), values);
                assertEquals("PENDING_ROOT", accepted.get("status"));
                values.set(0, "changed");
            }
            var result = new com.hengshucredit.rule.model.dto.RuleResult();
            result.setExecutionStatus("WAITING_EXTERNAL");
            runtime.finishListWrites(options, result);
            assertEquals(2, cache.listActions().snapshot().size());
            assertEquals(0, ((Number) queue.snapshot().get("pending")).intValue());
            // 同一任务在断点恢复后重新命中，仍使用原快照。
            var restored = new VariableResolutionInvocationCache();
            restored.listActions().restore(JSON.parseArray(JSON.toJSONString(cache.listActions().snapshot())));
            restored.listActions().add(cache.listActions().snapshot().get(1));
            options.setInvocationCache(restored);
            result.setExecutionStatus("FAILED_RETRYABLE");
            runtime.finishListWrites(options, result);
            var persisted = written.poll(4, java.util.concurrent.TimeUnit.SECONDS);
            assertNotNull(persisted);
            assertEquals("root", persisted.getString("rootTraceId"));
            assertEquals("child", persisted.getString("traceId"));
            assertEquals(4L, persisted.getLongValue("revisionId"));
            assertEquals(8L, persisted.getLongValue("groupId"));
            assertEquals("TEST", persisted.getString("groupType"));
            assertEquals("FAILED", persisted.getString("outcome"));
            assertEquals("original", persisted.getString("written"));
            assertTrue(restored.listActions().isEmpty());
            assertNull(written.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS));
        }
    }

    @Test public void offlineReplayNeverSubmitsAListWrite() {
        var options = VariableResolveOptions.defaults(); options.setOfflineReplay(true);
        try (var scope = new RuleActionRuntimeService().bindListWrites(1L, options, null)) {
            var result = (Map<?, ?>) RuntimeContextBridge.currentContext().writeRuleList("", java.util.List.of());
            assertEquals("SKIPPED_REPLAY", result.get("status"));
        }
    }

    @Test public void dryRunNeverSubmitsAListWrite() {
        var options = VariableResolveOptions.defaults(); options.setDryRun(true);
        try (var scope = new RuleActionRuntimeService().bindListWrites(1L, options, null)) {
            var result = (Map<?, ?>) RuntimeContextBridge.currentContext().writeRuleList("", java.util.List.of());
            assertEquals("SKIPPED_DRY_RUN", result.get("status"));
        }
    }
}
