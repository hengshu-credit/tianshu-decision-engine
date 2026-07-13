package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleModelInputField;
import com.hengshucredit.rule.model.entity.RuleModelOutputField;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleModelInputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleModelMapper;
import com.hengshucredit.rule.server.mapper.RuleModelOutputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleVariableMapper;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RuleModelServiceTest {

    @Test
    public void modelFieldEntitiesRemoveLegacyMissingAndTransformFields() {
        assertTrue(Stream.of(RuleModelInputField.class.getDeclaredFields())
                .noneMatch(field -> "missingValue".equals(field.getName())));
        assertTrue(Stream.of(RuleModelOutputField.class.getDeclaredFields())
                .noneMatch(field -> "transformType".equals(field.getName())));
        assertTrue(Stream.of(RuleModelOutputField.class.getDeclaredFields())
                .anyMatch(field -> "transformOperand".equals(field.getName())));
    }

    @Test
    public void publishRejectsUnboundInputField() {
        RuleModelService service = new RuleModelService();
        ReflectionTestUtils.setField(service, "modelMapper", mapper(RuleModelMapper.class, (proxy, method, args) -> {
            if ("selectById".equals(method.getName())) return model();
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "inputFieldMapper", mapper(RuleModelInputFieldMapper.class, (proxy, method, args) -> {
            if ("selectList".equals(method.getName())) return Collections.singletonList(inputField("age", "INTEGER", null, null, null));
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "outputFieldMapper", mapper(RuleModelOutputFieldMapper.class, (proxy, method, args) -> {
            if ("selectList".equals(method.getName())) return Collections.emptyList();
            return defaultValue(method.getReturnType());
        }));

        try {
            service.publish(1L, "publish", "tester");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("未完整关联变量"));
            return;
        }
        throw new AssertionError("Expected publish validation error");
    }

    @Test
    public void publishRejectsTypeMismatch() {
        RuleModelService service = new RuleModelService();
        ReflectionTestUtils.setField(service, "modelMapper", mapper(RuleModelMapper.class, (proxy, method, args) -> {
            if ("selectById".equals(method.getName())) return model();
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "inputFieldMapper", mapper(RuleModelInputFieldMapper.class, (proxy, method, args) -> {
            if ("selectList".equals(method.getName())) return Collections.singletonList(inputField("age", "INTEGER", 10L, "VARIABLE", "age"));
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "outputFieldMapper", mapper(RuleModelOutputFieldMapper.class, (proxy, method, args) -> {
            if ("selectList".equals(method.getName())) return Collections.emptyList();
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "variableMapper", mapper(RuleVariableMapper.class, (proxy, method, args) -> {
            if ("selectById".equals(method.getName())) return variable("STRING");
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "dataObjectFieldMapper", mapper(RuleDataObjectFieldMapper.class, (proxy, method, args) -> null));

        try {
            service.publish(1L, "publish", "tester");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("类型不匹配"));
            return;
        }
        throw new AssertionError("Expected publish validation error");
    }

    @Test
    public void publishRejectsLiteralOutputTarget() {
        RuleModelService service = new RuleModelService();
        ReflectionTestUtils.setField(service, "modelMapper", mapper(RuleModelMapper.class, (proxy, method, args) -> {
            if ("selectById".equals(method.getName())) return model();
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "inputFieldMapper", mapper(RuleModelInputFieldMapper.class, (proxy, method, args) -> {
            if ("selectList".equals(method.getName())) return Collections.emptyList();
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "outputFieldMapper", mapper(RuleModelOutputFieldMapper.class, (proxy, method, args) -> {
            if ("selectList".equals(method.getName())) {
                RuleModelOutputField field = new RuleModelOutputField();
                field.setFieldName("score");
                field.setFieldLabel("score");
                field.setFieldType("DOUBLE");
                field.setTargetOperand("{\"kind\":\"LITERAL\",\"value\":100}");
                return Collections.singletonList(field);
            }
            return defaultValue(method.getReturnType());
        }));

        try {
            service.publish(1L, "publish", "tester");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().toLowerCase().contains("operand"));
            return;
        }
        throw new AssertionError("Expected writable target validation error");
    }

    @Test
    public void updateOutputFieldPersistsFunctionTransformOperand() {
        AtomicReference<RuleModelOutputField> updated = new AtomicReference<>();
        RuleModelService service = outputUpdateService(function(7L, 1, twoParams()), updated);
        RuleModelOutputField request = new RuleModelOutputField();
        request.setVarId(20L);
        request.setRefType("VARIABLE");
        request.setScriptName("score");
        request.setFieldLabel("评分");
        request.setFieldType("DOUBLE");
        String transform = transformOperand(7L,
                "{\"kind\":\"REFERENCE\",\"refId\":100,\"refType\":\"MODEL_OUTPUT\",\"code\":\"risk_model.probability\"}",
                "{\"kind\":\"LITERAL\",\"value\":\"600\",\"valueType\":\"NUMBER\"}");
        ReflectionTestUtils.setField(request, "transformOperand", transform);

        service.updateOutputField(100L, request);

        assertEquals(transform, ReflectionTestUtils.getField(updated.get(), "transformOperand"));
    }

    @Test
    public void updateOutputFieldRejectsIncompleteFunctionArguments() {
        assertInvalidTransform(transformOperand(7L,
                "{\"kind\":\"REFERENCE\",\"refId\":100,\"refType\":\"MODEL_OUTPUT\",\"code\":\"risk_model.probability\"}",
                "null"), function(7L, 1, twoParams()), "参数");
    }

    @Test
    public void updateOutputFieldRejectsNestedFunctionArguments() {
        assertInvalidTransform(transformOperand(7L,
                "{\"kind\":\"FUNCTION\",\"functionId\":8,\"functionCode\":\"max\",\"args\":[]}",
                "{\"kind\":\"LITERAL\",\"value\":\"600\",\"valueType\":\"NUMBER\"}"),
                function(7L, 1, twoParams()), "嵌套");
    }

    @Test
    public void updateOutputFieldRejectsMissingOrDisabledFunction() {
        assertInvalidTransform(transformOperand(99L,
                "{\"kind\":\"LITERAL\",\"value\":\"0.2\",\"valueType\":\"NUMBER\"}",
                "{\"kind\":\"LITERAL\",\"value\":\"600\",\"valueType\":\"NUMBER\"}"), null, "不存在");
        assertInvalidTransform(transformOperand(7L,
                "{\"kind\":\"LITERAL\",\"value\":\"0.2\",\"valueType\":\"NUMBER\"}",
                "{\"kind\":\"LITERAL\",\"value\":\"600\",\"valueType\":\"NUMBER\"}"),
                function(7L, 0, twoParams()), "停用");
    }

    @Test
    public void updateOutputFieldRejectsFunctionOutsideModelScope() {
        RuleFunction function = function(7L, 1, twoParams());
        function.setScope(RuleFunctionService.SCOPE_PROJECT);
        function.setProjectId(99L);
        assertInvalidTransform(transformOperand(7L,
                "{\"kind\":\"LITERAL\",\"value\":\"0.2\",\"valueType\":\"NUMBER\"}",
                "{\"kind\":\"LITERAL\",\"value\":\"600\",\"valueType\":\"NUMBER\"}"), function, "作用域");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void outputTransformUsesExplicitRawOutputAndLiteralArguments() {
        RuleFunction function = function(7L, 1, twoParams());
        function.setImplType("SCRIPT");
        function.setImplScript("return probability * base;");
        RuleFunctionService functionService = new RuleFunctionService() {
            @Override
            public RuleFunction getById(Long id) {
                return function.getId().equals(id) ? function : null;
            }
        };
        ReflectionTestUtils.setField(functionService, "qlExpressEngine", new QLExpressEngine());
        ReflectionTestUtils.setField(functionService, "functionRegistrar", new FunctionRegistrar());

        RuleModelOutputField output = new RuleModelOutputField();
        output.setFieldName("probability");
        output.setTransformOperand(transformOperand(7L,
                "{\"kind\":\"REFERENCE\",\"refId\":100,\"refType\":\"MODEL_OUTPUT\",\"code\":\"risk_model.probability\"}",
                "{\"kind\":\"LITERAL\",\"value\":\"600\",\"valueType\":\"NUMBER\"}"));
        RuleModelService service = new RuleModelService();
        ReflectionTestUtils.setField(service, "ruleFunctionService", functionService);
        ReflectionTestUtils.setField(service, "outputFieldMapper", mapper(RuleModelOutputFieldMapper.class, (proxy, method, args) -> {
            if ("selectList".equals(method.getName())) return Collections.singletonList(output);
            return defaultValue(method.getReturnType());
        }));
        RuleModel model = model();
        model.setModelCode("risk_model");
        Map<String, Object> rawOutputs = new LinkedHashMap<>();
        rawOutputs.put("probability", 0.2);

        Map<String, Object> result = ReflectionTestUtils.invokeMethod(
                service, "applyOutputTransforms", model, Collections.emptyMap(), rawOutputs);

        assertEquals(120.0, ((Number) result.get("probability")).doubleValue(), 0.000001);
        assertEquals(0.2, ((Number) rawOutputs.get("probability")).doubleValue(), 0.000001);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void outputTransformResolvesConstantArgumentByStableId() {
        RuleFunction function = function(7L, 1, twoParams());
        function.setImplType("SCRIPT");
        function.setImplScript("return probability * base;");
        RuleFunctionService functionService = new RuleFunctionService() {
            @Override
            public RuleFunction getById(Long id) {
                return function.getId().equals(id) ? function : null;
            }
        };
        ReflectionTestUtils.setField(functionService, "qlExpressEngine", new QLExpressEngine());
        ReflectionTestUtils.setField(functionService, "functionRegistrar", new FunctionRegistrar());

        RuleModelOutputField output = new RuleModelOutputField();
        output.setFieldName("probability");
        output.setTransformOperand(transformOperand(7L,
                "{\"kind\":\"REFERENCE\",\"refId\":100,\"refType\":\"MODEL_OUTPUT\",\"code\":\"risk_model.probability\"}",
                "{\"kind\":\"REFERENCE\",\"refId\":7,\"refType\":\"CONSTANT\",\"code\":\"BASE_SCORE\"}"));
        RuleModelService service = new RuleModelService();
        ReflectionTestUtils.setField(service, "ruleFunctionService", functionService);
        ReflectionTestUtils.setField(service, "variableService", new RuleVariableService() {
            @Override
            public Map<String, Object> buildRefConstantValueMap(Long projectId) {
                Map<String, Object> values = new LinkedHashMap<>();
                values.put("CONSTANT:7", 600D);
                return values;
            }
        });
        ReflectionTestUtils.setField(service, "outputFieldMapper", mapper(RuleModelOutputFieldMapper.class, (proxy, method, args) -> {
            if ("selectList".equals(method.getName())) return Collections.singletonList(output);
            return defaultValue(method.getReturnType());
        }));
        RuleModel model = model();
        model.setModelCode("risk_model");
        Map<String, Object> rawOutputs = new LinkedHashMap<>();
        rawOutputs.put("probability", 0.2);

        Map<String, Object> result = ReflectionTestUtils.invokeMethod(
                service, "applyOutputTransforms", model, Collections.emptyMap(), rawOutputs);

        assertEquals(120.0, ((Number) result.get("probability")).doubleValue(), 0.000001);
    }

    private static RuleModel model() {
        RuleModel model = new RuleModel();
        model.setId(1L);
        model.setProjectId(10L);
        model.setScope(RuleModelService.SCOPE_PROJECT);
        model.setCurrentVersion(1);
        model.setModelContent("content");
        model.setModelConfig("{}");
        return model;
    }

    private static RuleModelInputField inputField(String name, String type, Long varId, String refType, String scriptName) {
        RuleModelInputField field = new RuleModelInputField();
        field.setId(1L);
        field.setModelId(1L);
        field.setFieldName(name);
        field.setFieldLabel(name);
        field.setFieldType(type);
        field.setVarId(varId);
        field.setRefType(refType);
        field.setScriptName(scriptName);
        field.setStatus(1);
        return field;
    }

    private static RuleVariable variable(String type) {
        RuleVariable variable = new RuleVariable();
        variable.setId(10L);
        variable.setVarType(type);
        return variable;
    }

    private static RuleModelService outputUpdateService(RuleFunction function,
                                                        AtomicReference<RuleModelOutputField> updated) {
        RuleModelService service = new RuleModelService();
        ReflectionTestUtils.setField(service, "modelMapper", mapper(RuleModelMapper.class, (proxy, method, args) -> {
            if ("selectById".equals(method.getName())) return model();
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "outputFieldMapper", mapper(RuleModelOutputFieldMapper.class, (proxy, method, args) -> {
            if ("selectById".equals(method.getName())) {
                RuleModelOutputField existing = new RuleModelOutputField();
                existing.setId(100L);
                existing.setModelId(1L);
                existing.setFieldName("score");
                return existing;
            }
            if ("updateById".equals(method.getName())) {
                updated.set((RuleModelOutputField) args[0]);
                return 1;
            }
            return defaultValue(method.getReturnType());
        }));
        RuleFunctionService functionService = new RuleFunctionService() {
            @Override
            public RuleFunction getById(Long id) {
                return function != null && function.getId().equals(id) ? function : null;
            }
        };
        ReflectionTestUtils.setField(service, "ruleFunctionService", functionService);
        return service;
    }

    private static void assertInvalidTransform(String transformOperand, RuleFunction function, String message) {
        RuleModelService service = outputUpdateService(function, new AtomicReference<>());
        RuleModelOutputField request = new RuleModelOutputField();
        request.setFieldLabel("评分");
        ReflectionTestUtils.setField(request, "transformOperand", transformOperand);
        try {
            service.updateOutputField(100L, request);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(message));
            return;
        }
        throw new AssertionError("Expected invalid transform operand");
    }

    private static RuleFunction function(Long id, int status, String paramsJson) {
        RuleFunction function = new RuleFunction();
        function.setId(id);
        function.setProjectId(10L);
        function.setScope(RuleFunctionService.SCOPE_PROJECT);
        function.setStatus(status);
        function.setFuncCode("scoreByProbability");
        function.setFuncName("概率转评分");
        function.setParamsJson(paramsJson);
        return function;
    }

    private static String twoParams() {
        return "[{\"name\":\"probability\",\"type\":\"NUMBER\"},{\"name\":\"base\",\"type\":\"NUMBER\"}]";
    }

    private static String transformOperand(Long functionId, String firstArg, String secondArg) {
        return "{\"kind\":\"FUNCTION\",\"functionId\":" + functionId
                + ",\"functionCode\":\"scoreByProbability\",\"args\":[" + firstArg + "," + secondArg + "]}";
    }

    @SuppressWarnings("unchecked")
    private static <T> T mapper(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == Void.TYPE) return null;
        if (type == Boolean.TYPE) return false;
        if (type == Integer.TYPE || type == Long.TYPE || type == Short.TYPE || type == Byte.TYPE) return 0;
        if (type == Double.TYPE || type == Float.TYPE) return 0.0;
        return null;
    }
}
