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

    @Test public void invalidContentDoesNotEnterRecoveryQueueOrInterruptFollowingActions() throws Exception {
        try (var actions = new AsyncListActionService(null, Files.createTempDirectory("list-action-invalid-").toString())) {
            actions.start();
            var service = new RuleActionRuntimeService();
            ReflectionTestUtils.setField(service, "listActions", actions);
            var events = new ArrayList<Map<String, Object>>();
            RuntimeContextBridge.bindTraceEventListener(events::add);
            String config = "{\"listIds\":[8],\"itemTypes\":[\"OTHER\"],\"operation\":\"MERGE\",\"expiryMode\":\"PERMANENT\"}";
            try (var scope = service.bindListWrites(1L, VariableResolveOptions.defaults(), null)) {
                var result = new QLExpressEngine().execute("writeRuleList(" + JSON.toJSONString(config) + ", [jsonParse(\"{\\\"key\\\":1}\")]); 7", new java.util.HashMap<>());
                assertTrue(result.getErrorMessage(), result.isSuccess());
                assertEquals(7, result.getResult());
                assertEquals("REJECTED", ((Map<?, ?>) events.get(0).get("result")).get("status"));
                assertEquals(0, ((Number) actions.snapshot().get("pending")).intValue());
            }
        }
    }

    @Test public void offlineReplayNeverSubmitsAListWrite() {
        var options = VariableResolveOptions.defaults(); options.setOfflineReplay(true);
        try (var scope = new RuleActionRuntimeService().bindListWrites(1L, options, null)) {
            var result = (Map<?, ?>) RuntimeContextBridge.currentContext().writeRuleList("", java.util.List.of());
            assertEquals("SKIPPED_REPLAY", result.get("status"));
        }
    }
}
