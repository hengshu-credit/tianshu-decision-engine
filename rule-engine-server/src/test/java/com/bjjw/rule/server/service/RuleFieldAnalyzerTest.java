package com.bjjw.rule.server.service;

import com.bjjw.rule.model.entity.RuleDefinitionInputField;
import com.bjjw.rule.model.entity.RuleDefinitionOutputField;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuleFieldAnalyzerTest {

    private final RuleFieldAnalyzer analyzer = new RuleFieldAnalyzer();

    @Test
    public void scriptExtractsInputsFromRightHandSide() {
        String json = "{"
                + "\"script\":\"riskScore = request.params.score + modelScore\\nresult.level = riskScore >= 60 ? \\\"PASS\\\" : \\\"REJECT\\\"\","
                + "\"scriptVarRefs\":["
                + "{\"refCode\":\"request.params.score\",\"varId\":10,\"refType\":\"DATA_OBJECT\"},"
                + "{\"refCode\":\"modelScore\",\"varId\":20,\"refType\":\"MODEL\"},"
                + "{\"refCode\":\"result.level\",\"varId\":30,\"refType\":\"DATA_OBJECT\"}"
                + "]"
                + "}";

        List<String> inputs = analyzer.extractInputFields(json, "SCRIPT").stream()
                .map(RuleDefinitionInputField::getScriptName)
                .collect(Collectors.toList());
        List<String> outputs = analyzer.extractOutputFields(json, "SCRIPT").stream()
                .map(RuleDefinitionOutputField::getScriptName)
                .collect(Collectors.toList());

        assertTrue(inputs.contains("request.params.score"));
        assertTrue(inputs.contains("modelScore"));
        assertFalse(inputs.contains("riskScore"));
        assertFalse(inputs.contains("result.level"));
        assertTrue(outputs.contains("riskScore"));
        assertTrue(outputs.contains("result.level"));
    }

    @Test
    public void graphExtractsConditionAndActionDataFields() {
        String json = "{"
                + "\"nodes\":["
                + "{\"id\":\"n1\",\"type\":\"decision\",\"conditionRoot\":{\"children\":[{\"varCode\":\"request.params.score\"}]}},"
                + "{\"id\":\"n2\",\"type\":\"task\",\"actionData\":["
                + "{\"type\":\"assign\",\"target\":\"internalScore\",\"value\":\"request.params.score + scoreOffset\"},"
                + "{\"type\":\"assign\",\"target\":\"result.level\",\"value\":\"request.params.score + scoreOffset\"},"
                + "{\"type\":\"if-block\",\"branches\":[{\"type\":\"if\",\"condVar\":\"creditModel\",\"condOp\":\"==\",\"condValue\":\"PASS\",\"actions\":[{\"type\":\"assign\",\"target\":\"result.hit\",\"value\":\"internalScore > 80\"}]}]}"
                + "]}"
                + "],"
                + "\"edges\":[{\"source\":\"n1\",\"target\":\"n2\",\"conditionExpression\":\"request.params.score >= threshold\"}]"
                + "}";

        List<String> inputs = analyzer.extractInputFields(json, "FLOW").stream()
                .map(RuleDefinitionInputField::getScriptName)
                .collect(Collectors.toList());
        List<String> outputs = analyzer.extractOutputFields(json, "FLOW").stream()
                .map(RuleDefinitionOutputField::getScriptName)
                .collect(Collectors.toList());

        assertTrue(inputs.contains("request.params.score"));
        assertTrue(inputs.contains("scoreOffset"));
        assertTrue(inputs.contains("creditModel"));
        assertTrue(inputs.contains("threshold"));
        assertFalse(inputs.contains("internalScore"));
        assertTrue(outputs.contains("result.level"));
        assertTrue(outputs.contains("result.hit"));
        assertTrue(outputs.contains("internalScore"));
    }
}
