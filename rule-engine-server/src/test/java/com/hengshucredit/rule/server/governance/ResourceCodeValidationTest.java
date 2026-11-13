package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.server.service.ReservedContentCode;
import org.junit.Test;

import static org.junit.Assert.*;

public class ResourceCodeValidationTest {
    @Test
    public void distinguishesLanguageAndEngineNamesWithoutMatchingPrefixes() {
        for (String code : new String[]{" Return ", "class", "not_in", "select", "DENSE_RANK"})
            assertTrue(code, ReservedContentCode.error(code).contains("语言关键字"));
        for (String code : new String[]{"sUm", "decision_PASS", "builtin_email", "invokeFunctionById", "_dim_3_4"})
            assertTrue(code, ReservedContentCode.error(code).contains("引擎内置编码"));
        for (String code : new String[]{"", "name", "mobile_no", "ReTuRnCode", "Customer_Sum", "_dim_business"})
            assertNull(code, ReservedContentCode.error(code));
    }

    @Test
    public void checksOnlyDeclarationsInNestedSnapshots() {
        var object = ResourceSnapshot.ofJson("""
                {"objectCode":"Customer","fields":[{"varCode":"mobile_no","scriptName":"ZERO",
                "refVariableId":1,"options":[{"varCode":"return"}]}],"sourceContent":{"varCode":"return"}}
                """);
        var issues = ResourceCodeValidation.inspect("DATA_OBJECT", object, false);
        assertEquals(1, issues.size());
        assertEquals("$.fields[0].scriptName", issues.get(0).path());
        assertThrows(IllegalArgumentException.class, () -> ResourceCodeValidation.requireValid(issues));
        var group = ResourceSnapshot.ofJson("""
                {"experimentCode":"business_group","groups":[{"groupCode":"sum","ruleCode":"return"}]}
                """);
        assertEquals("$.groups[0].groupCode", ResourceCodeValidation.inspect("EXPERIMENT", group, false).get(0).path());
    }

    @Test
    public void keepsExistingBuiltinFunctionParametersAndRejectsNewCollisions() {
        var function = ResourceSnapshot.ofJson("""
                {"funcCode":"numBetween","paramsJson":[{"name":"min"},{"name":"max"},{"name":"class"}]}
                """);
        assertEquals(4, ResourceCodeValidation.inspect("FUNCTION", function, false).size());
        var existing = ResourceCodeValidation.inspect("FUNCTION", function, true);
        assertEquals(1, existing.size());
        assertEquals("$.paramsJson[2].name", existing.get(0).path());
    }

    @Test
    public void validatesDesignerDeclarationsWithoutInspectingScriptReferences() {
        String flow = """
                {"logicflow":{"nodes":[{"properties":{"nodeCode":"while","actionData":[{"varCode":"ONE"}]}}]}}
                """;
        assertEquals("$.logicflow.nodes[0].properties.nodeCode", ResourceCodeValidation.designer("FLOW", flow).get(0).path());
        assertEquals("$.rules[0].ruleCode", ResourceCodeValidation.designer("RULE_SET",
                "{\"rules\":[{\"ruleCode\":\"sum\"}]}").get(0).path());
        assertTrue(ResourceCodeValidation.designer("SCRIPT", flow).isEmpty());
    }
}
