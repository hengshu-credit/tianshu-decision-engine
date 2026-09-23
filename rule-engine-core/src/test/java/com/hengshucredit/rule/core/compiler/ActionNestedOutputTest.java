package com.hengshucredit.rule.core.compiler;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.dto.RuleResult;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ActionNestedOutputTest {
    @Test
    public void createsMissingOutputContainersWithoutReevaluatingTheValue() {
        QLExpressEngine engine = new QLExpressEngine();
        AtomicInteger calls = new AtomicInteger();
        engine.getRunner().addFunction("nextValue", (com.alibaba.qlexpress4.runtime.function.CustomFunction)
                (context, parameters) -> calls.incrementAndGet());
        String script = ActionDataCompiler.compile(JSON.parseArray("[{\"type\":\"assign\","
                + "\"target\":\"结果.指标.值\",\"value\":\"nextValue()\"}]"));
        RuleResult result = engine.execute(script + "\n结果", new LinkedHashMap<>());
        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertEquals(Map.of("指标", Map.of("值", 1)), result.getResult());
        assertEquals(1, calls.get());
    }

    @Test
    public void preservesExistingSiblingFieldsAndSupportsExplicitNullParents() {
        String script = ActionDataCompiler.compile(JSON.parseArray("[{\"type\":\"assign\","
                + "\"target\":\"result.metrics.value\",\"value\":\"7\"}]"));
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("keep", "retained");
        output.put("metrics", null);
        RuleResult result = new QLExpressEngine().execute(script + "\nresult", new LinkedHashMap<>(Map.of("result", output)));
        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertEquals("retained", ((Map<?, ?>) result.getResult()).get("keep"));
        assertEquals(7, ((Number) ((Map<?, ?>) ((Map<?, ?>) result.getResult()).get("metrics")).get("value")).intValue());
    }

    @Test
    public void resultCollectorPreservesExistingInputParentsForNestedOutputs() {
        StringBuilder script = new StringBuilder("if (request.age >= 18) {\n"
                + "    request.score = request.age;\n"
                + "}\n");
        RuleScriptResultCollector.prependOutputNullInits(script, List.of("request.score"));
        RuleScriptResultCollector.appendResultMapReturn(script, List.of("request.score"));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("age", 20);
        request.put("keep", "yes");
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("request", request);

        RuleResult result = new QLExpressEngine().execute(script.toString(), context);

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertEquals("yes", request.get("keep"));
        assertEquals(20, ((Number) request.get("score")).intValue());
    }
}
