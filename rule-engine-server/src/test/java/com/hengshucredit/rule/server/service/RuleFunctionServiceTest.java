package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.core.engine.RuntimeContextBridge;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.server.mapper.RuleFunctionMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.After;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RuleFunctionServiceTest {

    @After
    public void clearRuntimeContext() {
        RuntimeContextBridge.clear();
    }

    @Test
    public void testFunctionExecutesScriptFunctionWithContextParams() {
        RuleFunction function = scriptFunction("roundTax", "[{\"name\":\"amount\",\"type\":\"NUMBER\"}]",
                "scaled = amount * 100 + 0.5; return (scaled - scaled % 1) / 100;");
        function.setId(1L);

        RuleFunctionService service = serviceWithFunction(function);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("amount", 12.345);

        Map<String, Object> result = service.testFunction(1L, params);

        assertEquals(true, result.get("success"));
        assertEquals("roundTax", result.get("functionCode"));
        assertEquals(12.35, ((Number) result.get("result")).doubleValue(), 0.000001);
    }

    @Test
    public void testFunctionRejectsInvalidParamName() {
        RuleFunction function = scriptFunction("roundTax", "[{\"name\":\"bad-name\",\"type\":\"NUMBER\"}]",
                "return 1;");
        function.setId(1L);

        RuleFunctionService service = serviceWithFunction(function);

        try {
            service.testFunction(1L, new LinkedHashMap<>());
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("函数参数名"));
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException");
    }

    @Test
    public void testFunctionExecutesCoreJavaJsonFunction() {
        RuleFunction function = javaFunction("jsonSum",
                "[{\"name\":\"json\",\"type\":\"OBJECT\"},{\"name\":\"path\",\"type\":\"STRING\"}]",
                "com.hengshucredit.rule.core.function.DecisionBuiltinFunctions",
                "jsonSum");
        function.setId(2L);

        RuleFunctionService service = serviceWithFunction(function);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("json", sampleJson());
        params.put("path", "$.orders[?(@.status='SUCCESS')].amount");

        Map<String, Object> result = service.testFunction(2L, params);

        assertEquals(true, result.get("success"));
        assertEquals("jsonSum", result.get("functionCode"));
        assertEquals(20.0, ((Number) result.get("result")).doubleValue(), 0.000001);
    }

    @Test
    public void invokeFunctionBindsOrderedArguments() {
        RuleFunction function = scriptFunction("roundTax", "[{\"name\":\"amount\",\"type\":\"NUMBER\"}]",
                "scaled = amount * 100 + 0.5; return (scaled - scaled % 1) / 100;");
        function.setId(1L);
        function.setStatus(1);
        RuleFunctionService service = serviceWithFunction(function);

        Object result = ReflectionTestUtils.invokeMethod(service, "invoke", 1L, Arrays.asList(12.345));

        assertEquals(12.35, ((Number) result).doubleValue(), 0.000001);
    }

    @Test
    public void invokeFunctionInsideRuleDoesNotTreatOuterConstantsAsInnerParameters() {
        RuleFunction function = javaFunction("imageToBase64",
                "[{\"name\":\"image\",\"type\":\"STRING\"},{\"name\":\"timeoutMs\",\"type\":\"NUMBER\"}]",
                "com.hengshucredit.rule.core.function.ImageInputFunctions",
                "imageToBase64");
        function.setId(2L);
        function.setStatus(1);
        RuleFunctionService service = serviceWithFunction(function);
        RuntimeContextBridge.registerConstant("NULL_NUMBER", null);

        Object result = ReflectionTestUtils.invokeMethod(service, "invoke", 2L,
                Arrays.asList("AQID", 10000d));

        assertEquals("AQID", result);
        try {
            RuntimeContextBridge.setValue("NULL_NUMBER", 1);
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("NULL_NUMBER"));
            return;
        }
        throw new AssertionError("Expected outer constant protection to remain active");
    }

    @Test
    public void invokeFunctionRejectsArgumentCountAndDisabledFunction() {
        RuleFunction function = scriptFunction("roundTax", "[{\"name\":\"amount\",\"type\":\"NUMBER\"}]", "return amount;");
        function.setId(1L);
        function.setStatus(1);
        RuleFunctionService service = serviceWithFunction(function);
        try {
            ReflectionTestUtils.invokeMethod(service, "invoke", 1L, Arrays.asList(1, 2));
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("参数数量"));
        }

        function.setStatus(0);
        try {
            ReflectionTestUtils.invokeMethod(service, "invoke", 1L, Arrays.asList(1));
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("停用"));
            return;
        }
        throw new AssertionError("Expected disabled function error");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void managementPagesOrderByLatestUpdateThenId() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), RuleFunction.class);
        List<String> sqlSegments = new ArrayList<>();
        RuleFunctionMapper mapper = (RuleFunctionMapper) Proxy.newProxyInstance(
                RuleFunctionMapper.class.getClassLoader(), new Class<?>[]{RuleFunctionMapper.class},
                (proxy, method, args) -> {
                    if ("selectPage".equals(method.getName())) {
                        sqlSegments.add(((LambdaQueryWrapper<RuleFunction>) args[1]).getSqlSegment());
                        return args[0];
                    }
                    return null;
                });
        RuleFunctionService service = new RuleFunctionService();
        ReflectionTestUtils.setField(service, "functionMapper", mapper);

        service.pageByProject(null, 1, 10, null, null, null, null, null, null);
        service.pageAll(1, 10, null, null, null, null, null, null, null);

        assertEquals(2, sqlSegments.size());
        for (String sqlSegment : sqlSegments) {
            assertLatestUpdateOrder(sqlSegment);
        }
    }

    private static RuleFunctionService serviceWithFunction(RuleFunction function) {
        RuleFunctionService service = new RuleFunctionService() {
            @Override
            public RuleFunction getById(Long id) {
                return function.getId().equals(id) ? function : null;
            }
        };
        ReflectionTestUtils.setField(service, "qlExpressEngine", new QLExpressEngine());
        ReflectionTestUtils.setField(service, "functionRegistrar", new FunctionRegistrar());
        return service;
    }

    private static RuleFunction scriptFunction(String funcCode, String paramsJson, String implScript) {
        RuleFunction function = new RuleFunction();
        function.setFuncCode(funcCode);
        function.setFuncName(funcCode);
        function.setImplType("SCRIPT");
        function.setParamsJson(paramsJson);
        function.setImplScript(implScript);
        return function;
    }

    private static RuleFunction javaFunction(String funcCode, String paramsJson, String implClass, String implMethod) {
        RuleFunction function = new RuleFunction();
        function.setFuncCode(funcCode);
        function.setFuncName(funcCode);
        function.setImplType("JAVA");
        function.setParamsJson(paramsJson);
        function.setImplClass(implClass);
        function.setImplMethod(implMethod);
        return function;
    }

    private static Map<String, Object> sampleJson() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("orders", Arrays.asList(
                order("SUCCESS", 12.5),
                order("FAIL", 3.0),
                order("SUCCESS", 7.5)
        ));
        return root;
    }

    private static Map<String, Object> order(String status, double amount) {
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("status", status);
        order.put("amount", amount);
        return order;
    }

    private static void assertLatestUpdateOrder(String sqlSegment) {
        String normalized = sqlSegment.replace("`", "").replace(" ", "").toLowerCase();
        assertTrue(sqlSegment, normalized.contains("orderbyupdate_timedesc,iddesc")
                || normalized.contains("orderbyupdatetimedesc,iddesc"));
    }
}
