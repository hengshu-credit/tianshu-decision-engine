package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.entity.RuleFunction;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
            assertEquals("JAVA", function.getImplType());
            assertFalse(function.getFuncName().isEmpty());
            assertFalse(function.getImplClass().isEmpty());
            assertFalse(function.getImplMethod().isEmpty());
            assertTrue(JSON.parseArray(function.getParamsJson()).size() >= 1);
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
        assertTrue(codeSet.contains("scoreByOddsPdo"));
        assertTrue(codeSet.contains("scoreByBadRatePdo"));
        assertTrue(definitions.size() >= 55);
    }
}
