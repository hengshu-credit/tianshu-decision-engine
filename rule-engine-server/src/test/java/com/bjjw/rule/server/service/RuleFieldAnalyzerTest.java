package com.bjjw.rule.server.service;

import com.bjjw.rule.model.entity.RuleDefinitionInputField;
import com.bjjw.rule.model.entity.RuleDefinitionOutputField;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
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
    public void scriptExplicitResultMapExtractsReturnedKeysAsOutputs() {
        String json = "{"
                + "\"script\":\"score = request.score + offset\\n_result = {\\\"riskScore\\\": score, \\\"riskLevel\\\": level}\\n_result\","
                + "\"scriptVarRefs\":["
                + "{\"refCode\":\"request.score\",\"varId\":10,\"refType\":\"DATA_OBJECT\"},"
                + "{\"refCode\":\"offset\",\"varId\":20,\"refType\":\"VARIABLE\"}"
                + "]"
                + "}";

        List<String> inputs = analyzer.extractInputFields(json, "SCRIPT").stream()
                .map(RuleDefinitionInputField::getScriptName)
                .collect(Collectors.toList());
        List<String> outputs = analyzer.extractOutputFields(json, "SCRIPT").stream()
                .map(RuleDefinitionOutputField::getScriptName)
                .collect(Collectors.toList());

        assertTrue(inputs.contains("request.score"));
        assertTrue(inputs.contains("offset"));
        assertEquals(java.util.Arrays.asList("riskScore", "riskLevel"), outputs);
        assertFalse(outputs.contains("_result"));
        assertFalse(outputs.contains("score"));
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

    @Test
    public void graphExtractsConditionConfigFieldsFromEdges() {
        String json = "{"
                + "\"nodes\":[{\"id\":\"n1\",\"type\":\"decision\"},{\"id\":\"n2\",\"type\":\"task\",\"actionData\":[{\"type\":\"assign\",\"target\":\"result.hit\",\"value\":\"1\"}]}],"
                + "\"edges\":[{\"source\":\"n1\",\"target\":\"n2\",\"conditionConfig\":{"
                + "\"type\":\"group\",\"op\":\"AND\",\"children\":["
                + "{\"type\":\"leaf\",\"varCode\":\"request.age\",\"operator\":\">=\",\"valueKind\":\"CONST\",\"value\":\"18\"},"
                + "{\"type\":\"leaf\",\"varCode\":\"request.city\",\"operator\":\"==\",\"valueKind\":\"VAR\",\"value\":\"targetCity\"}"
                + "]}}]"
                + "}";

        List<String> inputs = analyzer.extractInputFields(json, "FLOW").stream()
                .map(RuleDefinitionInputField::getScriptName)
                .collect(Collectors.toList());

        assertTrue(inputs.contains("request.age"));
        assertTrue(inputs.contains("request.city"));
        assertTrue(inputs.contains("targetCity"));
    }

    @Test
    public void tableAndCrossDoNotTreatOutputsAsInputs() {
        String table = "{"
                + "\"conditions\":[{\"varCode\":\"age\"}],"
                + "\"actions\":[{\"varCode\":\"riskLevel\"}],"
                + "\"rules\":[{\"conditionRoot\":{\"type\":\"leaf\",\"varCode\":\"income\",\"operator\":\">\",\"value\":\"100\"},\"actions\":[{\"varCode\":\"approveFlag\"}]}]"
                + "}";
        List<String> tableInputs = analyzer.extractInputFields(table, "TABLE").stream()
                .map(RuleDefinitionInputField::getScriptName)
                .collect(Collectors.toList());

        assertTrue(tableInputs.contains("age"));
        assertTrue(tableInputs.contains("income"));
        assertFalse(tableInputs.contains("riskLevel"));
        assertFalse(tableInputs.contains("approveFlag"));

        String cross = "{"
                + "\"rowVar\":{\"varCode\":\"taxpayerType\"},"
                + "\"colVar\":{\"varCode\":\"goodsCategory\"},"
                + "\"resultVar\":{\"varCode\":\"taxRate\"}"
                + "}";
        List<String> crossInputs = analyzer.extractInputFields(cross, "CROSS").stream()
                .map(RuleDefinitionInputField::getScriptName)
                .collect(Collectors.toList());

        assertEquals(java.util.Arrays.asList("taxpayerType", "goodsCategory"), crossInputs);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void fieldLevelActionRefsAreAppliedByFieldName() throws Exception {
        String json = "{"
                + "\"nodes\":[{\"id\":\"n1\",\"type\":\"task\",\"actionData\":["
                + "{\"type\":\"ternary\",\"target\":\"decision\",\"_targetVarId\":200,\"_targetRefType\":\"VARIABLE\","
                + "\"condVar\":\"score\",\"_condVarId\":100,\"_condVarRefType\":\"VARIABLE\",\"condOp\":\">=\",\"condValue\":\"60\"},"
                + "{\"type\":\"in-check\",\"target\":\"hit\",\"_targetVarId\":300,\"_targetRefType\":\"DATA_OBJECT\","
                + "\"checkVar\":\"riskLevel\",\"_checkVarId\":400,\"_checkVarRefType\":\"VARIABLE\",\"inValues\":[\"HIGH\"]}"
                + "]}]}";

        Method collectRefs = RuleFieldAnalyzer.class.getDeclaredMethod("collectExplicitRefs", String.class);
        collectRefs.setAccessible(true);
        Map<String, Object> refs = (Map<String, Object>) collectRefs.invoke(analyzer, json);

        Method applyInputRef = RuleFieldAnalyzer.class.getDeclaredMethod("applyExplicitRef",
                RuleDefinitionInputField.class, Map.class);
        applyInputRef.setAccessible(true);
        Method applyOutputRef = RuleFieldAnalyzer.class.getDeclaredMethod("applyExplicitRef",
                RuleDefinitionOutputField.class, Map.class);
        applyOutputRef.setAccessible(true);

        RuleDefinitionInputField score = new RuleDefinitionInputField();
        score.setScriptName("score");
        applyInputRef.invoke(analyzer, score, refs);
        assertEquals(Long.valueOf(100), score.getVarId());
        assertEquals("VARIABLE", score.getRefType());

        RuleDefinitionOutputField decision = new RuleDefinitionOutputField();
        decision.setScriptName("decision");
        applyOutputRef.invoke(analyzer, decision, refs);
        assertEquals(Long.valueOf(200), decision.getVarId());
        assertEquals("VARIABLE", decision.getRefType());

        RuleDefinitionInputField riskLevel = new RuleDefinitionInputField();
        riskLevel.setScriptName("riskLevel");
        applyInputRef.invoke(analyzer, riskLevel, refs);
        assertEquals(Long.valueOf(400), riskLevel.getVarId());

        RuleDefinitionOutputField hit = new RuleDefinitionOutputField();
        hit.setScriptName("hit");
        applyOutputRef.invoke(analyzer, hit, refs);
        assertEquals(Long.valueOf(300), hit.getVarId());
        assertEquals("DATA_OBJECT", hit.getRefType());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void rightSideConditionRefsAreAppliedByFieldName() throws Exception {
        String json = "{"
                + "\"rules\":[{\"conditionRoot\":{\"type\":\"leaf\",\"varCode\":\"age\",\"_varId\":1,"
                + "\"valueKind\":\"VAR\",\"value\":\"minAge\",\"_rightVarId\":2,\"_rightRefType\":\"VARIABLE\"}}]"
                + "}";

        Method collectRefs = RuleFieldAnalyzer.class.getDeclaredMethod("collectExplicitRefs", String.class);
        collectRefs.setAccessible(true);
        Map<String, Object> refs = (Map<String, Object>) collectRefs.invoke(analyzer, json);
        Method applyInputRef = RuleFieldAnalyzer.class.getDeclaredMethod("applyExplicitRef",
                RuleDefinitionInputField.class, Map.class);
        applyInputRef.setAccessible(true);

        RuleDefinitionInputField minAge = new RuleDefinitionInputField();
        minAge.setScriptName("minAge");
        applyInputRef.invoke(analyzer, minAge, refs);

        assertEquals(Long.valueOf(2), minAge.getVarId());
        assertEquals("VARIABLE", minAge.getRefType());
    }
}
