package com.hengshucredit.rule.server.service;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class RuleModelVarParserTest {

    private final RuleModelVarParser parser = new RuleModelVarParser();

    @Test
    public void ruleSetParsesConditionInputsAndActionOutputs() {
        String modelJson = "{"
                + "\"rules\":[{"
                + "\"conditionRoot\":{\"type\":\"leaf\",\"varCode\":\"score\",\"operator\":\">=\",\"value\":\"60\"},"
                + "\"actionData\":["
                + "{\"type\":\"assign\",\"target\":\"decision\",\"value\":\"score >= 80 ? \\\"PASS\\\" : \\\"REVIEW\\\"\"},"
                + "{\"type\":\"if-block\",\"branches\":[{\"type\":\"if\",\"condVar\":\"hitList\",\"condOp\":\"==\",\"condValue\":\"1\",\"actions\":[{\"type\":\"assign\",\"target\":\"hitResult\",\"value\":\"true\"}]}]},"
                + "{\"type\":\"switch-block\",\"matchVar\":\"grade\",\"cases\":[{\"value\":\"A\",\"actions\":[{\"type\":\"assign\",\"target\":\"gradeResult\",\"value\":\"\\\"A\\\"\"}]}]},"
                + "{\"type\":\"template-str\",\"target\":\"summary\",\"parts\":[{\"type\":\"expr\",\"content\":\"score\"}]}"
                + "]"
                + "}]"
                + "}";

        RuleModelVarParser.ParseResult result = parser.parse(modelJson, "RULE_SET");

        assertTrue(result.getInputCodes().contains("score"));
        assertTrue(result.getInputCodes().contains("hitList"));
        assertTrue(result.getInputCodes().contains("grade"));
        assertTrue(result.getOutputCodes().contains("decision"));
        assertTrue(result.getOutputCodes().contains("hitResult"));
        assertTrue(result.getOutputCodes().contains("gradeResult"));
        assertTrue(result.getOutputCodes().contains("summary"));
    }
}
