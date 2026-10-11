package com.hengshucredit.rule.core.compiler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class ScorecardLevelAssignmentTest {
    private final RuleCompiler[] compilers = {new ScorecardCompiler(), new AdvancedScorecardCompiler()};

    @Test
    public void missingOrClearedLevelTargetOnlyReturnsScore() {
        for (RuleCompiler compiler : compilers) {
            JSONObject model = model();
            assertScoreOnly(compiler, model);
            model.getJSONArray("thresholds").getJSONObject(0).put("resultVar", "riskLevel");
            model.put("levelVar", null);
            assertScoreOnly(compiler, model);
            model.put("levelVar", new JSONObject());
            assertScoreOnly(compiler, model);
        }
    }

    @Test
    public void levelWithoutIntervalsDoesNotWriteOrReturnLevel() {
        for (RuleCompiler compiler : compilers) {
            JSONObject model = model();
            model.put("levelVar", target(2, "grade"));
            model.remove("thresholds");
            assertScoreOnly(compiler, model);
        }
    }

    @Test
    public void configuredLevelResolvesByIdAndReturnsAssignedBoundaryValue() {
        for (RuleCompiler compiler : compilers) {
            JSONObject model = model();
            model.put("levelVar", target(2, "oldGrade"));
            model.put("resultVar", target(1, "oldScore"));
            VarContext context = new VarContext(Map.of(1L, "score", 2L, "ApprovedGrade"), Map.of(), Map.of("VARIABLE:1", "score", "VARIABLE:2", "ApprovedGrade"));
            CompileResult compiled = compiler.compile(model.toJSONString(), context);
            assertTrue(compiled.getErrorMessage(), compiled.isSuccess());
            var result = new QLExpressEngine().execute(compiled.getCompiledScript(), new LinkedHashMap<>());
            assertTrue(result.getErrorMessage(), result.isSuccess());
            assertEquals(Map.of("score", 100.0, "ApprovedGrade", "HIGH"), result.getResult());
            assertFalse(compiled.getCompiledScript().contains("oldGrade"));
            assertFalse(compiled.getCompiledScript().contains("riskLevel"));
        }
    }

    @Test
    public void scoreAndLevelCannotAssignTheSameField() {
        for (RuleCompiler compiler : compilers) {
            JSONObject model = model();
            model.put("levelVar", JSON.parseObject("{\"operand\":{\"kind\":\"PATH\",\"code\":\"score\"}}"));
            CompileResult compiled = compiler.compile(model.toJSONString());
            assertFalse(compiled.isSuccess());
            assertTrue(compiled.getErrorMessage().contains("不能相同"));
        }
    }

    private void assertScoreOnly(RuleCompiler compiler, JSONObject model) {
        CompileResult compiled = compiler.compile(JSON.toJSONString(model, com.alibaba.fastjson.serializer.SerializerFeature.WriteMapNullValue));
        assertTrue(compiled.getErrorMessage(), compiled.isSuccess());
        var result = new QLExpressEngine().execute(compiled.getCompiledScript(), new LinkedHashMap<>());
        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertEquals(Map.of("score", 100.0), result.getResult());
        assertFalse(compiled.getCompiledScript().contains("riskLevel"));
    }

    private static JSONObject model() {
        return JSON.parseObject("{\"initialScore\":100,\"resultVar\":{\"varCode\":\"score\"},"
                + "\"thresholds\":[{\"min\":0,\"max\":100,\"result\":\"LOW\"},"
                + "{\"min\":100,\"max\":200,\"result\":\"HIGH\"}]}");
    }

    private static JSONObject target(long id, String code) {
        return JSON.parseObject("{\"operand\":{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\","
                + "\"refId\":" + id + ",\"code\":\"" + code + "\",\"valueType\":\"STRING\"}}");
    }
}
