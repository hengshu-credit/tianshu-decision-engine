package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.core.engine.RuntimeContextBridge;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleVariable;
import org.junit.After;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class RuleVariableExecutionContextTest {
    @After public void clearContext() { RuntimeContextBridge.clear(); }

    @Test
    @SuppressWarnings("unchecked")
    public void lazyObjectViewRetainsExistingMapMutationWithoutFetchingSiblings() {
        var field = com.alibaba.fastjson.JSON.parseObject("{\"id\":30,\"refVariableId\":9,\"varType\":\"NUMBER\",\"lazyReference\":true}", RuleDataObjectField.class);
        var direct = new RuleDefinitionInputField(); direct.setVarId(30L); direct.setRefType("DATA_OBJECT"); direct.setScriptName("report.score");
        var source = new RuleVariable(); source.setId(9L); source.setScriptName("sourceScore"); source.setVarSource("API");
        var plan = new DataObjectFieldReferenceResolver().resolveSnapshot(List.of(direct), List.of(field), List.of(source));
        var options = VariableResolveOptions.defaults(); options.setRequiredScriptNames(Set.of("report.score", "sourceScore"));
        Map<String, Object> report = new LinkedHashMap<>(Map.of("existing", 0));
        var values = new LinkedHashMap<String, Object>(Map.of("report", report));
        try (var context = RuleVariableExecutionContext.prepare("SCRIPT", values, options, plan, Set.of(), () -> {
            assertFalse("对象结构和已有值操作不应调用缺失的引用来源", options.getRequiredScriptNames().contains("sourceScore"));
        })) {
            Map<String, Object> view = (Map<String, Object>) context.get("report");
            assertEquals(0, view.remove("existing"));
            assertTrue(report.isEmpty());
            view.put("changed", 1);
            assertEquals(1, report.get("changed"));
            view.clear(); assertTrue(report.isEmpty());
            assertSame(report, values.get("report"));
        }
    }

    @Test
    public void optionalLazyStatusReadLoadsOnlyWhenConditionIsEvaluated() {
        var field = com.alibaba.fastjson.JSON.parseObject("{\"id\":30,\"refVariableId\":9,\"varType\":\"NUMBER\",\"lazyReference\":true}", RuleDataObjectField.class);
        var direct = new RuleDefinitionInputField(); direct.setVarId(30L); direct.setRefType("DATA_OBJECT"); direct.setScriptName("report.score");
        var source = new RuleVariable(); source.setId(9L); source.setScriptName("sourceScore"); source.setVarSource("API");
        var plan = new DataObjectFieldReferenceResolver().resolveSnapshot(List.of(direct), List.of(field), List.of(source));
        var options = VariableResolveOptions.defaults(); options.setRequiredScriptNames(Set.of("report.score", "sourceScore"));
        options.setStatusReferenceKeys(Set.of("DATA_OBJECT:30"));
        var values = new LinkedHashMap<String, Object>(); int[] calls = {0};
        try (var context = RuleVariableExecutionContext.prepare("TREE", values, options, plan, Set.of(), () -> {
            if (options.getRequiredScriptNames().contains("sourceScore")) {
                calls[0]++; values.put("sourceScore", null);
                options.recordSourceState("VARIABLE", 9L, "OUTCOME", "ERROR");
            }
        })) {
            assertEquals(0, calls[0]);
            assertTrue(RuntimeContextBridge.sourceStatusMatches("DATA_OBJECT", "30", "OUTCOME", "ERROR"));
            assertEquals(1, calls[0]);
            assertTrue(RuntimeContextBridge.sourceStatusMatches("DATA_OBJECT", "30", "OUTCOME", "ERROR"));
            assertEquals(1, calls[0]);
            com.alibaba.fastjson.JSON.toJSONString(context);
            assertEquals(1, calls[0]);
        }
        RuntimeContextBridge.sourceStatusMatches("DATA_OBJECT", "30", "OUTCOME", "ERROR");
        assertEquals(1, calls[0]);
    }

    @Test
    public void onlyOptedInReferencedLeavesAreLazyAndExistingChildrenAreNeverFetched() {
        for (boolean lazy : new boolean[]{false, true}) {
            var first = new RuleDefinitionInputField(); first.setVarId(30L); first.setRefType("DATA_OBJECT"); first.setScriptName("report.score");
            var second = new RuleDefinitionInputField(); second.setVarId(31L); second.setRefType("DATA_OBJECT"); second.setScriptName("report.limit");
            var a = com.alibaba.fastjson.JSON.parseObject("{\"id\":30,\"refVariableId\":9,\"varType\":\"NUMBER\",\"lazyReference\":" + lazy + "}", RuleDataObjectField.class);
            var b = com.alibaba.fastjson.JSON.parseObject("{\"id\":31,\"refVariableId\":10,\"varType\":\"NUMBER\",\"lazyReference\":" + lazy + "}", RuleDataObjectField.class);
            var x = new RuleVariable(); x.setId(9L); x.setScriptName("sourceScore"); x.setVarSource("API");
            var y = new RuleVariable(); y.setId(10L); y.setScriptName("sourceLimit"); y.setVarSource("API");
            var plan = new DataObjectFieldReferenceResolver().resolveSnapshot(List.of(first, second), List.of(a, b), List.of(x, y));
            var options = VariableResolveOptions.defaults();
            options.setRequiredScriptNames(Set.of("report.score", "report.limit", "sourceScore", "sourceLimit"));
            Map<String, Object> values = new LinkedHashMap<>();
            var fetched = new java.util.LinkedHashSet<String>();
            var context = RuleVariableExecutionContext.prepare("TREE", values, options, plan, Set.of(), () -> {
                for (String name : List.of("sourceScore", "sourceLimit")) if (options.getRequiredScriptNames().contains(name)) {
                    fetched.add(name); values.put(name, name.equals("sourceScore") ? 88 : 100);
                }
            });
            var result = new QLExpressEngine().execute("return report.score + report.score;", context, true);
            assertTrue(result.getErrorMessage(), result.isSuccess()); assertEquals(176, result.getResult());
            assertEquals(lazy ? Set.of("sourceScore") : Set.of("sourceScore", "sourceLimit"), fetched);
        }
    }

    @Test
    public void nestedObjectReadResolvesItsIdBoundSourceOnceAndSharesOutputIdentity() {
        RuleDefinitionInputField target = new RuleDefinitionInputField();
        target.setVarId(30L); target.setRefType("DATA_OBJECT"); target.setScriptName("report.score");
        RuleDataObjectField field = new RuleDataObjectField();
        field.setId(30L); field.setRefVariableId(9L);
        RuleVariable source = new RuleVariable();
        source.setId(9L); source.setScriptName("sourceScore"); source.setVarSource("API");
        var plan = new DataObjectFieldReferenceResolver().resolveSnapshot(List.of(target), List.of(field), List.of(source));
        VariableResolveOptions options = VariableResolveOptions.defaults();
        options.setRequiredScriptNames(Set.of("report.score", "sourceScore"));
        Map<String, Object> output = new LinkedHashMap<>();
        Map<String, Object> values = new LinkedHashMap<>(Map.of("output", output));
        int[] calls = {0};
        var context = RuleVariableExecutionContext.prepare("TREE", values, options, plan, Set.of(), () -> {
            if (options.getRequiredScriptNames().contains("sourceScore")) {
                calls[0]++;
                values.put("sourceScore", 88);
            }
        });
        assertEquals(0, calls[0]);
        var result = new QLExpressEngine().execute("output.score = report.score;\nreturn report.score;", context, true);
        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertEquals(88, result.getResult());
        assertEquals(1, calls[0]);
        assertSame(output, values.get("output"));
        assertEquals(88, output.get("score"));
    }

    @Test
    public void explicitSourceStatusStillCollectsStatusAndUnusedFieldFailureDoesNotRun() {
        var options = VariableResolveOptions.defaults();
        options.setRequiredScriptNames(Set.of("external"));
        int[] calls = {0};
        var values = new LinkedHashMap<String, Object>();
        var context = RuleVariableExecutionContext.prepare("FLOW", values, options,
                DataObjectFieldReferenceResolver.ReferencePlan.empty(), Set.of(), () -> {
                    if (options.getRequiredScriptNames().contains("external")) throw new IllegalStateException("source unavailable");
                });
        var result = new QLExpressEngine().execute("return 1;", context);
        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertEquals(1, result.getResult());
        options.setStatusReferenceKeys(Set.of("VARIABLE:8"));
        var eager = RuleVariableExecutionContext.prepare("FLOW", values, options,
                DataObjectFieldReferenceResolver.ReferencePlan.empty(), Set.of(), () -> calls[0]++);
        assertEquals(values, eager);
        assertEquals(1, calls[0]);
    }
}
