package com.hengshucredit.rule.core.function;

import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.dto.RuleResult;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AggregateBuiltinFunctionsTest {

    @Test
    public void test空值判断函数可在规则脚本中执行() {
        QLExpressEngine engine = new QLExpressEngine();
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("apiScore", null);
        context.put("name", "  ");

        RuleResult result = engine.execute(
                "hitNull = isNull(apiScore);\n" +
                "hitBlank = isBlank(name);\n" +
                "fallbackScore = nvl(apiScore, 12);\n" +
                "_result = {\"hitNull\": hitNull, \"hitBlank\": hitBlank, \"fallbackScore\": fallbackScore}",
                context);

        assertTrue(result.getErrorMessage(), result.isSuccess());
        Map<?, ?> output = (Map<?, ?>) result.getResult();
        assertEquals(Boolean.TRUE, output.get("hitNull"));
        assertEquals(Boolean.TRUE, output.get("hitBlank"));
        assertEquals(12, ((Number) output.get("fallbackScore")).intValue());
    }

    @Test
    public void test聚合和包含函数可在规则脚本中执行() {
        QLExpressEngine engine = new QLExpressEngine();
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("scores", Arrays.asList(1, 2, 3, 4));
        context.put("tags", Arrays.asList("A", "B", "C"));
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("riskLevel", "HIGH");
        context.put("profile", profile);

        RuleResult result = engine.execute(
                "_result = {" +
                        "\"sum\": sum(scores), " +
                        "\"count\": count(scores), " +
                        "\"max\": max(scores), " +
                        "\"min\": min(scores), " +
                        "\"avg\": avg(scores), " +
                        "\"containsValue\": containsValue(tags, \"A\"), " +
                        "\"containsAnyValue\": containsAnyValue(tags, [\"X\", \"B\"]), " +
                        "\"containsAllValues\": containsAllValues(tags, [\"A\", \"C\"]), " +
                        "\"startsWithValue\": startsWithValue(\"ABCD\", \"AB\"), " +
                        "\"endsWithValue\": endsWithValue(\"ABCD\", \"CD\"), " +
                        "\"hasKey\": hasKey(profile, \"riskLevel\"), " +
                        "\"roundScale\": roundScale(12.3456, 2, \"HALF_UP\")" +
                        "}\n_result",
                context);

        assertTrue(result.getErrorMessage(), result.isSuccess());
        Map<?, ?> output = (Map<?, ?>) result.getResult();
        assertEquals(10.0d, ((Number) output.get("sum")).doubleValue(), 0.000001d);
        assertEquals(4, ((Number) output.get("count")).intValue());
        assertEquals(4.0d, ((Number) output.get("max")).doubleValue(), 0.000001d);
        assertEquals(1.0d, ((Number) output.get("min")).doubleValue(), 0.000001d);
        assertEquals(2.5d, ((Number) output.get("avg")).doubleValue(), 0.000001d);
        assertEquals(Boolean.TRUE, output.get("containsValue"));
        assertEquals(Boolean.TRUE, output.get("containsAnyValue"));
        assertEquals(Boolean.TRUE, output.get("containsAllValues"));
        assertEquals(Boolean.TRUE, output.get("startsWithValue"));
        assertEquals(Boolean.TRUE, output.get("endsWithValue"));
        assertEquals(Boolean.TRUE, output.get("hasKey"));
        assertEquals(12.35d, ((Number) output.get("roundScale")).doubleValue(), 0.000001d);
    }

    @Test
    public void advancedConditionHelpersWorkForRegexArrayMapAndSize() {
        AggregateBuiltinFunctions functions = new AggregateBuiltinFunctions();
        assertTrue(functions.regexMatchValue("VIP-001", "^VIP-[0-9]+$"));
        assertFalse(functions.regexMatchValue("VIP", "["));
        assertTrue(functions.containsElementValue(Arrays.asList("NORMAL", "VIP-GOLD"), "VIP"));
        assertTrue(functions.elementStartsWithValue(Arrays.asList("NORMAL", "VIP-GOLD"), "VIP"));
        assertTrue(functions.elementEndsWithValue(Arrays.asList("NORMAL", "GOLD-VIP"), "VIP"));
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("level", "GOLD");
        assertTrue(functions.hasMapValue(map, "GOLD"));
        assertEquals(2L, functions.sizeOfValue(Arrays.asList("A", "B")));
    }
}
