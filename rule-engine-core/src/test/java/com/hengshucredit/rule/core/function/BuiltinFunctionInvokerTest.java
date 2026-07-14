package com.hengshucredit.rule.core.function;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BuiltinFunctionInvokerTest {

    @Test
    public void invokesRegisteredBuiltinFunctionsWithResolvedArguments() {
        assertEquals(680d, ((Number) BuiltinFunctionInvoker.invoke("numMax", Arrays.<Object>asList(680, 600))).doubleValue(), 0d);
        assertEquals("ABC", BuiltinFunctionInvoker.invoke("strUpper", Collections.<Object>singletonList("abc")));
        assertEquals("B", BuiltinFunctionInvoker.invoke("arrGet", Arrays.<Object>asList(Arrays.asList("A", "B"), 1)));
        assertTrue(String.valueOf(BuiltinFunctionInvoker.invoke("currentDate", Collections.emptyList())).matches("\\d{4}-\\d{2}-\\d{2}"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnregisteredOrUnsafeFunctionCode() {
        BuiltinFunctionInvoker.invoke("getClass().getName", Collections.emptyList());
    }
}
