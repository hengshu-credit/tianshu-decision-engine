package com.hengshucredit.rule.core.function;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BuiltinFunctionInvokerTest {

    @Test
    public void invokesRegisteredBuiltinFunctionsWithResolvedArguments() {
        assertEquals(680d, ((Number) BuiltinFunctionInvoker.invoke("numMax", Arrays.<Object>asList(680, 600))).doubleValue(), 0d);
        assertEquals("ABC", BuiltinFunctionInvoker.invoke("strUpper", Collections.<Object>singletonList("abc")));
        assertEquals("B", BuiltinFunctionInvoker.invoke("arrGet", Arrays.<Object>asList(Arrays.asList("A", "B"), 1)));
        assertTrue(String.valueOf(BuiltinFunctionInvoker.invoke("currentDate", Collections.emptyList())).matches("\\d{4}-\\d{2}-\\d{2}"));
    }

    @Test
    public void appliesCommonIrreversibleFieldDigests() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72",
                BuiltinFunctionInvoker.invoke("md5", Collections.<Object>singletonList("abc")));
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d",
                BuiltinFunctionInvoker.invoke("sha1", Collections.<Object>singletonList("abc")));
        assertEquals("3187015765d6d96b014ddb06d20b25956f6c6020732e559befbf20dd057ad151",
                BuiltinFunctionInvoker.invoke("sha256", Collections.<Object>singletonList("天枢")));
        assertEquals("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
                BuiltinFunctionInvoker.invoke("hmacSha256",
                        Arrays.<Object>asList("The quick brown fox jumps over the lazy dog", "key")));
    }

    @Test
    public void returnsNullWhenDigestInputIsNull() {
        assertNull(BuiltinFunctionInvoker.invoke("sha256", Collections.<Object>singletonList(null)));
        assertNull(BuiltinFunctionInvoker.invoke("hmacSha256", Arrays.<Object>asList(null, "key")));
        assertNull(BuiltinFunctionInvoker.invoke("hmacSha256", Arrays.<Object>asList("abc", null)));
    }

    @Test
    public void returnsNullWhenHmacKeyIsEmpty() {
        assertNull(BuiltinFunctionInvoker.invoke("hmacSha256", Arrays.<Object>asList("abc", "")));
    }

    @Test
    public void invokesRandomBuiltinsWithZeroTwoAndFourArguments() {
        long defaultInt = ((Number) BuiltinFunctionInvoker.invoke("randomInt", Collections.emptyList())).longValue();
        assertTrue(defaultInt == 0L || defaultInt == 1L);
        assertEquals(5L, ((Number) BuiltinFunctionInvoker.invoke("randomInt",
                Arrays.<Object>asList(5, 5))).longValue());
        assertEquals(2L, ((Number) BuiltinFunctionInvoker.invoke("randomInt",
                Arrays.<Object>asList(1, 3, false, false))).longValue());
        assertEquals(0.5D, ((Number) BuiltinFunctionInvoker.invoke("randomDecimal",
                Arrays.<Object>asList(0.5D, 0.5D))).doubleValue(), 0D);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnregisteredOrUnsafeFunctionCode() {
        BuiltinFunctionInvoker.invoke("getClass().getName", Collections.emptyList());
    }
}
