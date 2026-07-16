package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.entity.RuleFunction;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BuiltinFunctionCatalogTest {

    @Test
    public void builtinFunctionDefinitionsAreValidForFunctionManagement() {
        List<RuleFunction> definitions = BuiltinFunctionCatalog.definitions();
        Set<String> codes = new HashSet<>();

        for (RuleFunction function : definitions) {
            assertTrue("duplicate funcCode: " + function.getFuncCode(), codes.add(function.getFuncCode()));
            assertEquals(RuleFunctionService.SCOPE_GLOBAL, function.getScope());
            assertEquals(Long.valueOf(0L), function.getProjectId());
            assertTrue("JAVA".equals(function.getImplType()) || "BEAN".equals(function.getImplType()));
            assertFalse(function.getFuncName().isEmpty());
            if ("JAVA".equals(function.getImplType())) {
                assertFalse(function.getImplClass().isEmpty());
            } else {
                assertEquals("ruleListFunctions", function.getImplBeanName());
            }
            assertFalse(function.getImplMethod().isEmpty());
            assertNotNull(function.getParamsJson());
            assertTrue(function.getParamsJson(), function.getParamsJson().startsWith("["));
            JSON.parseArray(function.getParamsJson());
        }

        Set<String> codeSet = definitions.stream().map(RuleFunction::getFuncCode).collect(Collectors.toSet());
        assertTrue(codeSet.contains("sum"));
        assertTrue(codeSet.contains("numAdd"));
        assertTrue(codeSet.contains("strRegexExtract"));
        assertTrue(codeSet.contains("arrDistinct"));
        assertTrue(codeSet.contains("jsonSum"));
        assertTrue(codeSet.contains("objGet"));
        assertTrue(codeSet.contains("dateAdd"));
        assertTrue(codeSet.contains("dateDiff"));
        assertTrue(codeSet.contains("idCardBirthDate"));
        assertTrue(codeSet.contains("idCardAge"));
        assertTrue(codeSet.contains("scoreByProbability"));
        assertTrue(codeSet.contains("scoreByOddsPdo"));
        assertTrue(codeSet.contains("scoreByBadRatePdo"));
        assertTrue(codeSet.contains("currentDate"));
        assertTrue(codeSet.contains("currentDateTime"));
        assertTrue(codeSet.contains("dateAddMonths"));
        assertTrue(codeSet.contains("dateAddParts"));
        assertTrue(codeSet.contains("dateSubParts"));
        assertTrue(codeSet.contains("dateYear"));
        assertTrue(codeSet.contains("dateDaysInSpecifiedMonths"));
        assertTrue(codeSet.contains("dateDaysOutsideSpecifiedMonths"));
        assertTrue(codeSet.contains("strSubstring"));
        assertTrue(codeSet.contains("strUpper"));
        assertTrue(codeSet.contains("md5"));
        assertTrue(codeSet.contains("sha1"));
        assertTrue(codeSet.contains("sha256"));
        assertTrue(codeSet.contains("hmacSha256"));
        assertTrue(codeSet.contains("numCeil"));
        assertTrue(codeSet.contains("numLog10"));
        assertTrue(codeSet.contains("randomInt"));
        assertTrue(codeSet.contains("randomDecimal"));
        assertTrue(codeSet.contains("arrSortBy"));
        assertTrue(codeSet.contains("arrPluck"));
        assertTrue(codeSet.contains("mapPut"));
        assertTrue(codeSet.contains("newMap"));
        assertTrue(codeSet.contains("newList"));
        assertTrue(codeSet.contains("newLike"));
        assertTrue(codeSet.contains("toNumberValue"));
        assertTrue(codeSet.contains("currentRule"));
        assertTrue(codeSet.contains("currentRuleName"));
        assertTrue(codeSet.contains("currentMatchedConditions"));
        assertTrue(codeSet.contains("isInLists"));
        assertTrue(codeSet.contains("isInListsNumber"));
        assertTrue(codeSet.contains("listMatch"));
        assertTrue(codeSet.contains("listMatchNumber"));
        assertFalse(codeSet.contains("newInstanceByClassName"));
        assertTrue(definitions.size() >= 105);
    }

    @Test
    public void builtinFunctionExamplesExecuteThroughFunctionManagement() {
        for (RuleFunction function : BuiltinFunctionCatalog.definitions()) {
            if ("BEAN".equals(function.getImplType())) continue;
            RuleFunctionService service = serviceWithFunction(function);
            Map<String, Object> params = exampleParams(function.getParamsJson());

            Map<String, Object> result = service.testFunction(1L, params);

            assertEquals(function.getFuncCode() + ": " + result.get("errorMessage"), true, result.get("success"));
        }
    }

    private static RuleFunctionService serviceWithFunction(RuleFunction function) {
        RuleFunctionService service = new RuleFunctionService() {
            @Override
            public RuleFunction getById(Long id) {
                return function;
            }
        };
        ReflectionTestUtils.setField(service, "qlExpressEngine", new QLExpressEngine());
        ReflectionTestUtils.setField(service, "functionRegistrar", new FunctionRegistrar());
        return service;
    }

    private static Map<String, Object> exampleParams(String paramsJson) {
        Map<String, Object> params = new LinkedHashMap<>();
        JSONArray definitions = JSON.parseArray(paramsJson);
        for (int i = 0; i < definitions.size(); i++) {
            JSONObject definition = definitions.getJSONObject(i);
            params.put(definition.getString("name"), definition.get("example"));
        }
        return params;
    }
}
