package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RuleModelInputField;
import com.hengshucredit.rule.model.entity.RuleModelOutputField;
import com.hengshucredit.rule.model.entity.RuleVariable;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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

    @Test
    @SuppressWarnings("unchecked")
    public void listSourceExpansionKeepsSourceVariableAndDependency() throws Exception {
        RuleDefinitionInputField listHit = new RuleDefinitionInputField();
        listHit.setFieldName("riskHit");
        listHit.setScriptName("riskHit");
        listHit.setFieldType("INTEGER");
        listHit.setVarId(9L);
        listHit.setRefType("VARIABLE");

        Map<String, Object> meta = new java.util.HashMap<>();
        meta.put("varSource", "LIST");
        meta.put("sourceConfig", "{\"queryField\":\"mobile\"}");
        Map<String, Map<String, Object>> varMetaMap = new java.util.HashMap<>();
        varMetaMap.put("riskhit", meta);

        Method expand = RuleFieldAnalyzer.class.getDeclaredMethod("expandModelInputFields", List.class, Map.class);
        expand.setAccessible(true);
        List<RuleDefinitionInputField> fields = (List<RuleDefinitionInputField>) expand.invoke(
                analyzer, java.util.Collections.singletonList(listHit), varMetaMap);
        List<String> names = fields.stream()
                .map(RuleDefinitionInputField::getScriptName)
                .collect(Collectors.toList());

        assertTrue(names.contains("riskHit"));
        assertTrue(names.contains("mobile"));
        assertEquals(Long.valueOf(9), fields.stream()
                .filter(field -> "riskHit".equals(field.getScriptName()))
                .findFirst()
                .get()
                .getVarId());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void modelOutputExpandsToModelInputFieldsRecursively() throws Exception {
        RuleDefinitionInputField scoreF1 = new RuleDefinitionInputField();
        scoreF1.setScriptName("score_f1.score");
        scoreF1.setRefType("MODEL_OUTPUT");
        scoreF1.setVarId(130L);

        Map<String, Object> modelOutputMeta = new HashMap<>();
        modelOutputMeta.put("varSource", "MODEL_OUTPUT");
        modelOutputMeta.put("modelId", 1L);
        modelOutputMeta.put("sourceConfig", "");
        Map<String, Object> leafMeta = new HashMap<>();
        leafMeta.put("varSource", "DATA_OBJECT");
        leafMeta.put("id", 25L);
        leafMeta.put("refType", "DATA_OBJECT");
        Map<String, Map<String, Object>> varMetaMap = new HashMap<>();
        varMetaMap.put("score_f1.score", modelOutputMeta);
        varMetaMap.put("hybase_x115", leafMeta);

        RuleFieldAnalyzer analyzer = new TestableRuleFieldAnalyzer(Arrays.asList("HYBASE_X115"));

        Method expand = RuleFieldAnalyzer.class.getDeclaredMethod("expandModelInputFields", List.class, Map.class);
        expand.setAccessible(true);
        List<RuleDefinitionInputField> fields = (List<RuleDefinitionInputField>) expand.invoke(
                analyzer, java.util.Collections.singletonList(scoreF1), varMetaMap);
        List<String> names = fields.stream()
                .map(RuleDefinitionInputField::getScriptName)
                .collect(Collectors.toList());

        assertTrue("模型输出应保留为输入字段", names.contains("score_f1.score"));
        assertTrue("模型输出应穿透到最底层输入特征", names.contains("HYBASE_X115"));
        RuleDefinitionInputField leaf = fields.stream()
                .filter(f -> "HYBASE_X115".equals(f.getScriptName())).findFirst().get();
        assertEquals("底层字段应携带引擎变量关联", Long.valueOf(25), leaf.getVarId());
        assertEquals("DATA_OBJECT", leaf.getRefType());
    }

    /** 覆盖 loadModelInputFields，避免依赖 MyBatis mapper 实现 */
    private static class TestableRuleFieldAnalyzer extends RuleFieldAnalyzer {
        private final List<String> modelInputNames;

        private TestableRuleFieldAnalyzer(List<String> modelInputNames) {
            this.modelInputNames = modelInputNames;
        }

        @Override
        protected List<RuleModelInputField> loadModelInputFields(Long modelId) {
            List<RuleModelInputField> fields = new ArrayList<>();
            for (String name : modelInputNames) {
                RuleModelInputField field = new RuleModelInputField();
                field.setModelId(modelId);
                field.setVarId(25L);
                field.setRefType("DATA_OBJECT");
                field.setFieldName(name);
                field.setScriptName(name);
                field.setFieldType("DOUBLE");
                fields.add(field);
            }
            return fields;
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void sourceVariablesExpandToUnderlyingInputs() throws Exception {
        setField(analyzer, "variableSourceResolver", new VariableSourceResolver());

        Map<String, Map<String, Object>> varMetaMap = new HashMap<>();
        Map<String, Object> dbMeta = new HashMap<>();
        dbMeta.put("varSource", "DB");
        dbMeta.put("sourceConfig", "{\"sql\":\"select 1\",\"params\":[\"$.idcard_no\"]}");
        Map<String, Object> apiMeta = new HashMap<>();
        apiMeta.put("varSource", "API");
        apiMeta.put("sourceConfig", "{\"apiConfigId\":7,\"paramMapping\":{\"idNo\":\"$.idcard_no\"}}");
        Map<String, Object> computedMeta = new HashMap<>();
        computedMeta.put("varSource", "COMPUTED");
        computedMeta.put("sourceConfig", "{\"expression\":\"idcard_no + age\"}");
        varMetaMap.put("db_score", dbMeta);
        varMetaMap.put("api_score", apiMeta);
        varMetaMap.put("computed_score", computedMeta);
        varMetaMap.put("idcard_no", leafMeta("idcard_no", "INPUT", 6L));
        varMetaMap.put("age", leafMeta("age", "INPUT", 37L));

        Method expand = RuleFieldAnalyzer.class.getDeclaredMethod("expandModelInputFields", List.class, Map.class);
        expand.setAccessible(true);

        RuleDefinitionInputField dbField = inputField("db_score", "VARIABLE", "DB", 100L);
        List<RuleDefinitionInputField> dbResult = (List<RuleDefinitionInputField>) expand.invoke(
                analyzer, java.util.Collections.singletonList(dbField), varMetaMap);
        assertTrue("DB 变量应穿透到其参数依赖", names(dbResult).contains("idcard_no"));

        RuleDefinitionInputField apiField = inputField("api_score", "VARIABLE", "API", 101L);
        List<RuleDefinitionInputField> apiResult = (List<RuleDefinitionInputField>) expand.invoke(
                analyzer, java.util.Collections.singletonList(apiField), varMetaMap);
        assertTrue("API 变量应穿透到请求映射依赖", names(apiResult).contains("idcard_no"));

        RuleDefinitionInputField computedField = inputField("computed_score", "VARIABLE", "COMPUTED", 102L);
        List<RuleDefinitionInputField> computedResult = (List<RuleDefinitionInputField>) expand.invoke(
                analyzer, java.util.Collections.singletonList(computedField), varMetaMap);
        assertTrue("计算变量应穿透到表达式引用变量", names(computedResult).contains("idcard_no"));
        assertTrue("计算变量应穿透到表达式引用变量", names(computedResult).contains("age"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void cyclicDependenciesDoNotCauseInfiniteRecursion() throws Exception {
        setField(analyzer, "variableSourceResolver", new VariableSourceResolver());

        Map<String, Map<String, Object>> varMetaMap = new HashMap<>();
        Map<String, Object> aMeta = new HashMap<>();
        aMeta.put("varSource", "DB");
        aMeta.put("sourceConfig", "{\"params\":[\"$.b_var\"]}");
        Map<String, Object> bMeta = new HashMap<>();
        bMeta.put("varSource", "DB");
        bMeta.put("sourceConfig", "{\"params\":[\"$.a_var\"]}");
        varMetaMap.put("a_var", aMeta);
        varMetaMap.put("b_var", bMeta);

        Method expand = RuleFieldAnalyzer.class.getDeclaredMethod("expandModelInputFields", List.class, Map.class);
        expand.setAccessible(true);
        RuleDefinitionInputField aField = inputField("a_var", "VARIABLE", "DB", 1L);
        List<RuleDefinitionInputField> result = (List<RuleDefinitionInputField>) expand.invoke(
                analyzer, java.util.Collections.singletonList(aField), varMetaMap);
        List<String> names = names(result);
        assertTrue(names.contains("a_var"));
        assertTrue(names.contains("b_var"));
    }

    private static RuleDefinitionInputField inputField(String scriptName, String refType, String varSource, Long varId) {
        RuleDefinitionInputField field = new RuleDefinitionInputField();
        field.setScriptName(scriptName);
        field.setFieldName(scriptName);
        field.setRefType(refType);
        field.setVarId(varId);
        field.setFieldType("STRING");
        field.setStatus(1);
        return field;
    }

    private static Map<String, Object> leafMeta(String scriptName, String varSource, Long id) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("varSource", varSource);
        meta.put("id", id);
        meta.put("refType", varSource);
        return meta;
    }

    private static List<String> names(List<RuleDefinitionInputField> fields) {
        return fields.stream().map(RuleDefinitionInputField::getScriptName).collect(Collectors.toList());
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
