package com.hengshucredit.rule.core.function;

import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.dto.RuleResult;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
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
}
