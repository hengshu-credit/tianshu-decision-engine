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
        function.setImplClass("com.bjjw.rule.example.functions.TaxFunctions");
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

    @Test
    public void javaFunctionFormatsAmountWithoutScriptConstructor() {
        RuleFunction function = new RuleFunction();
        function.setFuncCode("formatAmount");
        function.setImplType("JAVA");
        function.setImplClass("com.hengshucredit.rule.server.functions.TaxFunctions");
        function.setImplMethod("formatAmount");
        function.setParamsJson("[{\"name\":\"amount\",\"type\":\"NUMBER\"}]");

        QLExpressEngine engine = new QLExpressEngine();
        new FunctionRegistrar().registerJavaFunctions(Collections.singletonList(function), engine.getRunner());

        RuleResult result = engine.execute(
                "formatted = formatAmount(13000.0);\n" +
                "_result = {\"formatted\": formatted}",
                Collections.emptyMap());

        assertTrue(result.getErrorMessage(), result.isSuccess());
        Map<?, ?> output = (Map<?, ?>) result.getResult();
        assertEquals("13,000.00", output.get("formatted"));
    }

    @Test
    public void scriptFunctionPrefixExecutesRoundTax() {
        RuleFunction function = new RuleFunction();
        function.setFuncCode("roundTax");
        function.setImplType("SCRIPT");
        function.setParamsJson("[{\"name\":\"amount\",\"type\":\"NUMBER\"}]");
        function.setImplScript("scaled = amount * 100 + 0.5; return (scaled - scaled % 1) / 100;");

        String prefix = new FunctionRegistrar().buildScriptFunctionPrefix(Collections.singletonList(function));

        QLExpressEngine engine = new QLExpressEngine();
        RuleResult result = engine.execute(prefix +
                "taxAmount = roundTax(12.345);\n" +
                "_result = {\"taxAmount\": taxAmount}\n" +
                "_result",
                Collections.emptyMap());

        assertTrue(result.getErrorMessage(), result.isSuccess());
        Map<?, ?> output = (Map<?, ?>) result.getResult();
        assertEquals(12.35, ((Number) output.get("taxAmount")).doubleValue(), 0.000001);
    }
}
