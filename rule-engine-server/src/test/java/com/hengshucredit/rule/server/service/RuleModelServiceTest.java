package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleModelInputField;
import com.hengshucredit.rule.model.entity.RuleModelOutputField;
import com.hengshucredit.rule.model.entity.RuleVariable;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RuleModelServiceTest {

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
    public void applyMissingValuesOnlyReplacesEmptyInputsWhenConfigured() {
        RuleModelService service = new RuleModelService();
        RuleModelInputField age = inputField("age", "INTEGER", 10L, "VARIABLE", "age");
        age.setMissingValue("18");
        RuleModelInputField income = inputField("income", "DOUBLE", 11L, "VARIABLE", "income");
        ReflectionTestUtils.setField(service, "inputFieldMapper", mapper(RuleModelInputFieldMapper.class, (proxy, method, args) -> {
            if ("selectList".equals(method.getName())) return Arrays.asList(age, income);
            return defaultValue(method.getReturnType());
        }));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("age", "");
        params.put("income", null);

        Map<String, Object> result = ReflectionTestUtils.invokeMethod(service, "applyMissingValues", 1L, params);

        assertEquals(18, result.get("age"));
        assertTrue(result.containsKey("income"));
        assertEquals(null, result.get("income"));
    }

    private static RuleModel model() {
        RuleModel model = new RuleModel();
        model.setId(1L);
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
