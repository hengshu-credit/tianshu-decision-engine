package com.bjjw.rule.server.service;

import com.bjjw.rule.model.entity.RuleDefinition;
import com.bjjw.rule.model.entity.RuleDefinitionContent;
import com.bjjw.rule.server.mapper.RuleDefinitionContentMapper;
import com.bjjw.rule.server.mapper.RuleDefinitionMapper;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RuleCallCycleServiceTest {

    @Test
    public void collectRuleCallsFromActionDataAndScript() {
        List<RuleCallCycleService.RuleCallRef> refs = RuleCallCycleService.collectRuleCallRefs("{"
                + "\"nodes\":[{\"actionData\":[{\"type\":\"rule-call\",\"ruleId\":2,\"ruleCode\":\"B\"}],"
                + "\"qlExpressScript\":\"x = executeRuleField(\\\"C\\\", \\\"score\\\")\"}]"
                + "}");

        assertEquals(2, refs.size());
        RuleCallCycleService.RuleCallRef bRef = refs.stream()
                .filter(ref -> "B".equals(ref.getRuleCode()))
                .findFirst()
                .orElse(null);
        RuleCallCycleService.RuleCallRef cRef = refs.stream()
                .filter(ref -> "C".equals(ref.getRuleCode()))
                .findFirst()
                .orElse(null);
        assertEquals(Long.valueOf(2L), bRef.getRuleId());
        assertEquals("C", cRef.getRuleCode());
    }

    @Test
    public void validateNoCycleRejectsReachableRuleCallCycle() {
        RuleDefinition a = definition(1L, "A", "规则A");
        RuleDefinition b = definition(2L, "B", "规则B");
        RuleDefinitionContent contentA = content(1L, "{\"nodes\":[{\"actionData\":[{\"type\":\"rule-call\",\"ruleCode\":\"B\"}]}]}");
        RuleDefinitionContent contentB = content(2L, "{\"nodes\":[{\"actionData\":[{\"type\":\"rule-call\",\"ruleCode\":\"A\"}]}]}");

        RuleCallCycleService service = new RuleCallCycleService();
        ReflectionTestUtils.setField(service, "definitionMapper", mapper(RuleDefinitionMapper.class, (proxy, method, args) -> {
            if ("selectById".equals(method.getName())) return a;
            if ("selectList".equals(method.getName())) return Arrays.asList(a, b);
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "contentMapper", mapper(RuleDefinitionContentMapper.class, (proxy, method, args) -> {
            if ("selectList".equals(method.getName())) return Arrays.asList(contentA, contentB);
            return defaultValue(method.getReturnType());
        }));

        String error = service.validateNoCycle(1L, contentA.getModelJson());

        assertTrue(error, error.contains("规则调用存在环路"));
        assertTrue(error, error.contains("A"));
        assertTrue(error, error.contains("B"));
    }

    @Test
    public void validateNoCycleResolvesRuleCodeWithinSameProjectFirst() {
        RuleDefinition a = definition(1L, "A", "项目1规则A", 1L);
        RuleDefinition projectOneB = definition(2L, "B", "项目1规则B", 1L);
        RuleDefinition projectTwoB = definition(3L, "B", "项目2规则B", 2L);
        RuleDefinitionContent contentA = content(1L, "{\"nodes\":[{\"actionData\":[{\"type\":\"rule-call\",\"ruleCode\":\"B\"}]}]}");
        RuleDefinitionContent contentProjectOneB = content(2L, "{\"nodes\":[]}");
        RuleDefinitionContent contentProjectTwoB = content(3L, "{\"nodes\":[{\"actionData\":[{\"type\":\"rule-call\",\"ruleCode\":\"A\"}]}]}");

        RuleCallCycleService service = new RuleCallCycleService();
        ReflectionTestUtils.setField(service, "definitionMapper", mapper(RuleDefinitionMapper.class, (proxy, method, args) -> {
            if ("selectById".equals(method.getName())) return a;
            if ("selectList".equals(method.getName())) return Arrays.asList(a, projectOneB, projectTwoB);
            return defaultValue(method.getReturnType());
        }));
        ReflectionTestUtils.setField(service, "contentMapper", mapper(RuleDefinitionContentMapper.class, (proxy, method, args) -> {
            if ("selectList".equals(method.getName())) {
                return Arrays.asList(contentA, contentProjectOneB, contentProjectTwoB);
            }
            return defaultValue(method.getReturnType());
        }));

        assertNull(service.validateNoCycle(1L, contentA.getModelJson()));
    }

    private static RuleDefinition definition(Long id, String code, String name) {
        return definition(id, code, name, null);
    }

    private static RuleDefinition definition(Long id, String code, String name, Long projectId) {
        RuleDefinition definition = new RuleDefinition();
        definition.setId(id);
        definition.setProjectId(projectId);
        definition.setRuleCode(code);
        definition.setRuleName(name);
        return definition;
    }

    private static RuleDefinitionContent content(Long definitionId, String modelJson) {
        RuleDefinitionContent content = new RuleDefinitionContent();
        content.setDefinitionId(definitionId);
        content.setModelJson(modelJson);
        return content;
    }

    @SuppressWarnings("unchecked")
    private static <T> T mapper(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) return null;
        if (returnType == boolean.class) return false;
        if (returnType == void.class) return null;
        return 0;
    }
}
