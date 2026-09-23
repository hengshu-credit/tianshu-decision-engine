package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.core.engine.RuntimeContextBridge;
import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.derived.DerivedVariableService;
import com.hengshucredit.rule.server.service.onnx.OnnxModelExecutionService;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class FrozenModelInputContractTest {
    @Test
    public void suppliedObjectLeafWinsOverReferencedApiIncludingExplicitNull() {
        for (Object supplied : java.util.Arrays.asList(0, false, "", null)) {
            RuleModel model = new RuleModel(); model.setId(9L); model.setModelCode("risk"); model.setModelFormat("ONNX"); model.setModelContent("AQID"); model.setModelConfig("{}");
            var binding = input("x", 20L, "DATA_OBJECT", "request.value");
            binding.setFieldType(supplied instanceof Boolean ? "BOOLEAN" : supplied instanceof String ? "STRING" : "NUMBER");
            model.setInputFields(List.of(binding)); model.setOutputFields(List.of());
            var api = variable(13L, "remote", "API", "{\"apiConfigId\":7,\"resultPath\":\"body.score\"}");
            AtomicReference<Map<String, Object>> nativeInputs = new AtomicReference<>();
            var models = new RuleModelService(); var executor = new ModelExecutionTimeoutExecutor();
            ReflectionTestUtils.setField(models, "executionParameterBinder", new ExecutionParameterBinder());
            ReflectionTestUtils.setField(models, "modelExecutionTimeoutExecutor", executor);
            ReflectionTestUtils.setField(models, "onnxModelExecutionService", new OnnxModelExecutionService(null) {
                @Override public Map<String, Object> execute(byte[] bytes, String config, Map<String, Object> params) {
                    nativeInputs.set(new LinkedHashMap<>(params)); return Map.of("score", 1);
                }
            });
            var resolver = new VariableSourceResolver(); ReflectionTestUtils.setField(resolver, "ruleModelService", models);
            ReflectionTestUtils.setField(resolver, "externalApiInvokeService", new ExternalApiInvokeService() {
                @Override public Map<String, Object> invoke(Long id, Map<String, Object> params) { throw new AssertionError("已有对象值不应再调用引用来源"); }
            });
            var options = VariableResolveOptions.defaults(); options.setRequiredNamesUpstreamOnly(true); options.setRequiredScriptNames(Set.of("risk"));
            options.setDerivedReferencePaths(Map.of("DATA_OBJECT:20", "remote", "VARIABLE:13", "remote"));
            options.setDataObjectReferencePaths(Map.of("DATA_OBJECT:20", "request.value"));
            Map<String, Object> request = new LinkedHashMap<>(); request.put("value", supplied);
            try {
                resolver.resolveIntoSnapshot(List.of(api), List.of(model), List.of(), new LinkedHashMap<>(Map.of("request", request)), options);
                assertTrue(nativeInputs.get().containsKey("x"));
                assertEquals(supplied instanceof Number ? 0D : supplied, nativeInputs.get().get("x"));
            } finally { executor.close(); RuntimeContextBridge.clear(); }
        }
    }
    @Test
    public void modelInputsResolveFrozenConstantDerivedApiAndObjectFieldById() {
        RuleModel model = new RuleModel();
        model.setId(9L); model.setModelCode("risk"); model.setModelFormat("ONNX"); model.setModelContent("AQID");
        model.setModelConfig("{}");
        model.setInputFields(List.of(input("x", 11L, "VARIABLE", "staleDerived"),
                input("y", 12L, "CONSTANT", "staleConstant"),
                input("z", 20L, "DATA_OBJECT", "staleObjectField")));
        model.setOutputFields(List.of());
        RuleVariable derived = variable(11L, "currentDerived", "DERIVED", "{\"mode\":\"EXPRESSION\",\"expression\":{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\",\"refId\":13,\"code\":\"staleApi\"}}");
        RuleVariable constant = variable(12L, "trustedConstant", "CONSTANT", null);
        constant.setDefaultValue("5");
        RuleVariable api = variable(13L, "currentApi", "API", "{\"apiConfigId\":7,\"resultPath\":\"body.score\"}");
        AtomicReference<Map<String, Object>> nativeInputs = new AtomicReference<>();
        RuleModelService models = new RuleModelService();
        ModelExecutionTimeoutExecutor executor = new ModelExecutionTimeoutExecutor();
        ReflectionTestUtils.setField(models, "executionParameterBinder", new ExecutionParameterBinder());
        ReflectionTestUtils.setField(models, "modelExecutionTimeoutExecutor", executor);
        ReflectionTestUtils.setField(models, "onnxModelExecutionService", new OnnxModelExecutionService(null) {
            @Override public Map<String, Object> execute(byte[] bytes, String config, Map<String, Object> params) {
                nativeInputs.set(new LinkedHashMap<>(params));
                return Map.of("score", 0.75);
            }
        });
        DerivedVariableService derivation = new DerivedVariableService();
        VariableSourceResolver resolver = new VariableSourceResolver();
        ReflectionTestUtils.setField(resolver, "derivedVariableService", derivation);
        ReflectionTestUtils.setField(resolver, "ruleModelService", models);
        ReflectionTestUtils.setField(resolver, "externalApiInvokeService", new ExternalApiInvokeService() {
            @Override public Map<String, Object> invoke(Long id, Map<String, Object> values) { return Map.of("body", Map.of("score", 88)); }
        });
        VariableResolveOptions options = VariableResolveOptions.defaults();
        options.setRequiredScriptNames(Set.of("risk")); options.setRequiredNamesUpstreamOnly(true);
        options.setDerivedReferencePaths(Map.of("VARIABLE:11", "currentDerived", "CONSTANT:12", "trustedConstant",
                "DATA_OBJECT:20", "request.quantity", "VARIABLE:13", "currentApi"));
        Map<String, Object> values = new LinkedHashMap<>(Map.of("request", Map.of("quantity", 7),
                "staleDerived", 999, "trustedConstant", 999, "currentApi", 999));
        try {
            resolver.resolveIntoSnapshot(List.of(derived, constant, api), List.of(model), List.of(), values, options);
            assertEquals(Map.of("x", 88d, "y", 5d, "z", 7d), nativeInputs.get());
            assertEquals(Map.of("score", 0.75), values.get("risk"));
        } finally {
            executor.close();
            RuntimeContextBridge.clear();
        }
    }

    private static RuleModelInputField input(String name, Long id, String type, String stale) {
        RuleModelInputField field = new RuleModelInputField();
        field.setFieldName(name); field.setFieldType("NUMBER"); field.setVarId(id); field.setRefType(type);
        field.setSourceOperand("{\"kind\":\"REFERENCE\",\"refId\":" + id + ",\"refType\":\"" + type + "\",\"code\":\"" + stale + "\"}");
        return field;
    }

    private static RuleVariable variable(Long id, String code, String source, String config) {
        RuleVariable variable = new RuleVariable();
        variable.setId(id); variable.setVarCode(code); variable.setScriptName(code); variable.setVarSource(source);
        variable.setVarType("NUMBER"); variable.setStatus(1); variable.setSourceConfig(config);
        return variable;
    }
}
