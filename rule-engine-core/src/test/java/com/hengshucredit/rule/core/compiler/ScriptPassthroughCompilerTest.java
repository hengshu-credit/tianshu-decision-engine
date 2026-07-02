package com.hengshucredit.rule.core.compiler;

import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.dto.RuleResult;
import org.junit.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ScriptPassthroughCompilerTest {

    @Test
    public void scriptAssignmentsAreReturnedAsResultMapWhenResultIsNotExplicit() {
        CompileResult compileResult = new ScriptPassthroughCompiler().compile(
                "{\"script\":\"taxAmount = amount * rate\\nfinalAmount = amount + taxAmount\"}");

        assertTrue(compileResult.getErrorMessage(), compileResult.isSuccess());
        assertTrue(compileResult.getCompiledScript().contains("\"taxAmount\": taxAmount"));
        assertTrue(compileResult.getCompiledScript().contains("\"finalAmount\": finalAmount"));

        RuleResult result = new QLExpressEngine().execute(
                compileResult.getCompiledScript(),
                new java.util.LinkedHashMap<String, Object>() {{
                    put("amount", 100);
                    put("rate", 0.13);
                }});

        assertTrue(result.getErrorMessage(), result.isSuccess());
        Map<?, ?> output = (Map<?, ?>) result.getResult();
        assertEquals(13.0, ((Number) output.get("taxAmount")).doubleValue(), 0.000001);
        assertEquals(113.0, ((Number) output.get("finalAmount")).doubleValue(), 0.000001);
    }

    @Test
    public void explicitResultIsKeptUnchanged() {
        String script = "score = 1\n_result = {\"score\": score}\n_result";

        CompileResult compileResult = new ScriptPassthroughCompiler().compile(script);

        assertTrue(compileResult.isSuccess());
        assertEquals(script, compileResult.getCompiledScript());
    }

    @Test
    public void blendCalcScriptExecutesWithTraceAndReturnsAssignedOutputs() {
        String script = "{\"script\":\"// 混业场景组合计费脚本\\n"
                + "if (taxpayerQualification == \\\"小规模纳税人\\\") {\\n"
                + "    multiDimRate = 0.03\\n"
                + "    excludingTaxAmount = billingAmount / (1 + multiDimRate)\\n"
                + "    taxAmount = roundTax(excludingTaxAmount * multiDimRate)\\n"
                + "} else {\\n"
                + "    basicAmount = billingAmount * basicServiceRatio\\n"
                + "    basicExcludingTax = basicAmount / (1 + 0.09)\\n"
                + "    basicTax = basicExcludingTax * 0.09\\n"
                + "    vasAmount = billingAmount * vasServiceRatio\\n"
                + "    vasExcludingTax = vasAmount / (1 + 0.06)\\n"
                + "    vasTax = vasExcludingTax * 0.06\\n"
                + "    excludingTaxAmount = roundTax(basicExcludingTax + vasExcludingTax)\\n"
                + "    taxAmount = roundTax(basicTax + vasTax)\\n"
                + "    if (excludingTaxAmount > 0) {\\n"
                + "        multiDimRate = roundTax(taxAmount / excludingTaxAmount)\\n"
                + "    } else {\\n"
                + "        multiDimRate = 0\\n"
                + "    }\\n"
                + "}\\n"
                + "finalAmount = billingAmount\\n"
                + "netAmount = excludingTaxAmount\\n"
                + "vatAmount = taxAmount\"}";

        CompileResult compileResult = new ScriptPassthroughCompiler().compile(script);

        assertTrue(compileResult.getErrorMessage(), compileResult.isSuccess());
        assertTrue(compileResult.getCompiledScript().contains("\"finalAmount\": finalAmount"));
        assertTrue(compileResult.getCompiledScript().contains("\"netAmount\": netAmount"));
        assertTrue(compileResult.getCompiledScript().contains("\"vatAmount\": vatAmount"));

        QLExpressEngine engine = new QLExpressEngine();
        RoundFunctions funcs = new RoundFunctions();
        engine.getRunner().addFunctionOfServiceMethod("roundTax", funcs, "roundTax", new Class<?>[]{Object.class});

        Map<String, Object> context = new HashMap<>();
        context.put("taxpayerQualification", "一般纳税人");
        context.put("billingAmount", 100000);
        context.put("basicServiceRatio", 0.6);
        context.put("vasServiceRatio", 0.4);

        RuleResult result = engine.execute(compileResult.getCompiledScript(), context, true);

        assertTrue(result.getErrorMessage(), result.isSuccess());
        Map<?, ?> output = (Map<?, ?>) result.getResult();
        assertEquals(100000.0, ((Number) output.get("finalAmount")).doubleValue(), 0.000001);
        assertEquals(((Number) output.get("taxAmount")).doubleValue(), ((Number) output.get("vatAmount")).doubleValue(), 0.000001);
        assertEquals(((Number) output.get("excludingTaxAmount")).doubleValue(), ((Number) output.get("netAmount")).doubleValue(), 0.000001);
    }

    public static class RoundFunctions {
        public double roundTax(Object amount) {
            return new BigDecimal(String.valueOf(amount))
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
        }
    }
}
