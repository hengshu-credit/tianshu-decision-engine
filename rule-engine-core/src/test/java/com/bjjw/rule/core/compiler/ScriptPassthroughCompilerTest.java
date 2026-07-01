package com.bjjw.rule.core.compiler;

import com.bjjw.rule.core.engine.QLExpressEngine;
import com.bjjw.rule.model.dto.RuleResult;
import org.junit.Test;

import java.util.Collections;
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
}
