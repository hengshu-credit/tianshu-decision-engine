package com.hengshucredit.rule.core.compiler;

import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.dto.RuleResult;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ConstantValueCodecTest {

    @Test
    public void compilesAndExecutesSupportedConstantValues() {
        assertExpression("STRING", "", "''", "");
        assertExpression("STRING", "O'Reilly", "'O\\'Reilly'", "O'Reilly");
        assertExpression("OBJECT", "null", "null", null);
        assertExpression("LIST", "[]", "[]", Collections.emptyList());
        assertExpression("MAP", "{}", "jsonParse('{}')", Collections.emptyMap());
        assertExpression("BOOLEAN", "TRUE", "true", true);
        assertExpression("NUMBER", "-1.25", "-1.25", -1.25D);
        assertExpression("DOUBLE", "Infinity", "1.0 / 0.0", Double.POSITIVE_INFINITY);
        assertExpression("DOUBLE", "-Infinity", "-1.0 / 0.0", Double.NEGATIVE_INFINITY);
    }

    @Test
    public void normalizesTypedValuesWithoutChangingStringContent() {
        Assert.assertEquals("", ConstantValueCodec.normalize("STRING", ""));
        Assert.assertEquals("  value  ", ConstantValueCodec.normalize("STRING", "  value  "));
        Assert.assertEquals("true", ConstantValueCodec.normalize("BOOLEAN", " TRUE "));
        Assert.assertEquals("12.50", ConstantValueCodec.normalize("NUMBER", " 12.50 "));
        Assert.assertEquals("[1,2]", ConstantValueCodec.normalize("LIST", " [1,2] "));
        Assert.assertEquals("{\"a\":1}", ConstantValueCodec.normalize("MAP", " {\"a\":1} "));
    }

    @Test
    public void rejectsMissingOrMismatchedValues() {
        assertInvalid("STRING", null);
        assertInvalid("BOOLEAN", "yes");
        assertInvalid("NUMBER", "Infinity");
        assertInvalid("NUMBER", "abc");
        assertInvalid("LIST", "{}");
        assertInvalid("MAP", "[]");
        assertInvalid("OBJECT", "");
    }

    @SuppressWarnings("unchecked")
    private void assertExpression(String type, String raw, String expression, Object expected) {
        Assert.assertEquals(expression, ConstantValueCodec.toQlExpression(type, raw));
        Object runtime = ConstantValueCodec.toRuntimeValue(type, raw);
        if (expected instanceof List) {
            Assert.assertTrue(runtime instanceof List);
            Assert.assertEquals(expected, runtime);
        } else if (expected instanceof Map) {
            Assert.assertTrue(runtime instanceof Map);
            Assert.assertEquals(expected, runtime);
        } else if (expected instanceof Double && ((Double) expected).isInfinite()) {
            Assert.assertEquals(expected, runtime);
        } else if (expected instanceof Number) {
            Assert.assertEquals(((Number) expected).doubleValue(), ((Number) runtime).doubleValue(), 0.0D);
        } else {
            Assert.assertEquals(expected, runtime);
        }

        RuleResult result = new QLExpressEngine().execute(expression, Collections.emptyMap());
        Assert.assertTrue(result.getErrorMessage(), result.isSuccess());
        Object executed = result.getResult();
        if (expected instanceof Number) {
            Assert.assertEquals(((Number) expected).doubleValue(), ((Number) executed).doubleValue(), 0.0D);
        } else {
            Assert.assertEquals(expected, executed);
        }
    }

    private void assertInvalid(String type, String raw) {
        try {
            ConstantValueCodec.normalize(type, raw);
            Assert.fail("Expected invalid constant: " + type + " / " + raw);
        } catch (IllegalArgumentException expected) {
            Assert.assertFalse(expected.getMessage().isEmpty());
        }
    }
}
