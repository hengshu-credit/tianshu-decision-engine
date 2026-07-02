package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.entity.RuleFunction;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FunctionRegistrarTest {

    @Test
    public void javaFunctionFallsBackFromExampleClassToServerClass() {
        RuleFunction function = new RuleFunction();
        function.setFuncCode("calculateVAT");
        function.setImplType("JAVA");
        function.setImplClass("com.hengshucredit.rule.example.functions.TaxFunctions");
        function.setImplMethod("calculateVAT");
        function.setParamsJson("[{\"name\":\"amount\",\"type\":\"NUMBER\"},{\"name\":\"rate\",\"type\":\"NUMBER\"}]");

        QLExpressEngine engine = new QLExpressEngine();
        new FunctionRegistrar().registerJavaFunctions(Collections.singletonList(function), engine.getRunner());

        RuleResult result = engine.execute(
                "taxAmount = calculateVAT(113000, 0.13);\n" +
                "_result = {\"taxAmount\": taxAmount}",
                Collections.emptyMap());

        assertTrue(result.getErrorMessage(), result.isSuccess());
        Map<?, ?> output = (Map<?, ?>) result.getResult();
        assertEquals(13000.0, ((Number) output.get("taxAmount")).doubleValue(), 0.000001);
    }
}
