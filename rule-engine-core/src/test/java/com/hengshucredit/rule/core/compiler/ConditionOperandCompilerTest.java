package com.hengshucredit.rule.core.compiler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ConditionOperandCompilerTest {

    @Test
    public void listOperatorsCompileListQueryWithTheSelectedLeftExpression() {
        JSONObject leaf = JSON.parseObject("{\"leftOperand\":{\"kind\":\"REFERENCE\",\"refId\":1,\"refType\":\"VARIABLE\",\"code\":\"request.mobile\"},"
                + "\"operator\":\"in_list\",\"rightOperand\":{\"kind\":\"LIST_QUERY\",\"listIds\":[10,20],"
                + "\"itemTypes\":[\"MOBILE\"],\"combinationMode\":\"ANY_FIELD_ANY_LIST\",\"matchMode\":\"IN_LIST\"}}");

        assertEquals("listMatch([request.mobile], [10, 20], \"ANY_FIELD_ANY_LIST\", \"IN_LIST\", [\"MOBILE\"])",
                ConditionOperandCompiler.compile(leaf, null));
        leaf.put("operator", "not_in_list");
        assertEquals("!listMatch([request.mobile], [10, 20], \"ANY_FIELD_ANY_LIST\", \"IN_LIST\", [\"MOBILE\"])",
                ConditionOperandCompiler.compile(leaf, null));
    }
}
