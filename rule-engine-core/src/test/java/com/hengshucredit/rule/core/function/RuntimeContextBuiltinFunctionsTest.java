package com.hengshucredit.rule.core.function;

import com.hengshucredit.rule.core.engine.RuntimeContextBridge;
import org.junit.After;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuntimeContextBuiltinFunctionsTest {

    private final RuntimeContextBuiltinFunctions functions = new RuntimeContextBuiltinFunctions();

    @After
    public void clear() {
        RuntimeContextBridge.clear();
    }

    @Test
    public void exposesReadOnlyCurrentRuleContext() {
        RuntimeContextBridge.bind((path, value) -> { });
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("id", 7L);
        rule.put("code", "credit_flow");
        rule.put("name", "授信流程");
        RuntimeContextBridge.setRuleContext(rule, Arrays.asList("年龄命中", "评分命中"));

        assertEquals("授信流程", functions.currentRuleName());
        assertEquals("credit_flow", functions.currentRule().get("code"));
        assertEquals(Arrays.asList("年龄命中", "评分命中"), functions.currentMatchedConditions());
        boolean rejected = false;
        try {
            functions.currentRule().put("code", "tampered");
        } catch (UnsupportedOperationException expected) {
            rejected = true;
        }
        assertTrue(rejected);
        assertEquals("credit_flow", functions.currentRule().get("code"));
    }

    @Test
    public void missingContextReturnsSafeEmptyValues() {
        assertTrue(functions.currentRule().isEmpty());
        assertEquals("", functions.currentRuleName());
        assertTrue(functions.currentMatchedConditions().isEmpty());
    }

    @Test
    public void constantCannotBeAssignedThroughRuntimeFunction() {
        Map<String, Object> captured = new LinkedHashMap<>();
        RuntimeContextBridge.bind(captured::put);
        RuntimeContextBridge.registerConstant("CREDIT_AMOUNT", 5000);

        boolean rejected = false;
        try {
            functions.setRuntimeValue("CREDIT_AMOUNT", 3000);
        } catch (IllegalStateException expected) {
            rejected = true;
        }

        assertTrue(rejected);
        assertFalse(captured.containsKey("CREDIT_AMOUNT"));
    }
}
