package com.hengshucredit.rule.server.derived;

import com.alibaba.fastjson.JSON;
import org.junit.Test;

import static org.junit.Assert.assertThrows;

public class DerivedVariableConfigTest {
    @Test
    public void rejectsIncompleteAccessOperand() {
        var config = JSON.parseObject("{\"mode\":\"EXPRESSION\",\"expression\":{\"kind\":\"ACCESS\",\"accessType\":\"KEY\"}}");
        assertThrows(IllegalArgumentException.class, () -> DerivedVariableConfig.validate(config));
    }

    @Test
    public void rejectsOperationTermWithoutOperandOrOperator() {
        var config = JSON.parseObject("{\"mode\":\"EXPRESSION\",\"expression\":{\"kind\":\"OPERATION\",\"terms\":[{\"operand\":{\"kind\":\"LITERAL\",\"valueType\":\"NUMBER\",\"value\":1}},{\"operator\":\"+\"}]}}");
        assertThrows(IllegalArgumentException.class, () -> DerivedVariableConfig.validate(config));
    }
}
