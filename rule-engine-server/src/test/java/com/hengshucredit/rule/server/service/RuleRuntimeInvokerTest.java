package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.core.engine.RuntimeContextBridge;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class RuleRuntimeInvokerTest {

    @Test
    public void runtimeBridgeWritesComputedValuesIntoCurrentExecutionFrame() {
        RuleRuntimeInvoker invoker = new RuleRuntimeInvoker();
        Map<String, Object> context = new LinkedHashMap<>();
        invoker.enter("JCLTest", 1L, "TIANSHU", context);
        try {
            RuntimeContextBridge.setValue("age", 22);
            RuntimeContextBridge.setValue("result.decision", "PASS");

            assertEquals(Integer.valueOf(22), context.get("age"));
            assertEquals("PASS", ((Map<?, ?>) context.get("result")).get("decision"));
        } finally {
            invoker.exit();
        }

        RuntimeContextBridge.setValue("afterExit", true);
        assertFalse(context.containsKey("afterExit"));
    }
}
