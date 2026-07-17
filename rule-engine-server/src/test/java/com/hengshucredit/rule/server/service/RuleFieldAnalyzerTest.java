package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleModelInputField;
import com.hengshucredit.rule.model.entity.RuleModelOutputField;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDataObjectMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionInputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionOutputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleModelMapper;
import com.hengshucredit.rule.server.mapper.RuleModelOutputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleVariableMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuleFieldAnalyzerTest {

    private final RuleFieldAnalyzer analyzer = new RuleFieldAnalyzer();

    @Test
    @SuppressWarnings("unchecked")
    public void modelMetadataLookupDoesNotReadLargeModelContent() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), RuleModel.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), RuleModelOutputField.class);
        AtomicReference<String> selectedColumns = new AtomicReference<>();
        RuleModel model = new RuleModel();
        model.setId(101L);
        model.setModelCode("buffalo_det_face");
        model.setModelName("Buffalo detector");
        RuleModelOutputField output = new RuleModelOutputField();
        output.setId(201L);
        output.setModelId(101L);
        output.setFieldName("faces");
        output.setScriptName("buffalo_det_face_faces");
        output.setFieldType("LIST");

        setField(analyzer, "ruleVariableMapper", mapper(RuleVariableMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName()) ? Collections.emptyList() : null));
        setField(analyzer, "dataObjectMapper", mapper(RuleDataObjectMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName()) ? Collections.emptyList() : null));
        setField(analyzer, "dataObjectFieldMapper", mapper(RuleDataObjectFieldMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName()) ? Collections.emptyList() : null));
        setField(analyzer, "modelOutputFieldMapper", mapper(RuleModelOutputFieldMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? Collections.singletonList(output) : null));
        setField(analyzer, "modelMapper", mapper(RuleModelMapper.class, (proxy, method, args) -> {
            if ("selectList".equals(method.getName())) {
                LambdaQueryWrapper<RuleModel> wrapper = (LambdaQueryWrapper<RuleModel>) args[0];
                selectedColumns.set(wrapper.getSqlSelect());
                return Collections.singletonList(model);
            }
            return null;
        }));

        Method build = RuleFieldAnalyzer.class.getDeclaredMethod("buildVarMetaMap", Long.class);
        build.setAccessible(true);
        Map<String, Map<String, Object>> metadata =
                (Map<String, Map<String, Object>>) build.invoke(analyzer, 1L);

        assertEquals(Long.valueOf(101L), metadata.get("buffalo_det_face").get("id"));
        assertTrue(metadata.containsKey("buffalo_det_face.faces"));
        assertEquals(Long.valueOf(201L), metadata.get("buffalo_det_face.faces").get("id"));
        assertFalse(metadata.containsKey("buffalo_det_face.buffalo_det_face_faces"));
        assertTrue(selectedColumns.get() != null && !selectedColumns.get().isEmpty());
        assertFalse(selectedColumns.get().contains("model_content"));
    }

    @Test
    public void unifiedOperandsContributeInputAndOutputDependencies() {
        String json = "{\"rules\":[{"
                + "\"conditionRoot\":{\"type\":\"group\",\"children\":[{\"type\":\"leaf\",\"leftOperand\":{\"kind\":\"PATH\",\"value\":\"request.score\"},\"operator\":\">=\",\"rightOperand\":{\"kind\":\"LITERAL\",\"value\":\"600\",\"valueType\":\"NUMBER\"}}]},"
                + "\"actions\":[{\"targetOperand\":{\"kind\":\"REFERENCE\",\"code\":\"decision\",\"refId\":2,\"refType\":\"VARIABLE\"},\"valueOperand\":{\"kind\":\"PATH\",\"value\":\"request.result\"}}]}]}";

        List<String> inputs = analyzer.extractInputFields(json, "TABLE").stream()
                .map(RuleDefinitionInputField::getScriptName).collect(Collectors.toList());
        List<String> outputs = analyzer.extractOutputFields(json, "TABLE").stream()
                .map(RuleDefinitionOutputField::getScriptName).collect(Collectors.toList());

        assertTrue(inputs.contains("request.score"));
        assertTrue(inputs.contains("request.result"));
        assertTrue(outputs.contains("decision"));
    }

    @Test
    public void ruleSetResultVarIsAListOutputWithStableDataObjectReference() {
        String json = "{"
                + "\"resultVar\":{\"varCode\":\"request.hitRules\",\"varType\":\"LIST\",\"_varId\":33,\"_refType\":\"DATA_OBJECT\","
                + "\"operand\":{\"kind\":\"PATH\",\"value\":\"request.hitRules\",\"code\":\"request.hitRules\",\"valueType\":\"LIST\",\"refId\":33,\"refType\":\"DATA_OBJECT\",\"resolved\":true}},"
                + "\"rules\":[]"
                + "}";

        List<RuleDefinitionOutputField> outputs = analyzer.extractOutputFields(json, "RULE_SET");
        List<String> inputs = analyzer.extractInputFields(json, "RULE_SET").stream()
                .map(RuleDefinitionInputField::getScriptName).collect(Collectors.toList());

        assertEquals(1, outputs.size());
        assertEquals("request.hitRules", outputs.get(0).getScriptName());
        assertEquals("LIST", outputs.get(0).getFieldType());
        assertEquals(Long.valueOf(33), outputs.get(0).getVarId());
        assertEquals("DATA_OBJECT", outputs.get(0).getRefType());
        assertFalse(inputs.contains("request.hitRules"));
    }

    @Test
    public void disabledRuleCallMappingDoesNotExposeRetainedTargetAsOutput() {
        String json = "{\"rules\":[{\"actionData\":[{"
                + "\"type\":\"rule-call\",\"ruleId\":8,\"ruleCode\":\"score_card\","
                + "\"enableOutputMapping\":false,\"outputField\":\"score\","
                + "\"targetOperand\":{\"kind\":\"REFERENCE\",\"code\":\"risk_score\",\"valueType\":\"NUMBER\",\"refId\":9,\"refType\":\"VARIABLE\",\"resolved\":true}"
                + "}]}]}";

        List<String> outputs = analyzer.extractOutputFields(json, "RULE_SET").stream()
                .map(RuleDefinitionOutputField::getScriptName).collect(Collectors.toList());

        assertFalse(outputs.contains("risk_score"));
    }

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
    public void graphFunctionArgsOnlyTreatExplicitRefsAsInputs() {
        String json = "{\"nodes\":[{\"id\":\"n1\",\"type\":\"task\",\"actionData\":[{"
                + "\"type\":\"func-call\",\"target\":\"age\",\"funcName\":\"idCardAge\","
                + "\"args\":[\"idcard_no\",\"credit_time\",\"DAY\"],"
                + "\"_argRefs\":[{\"_varId\":6,\"_refType\":\"VARIABLE\"},"
                + "{\"_varId\":8,\"_refType\":\"VARIABLE\"},null]"
                + "}]}]}";

        List<String> inputs = analyzer.extractInputFields(json, "FLOW").stream()
                .map(RuleDefinitionInputField::getScriptName)
                .collect(Collectors.toList());

        assertTrue(inputs.contains("idcard_no"));
        assertTrue(inputs.contains("credit_time"));
        assertFalse(inputs.contains("DAY"));
    }

    @Test
    public void graphForeachItemVariableIsNotAnExternalInput() {
        String json = "{\"nodes\":[{\"id\":\"n1\",\"type\":\"task\",\"actionData\":[{"
                + "\"type\":\"foreach\",\"itemVar\":\"item\","
                + "\"listOperand\":{\"kind\":\"REFERENCE\",\"code\":\"model.results\",\"value\":\"model.results\"},"
                + "\"actions\":[{\"type\":\"func-call\",\"target\":\"summary\",\"args\":["
                + "{\"kind\":\"PATH\",\"code\":\"item\",\"value\":\"item\"}]}]}]}]}";

        List<String> inputs = analyzer.extractInputFields(json, "FLOW").stream()
                .map(RuleDefinitionInputField::getScriptName)
                .collect(Collectors.toList());

        assertTrue(inputs.contains("model.results"));
        assertFalse(inputs.contains("item"));
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

        assertFalse(names.contains("riskHit"));
        assertTrue(names.contains("mobile"));
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
        leafMeta.put("scriptName", "score_f1_fields.HYBASE_X115");
        Map<String, Map<String, Object>> varMetaMap = new HashMap<>();
        varMetaMap.put("score_f1.score", modelOutputMeta);
        varMetaMap.put("score_f1_fields.hybase_x115", leafMeta);

        RuleFieldAnalyzer analyzer = new TestableRuleFieldAnalyzer(Arrays.asList("HYBASE_X115"));

        Method expand = RuleFieldAnalyzer.class.getDeclaredMethod("expandModelInputFields", List.class, Map.class);
        expand.setAccessible(true);
        List<RuleDefinitionInputField> fields = (List<RuleDefinitionInputField>) expand.invoke(
                analyzer, java.util.Collections.singletonList(scoreF1), varMetaMap);
        List<String> names = fields.stream()
                .map(RuleDefinitionInputField::getScriptName)
                .collect(Collectors.toList());

        assertFalse("模型输出不应保留为测试入参", names.contains("score_f1.score"));
        assertTrue("模型输出应穿透到最底层输入特征", names.contains("score_f1_fields.HYBASE_X115"));
        RuleDefinitionInputField leaf = fields.stream()
                .filter(f -> "score_f1_fields.HYBASE_X115".equals(f.getScriptName())).findFirst().get();
        assertEquals("底层字段应携带引擎变量关联", Long.valueOf(25), leaf.getVarId());
        assertEquals("DATA_OBJECT", leaf.getRefType());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void repeatedModelOutputDependencyDoesNotBecomeExternalInput() throws Exception {
        RuleDefinitionInputField modelOutput = new RuleDefinitionInputField();
        modelOutput.setScriptName("face_detector.faces");
        modelOutput.setRefType("MODEL_OUTPUT");
        modelOutput.setVarId(130L);

        Map<String, Object> modelOutputMeta = new HashMap<>();
        modelOutputMeta.put("varSource", "MODEL_OUTPUT");
        modelOutputMeta.put("modelId", 1L);
        Map<String, Object> leafMeta = new HashMap<>();
        leafMeta.put("varSource", "DATA_OBJECT");
        leafMeta.put("id", 25L);
        leafMeta.put("refType", "DATA_OBJECT");
        leafMeta.put("scriptName", "request.face_image");
        Map<String, Map<String, Object>> varMetaMap = new HashMap<>();
        varMetaMap.put("face_detector.faces", modelOutputMeta);
        varMetaMap.put("request.face_image", leafMeta);

        RuleFieldAnalyzer analyzer = new TestableRuleFieldAnalyzer(Collections.singletonList("request.face_image"));
        Method expand = RuleFieldAnalyzer.class.getDeclaredMethod("expandModelInputFields", List.class, Map.class);
        expand.setAccessible(true);
        List<RuleDefinitionInputField> fields = (List<RuleDefinitionInputField>) expand.invoke(
                analyzer, Arrays.asList(modelOutput, modelOutput), varMetaMap);

        assertEquals(Collections.singletonList("request.face_image"), names(fields));
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
        assertFalse("DB 变量本身不应保留为测试入参", names(dbResult).contains("db_score"));

        RuleDefinitionInputField apiField = inputField("api_score", "VARIABLE", "API", 101L);
        List<RuleDefinitionInputField> apiResult = (List<RuleDefinitionInputField>) expand.invoke(
                analyzer, java.util.Collections.singletonList(apiField), varMetaMap);
        assertTrue("API 变量应穿透到请求映射依赖", names(apiResult).contains("idcard_no"));
        assertFalse("API 变量本身不应保留为测试入参", names(apiResult).contains("api_score"));

        RuleDefinitionInputField computedField = inputField("computed_score", "VARIABLE", "COMPUTED", 102L);
        List<RuleDefinitionInputField> computedResult = (List<RuleDefinitionInputField>) expand.invoke(
                analyzer, java.util.Collections.singletonList(computedField), varMetaMap);
        assertTrue("计算变量应穿透到表达式引用变量", names(computedResult).contains("idcard_no"));
        assertTrue("计算变量应穿透到表达式引用变量", names(computedResult).contains("age"));
        assertFalse("计算变量本身不应保留为测试入参", names(computedResult).contains("computed_score"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void sourceVariableWithoutDependenciesDoesNotBecomeExternalInput() throws Exception {
        setField(analyzer, "variableSourceResolver", new VariableSourceResolver());

        Map<String, Object> apiMeta = new HashMap<>();
        apiMeta.put("varSource", "API");
        apiMeta.put("sourceConfig", "{\"apiConfigId\":7,\"resultPath\":\"body.items\"}");
        Map<String, Map<String, Object>> varMetaMap = new HashMap<>();
        varMetaMap.put("engine_bdrules", apiMeta);

        Method expand = RuleFieldAnalyzer.class.getDeclaredMethod("expandModelInputFields", List.class, Map.class);
        expand.setAccessible(true);
        RuleDefinitionInputField apiField = inputField("engine_bdrules", "VARIABLE", "API", 195L);
        List<RuleDefinitionInputField> result = (List<RuleDefinitionInputField>) expand.invoke(
                analyzer, Collections.singletonList(apiField), varMetaMap);

        assertTrue("API variables without dependencies must not become external inputs", result.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void constantsAreNotExpandedAsTestInputFields() throws Exception {
        Map<String, Map<String, Object>> varMetaMap = new HashMap<>();
        Map<String, Object> constantMeta = new HashMap<>();
        constantMeta.put("varSource", "CONSTANT");
        constantMeta.put("id", 10L);
        constantMeta.put("refType", "CONSTANT");
        varMetaMap.put("empty_value", constantMeta);

        Method expand = RuleFieldAnalyzer.class.getDeclaredMethod("expandModelInputFields", List.class, Map.class);
        expand.setAccessible(true);
        RuleDefinitionInputField constantField = inputField("empty_value", "CONSTANT", "CONSTANT", 10L);
        List<RuleDefinitionInputField> result = (List<RuleDefinitionInputField>) expand.invoke(
                analyzer, java.util.Collections.singletonList(constantField), varMetaMap);

        assertTrue("常量是直接值，不应生成测试入参字段", result.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void dataObjectLeafUsesPersistedObjectPathWhenOnlyVarIdMatches() throws Exception {
        Map<String, Object> leafMeta = new HashMap<>();
        leafMeta.put("varSource", "DATA_OBJECT");
        leafMeta.put("id", 25L);
        leafMeta.put("refType", "DATA_OBJECT");
        leafMeta.put("scriptName", "score_f1_fields.HYBASE_X115");
        leafMeta.put("varType", "DOUBLE");
        Map<String, Map<String, Object>> varMetaMap = new HashMap<>();
        varMetaMap.put("score_f1_fields.hybase_x115", leafMeta);

        Method expand = RuleFieldAnalyzer.class.getDeclaredMethod("expandModelInputFields", List.class, Map.class);
        expand.setAccessible(true);
        RuleDefinitionInputField field = inputField("HYBASE_X115", "DATA_OBJECT", "DATA_OBJECT", 25L);
        List<RuleDefinitionInputField> result = (List<RuleDefinitionInputField>) expand.invoke(
                analyzer, java.util.Collections.singletonList(field), varMetaMap);

        assertTrue(names(result).contains("score_f1_fields.HYBASE_X115"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void variableMetaCarriesExampleAndDefaultValueToResolvedInputs() throws Exception {
        Map<String, Object> ageMeta = leafMeta("age", "INPUT", 37L);
        ageMeta.put("scriptName", "age");
        ageMeta.put("varType", "NUMBER");
        ageMeta.put("defaultValue", "18");
        ageMeta.put("exampleValue", "55");
        Map<String, Map<String, Object>> varMetaMap = new HashMap<>();
        varMetaMap.put("age", ageMeta);

        Method expand = RuleFieldAnalyzer.class.getDeclaredMethod("expandModelInputFields", List.class, Map.class);
        expand.setAccessible(true);
        RuleDefinitionInputField field = inputField("age", "VARIABLE", "INPUT", 37L);
        List<RuleDefinitionInputField> result = (List<RuleDefinitionInputField>) expand.invoke(
                analyzer, java.util.Collections.singletonList(field), varMetaMap);

        RuleDefinitionInputField resolved = result.get(0);
        assertEquals("18", resolved.getDefaultValue());
        assertEquals("55", resolved.getExampleValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void ruleCallInputsAreMergedAndCurrentOutputsAreExcluded() throws Exception {
        RuleDefinitionInputField age = inputField("age", "VARIABLE", "INPUT", 37L);
        RuleDefinitionInputField scoreField = inputField("HYBASE_X115", "DATA_OBJECT", "DATA_OBJECT", 25L);
        setField(analyzer, "inputFieldMapper", inputFieldMapper(Arrays.asList(age, scoreField)));

        String json = "{\"nodes\":[{\"actionData\":[{\"type\":\"rule-call\",\"ruleId\":1}]}]}";
        Method loadRuleCallInputs = RuleFieldAnalyzer.class.getDeclaredMethod("loadRuleCallInputFields", String.class);
        loadRuleCallInputs.setAccessible(true);
        List<RuleDefinitionInputField> calledInputs = (List<RuleDefinitionInputField>) loadRuleCallInputs.invoke(analyzer, json);
        assertTrue(names(calledInputs).contains("age"));
        assertTrue(names(calledInputs).contains("HYBASE_X115"));

        RuleDefinitionOutputField output = new RuleDefinitionOutputField();
        output.setScriptName("age");
        Method removeOutputs = RuleFieldAnalyzer.class.getDeclaredMethod("removeOutputFields", List.class, List.class);
        removeOutputs.setAccessible(true);
        List<RuleDefinitionInputField> filtered = (List<RuleDefinitionInputField>) removeOutputs.invoke(
                analyzer, calledInputs, java.util.Collections.singletonList(output));

        assertFalse("当前规则已计算的中间变量不应作为测试入参", names(filtered).contains("age"));
        assertTrue(names(filtered).contains("HYBASE_X115"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void ruleCallOutputsArePropagatedAndCanRemoveIntermediateInputs() throws Exception {
        RuleDefinitionOutputField riskFactor = new RuleDefinitionOutputField();
        riskFactor.setScriptName("risk_factor");
        riskFactor.setFieldName("risk_factor");
        riskFactor.setRefType("VARIABLE");
        riskFactor.setVarId(218L);
        riskFactor.setStatus(1);
        setField(analyzer, "outputFieldMapper", outputFieldMapper(Collections.singletonList(riskFactor)));

        String json = "{\"nodes\":[{\"actionData\":[{\"type\":\"rule-call\",\"ruleId\":11}]}]}";
        Method loadRuleCallOutputs = RuleFieldAnalyzer.class.getDeclaredMethod("loadRuleCallOutputFields", String.class);
        loadRuleCallOutputs.setAccessible(true);
        List<RuleDefinitionOutputField> calledOutputs =
                (List<RuleDefinitionOutputField>) loadRuleCallOutputs.invoke(analyzer, json);

        assertEquals(Collections.singletonList("risk_factor"), outputNames(calledOutputs));
        RuleDefinitionInputField intermediate = inputField("risk_factor", "VARIABLE", "INPUT", 218L);
        Method removeOutputs = RuleFieldAnalyzer.class.getDeclaredMethod("removeOutputFields", List.class, List.class);
        removeOutputs.setAccessible(true);
        List<RuleDefinitionInputField> filtered = (List<RuleDefinitionInputField>) removeOutputs.invoke(
                analyzer, Collections.singletonList(intermediate), calledOutputs);
        assertTrue("子规则产出的中间字段不应成为父规则外部输入", filtered.isEmpty());
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

    @Test
    public void persistedVariableTypeOverridesNameHeuristics() throws Exception {
        RuleDefinitionInputField field = inputField("riskFactor", "VARIABLE", "INPUT", 241L);
        field.setFieldType("BOOLEAN");
        Map<String, Object> meta = leafMeta("riskFactor", "INPUT", 241L);
        meta.put("refType", "VARIABLE");
        meta.put("scriptName", "riskFactor");
        meta.put("varType", "NUMBER");
        Map<String, Map<String, Object>> varMetaMap = new HashMap<>();
        varMetaMap.put("riskfactor", meta);

        Method enrich = RuleFieldAnalyzer.class.getDeclaredMethod("enrichFieldFromMeta",
                RuleDefinitionInputField.class, Map.class, Map.class, Map.class);
        enrich.setAccessible(true);
        enrich.invoke(analyzer, field, varMetaMap, Collections.emptyMap(), Collections.emptyMap());

        assertEquals("NUMBER", field.getFieldType());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void modelOperandDependenciesTraverseEveryRecursiveNodeKind() throws Exception {
        RuleModelInputField modelField = new RuleModelInputField();
        modelField.setFieldName("derived");
        modelField.setFieldType("NUMBER");
        modelField.setSourceOperand("{\"kind\":\"CAST\",\"targetType\":\"NUMBER\",\"operand\":{\"kind\":\"OPERATION\",\"terms\":[{\"operand\":{\"kind\":\"REFERENCE\",\"refId\":11,\"refType\":\"VARIABLE\",\"code\":\"baseAmount\",\"valueType\":\"NUMBER\"}},{\"operator\":\"+\",\"operand\":{\"kind\":\"ACCESS\",\"accessType\":\"KEY\",\"target\":{\"kind\":\"REFERENCE\",\"refId\":12,\"refType\":\"DATA_OBJECT\",\"code\":\"payload\",\"valueType\":\"MAP\"},\"accessor\":{\"kind\":\"LITERAL\",\"value\":\"score\",\"valueType\":\"STRING\"}}}]}}");

        Method copy = RuleFieldAnalyzer.class.getDeclaredMethod("copyModelInputFields", RuleModelInputField.class);
        copy.setAccessible(true);
        List<RuleDefinitionInputField> fields = (List<RuleDefinitionInputField>) copy.invoke(analyzer, modelField);

        assertEquals(Arrays.asList("baseAmount", "payload"), names(fields));
        assertEquals(Long.valueOf(11), fields.get(0).getVarId());
        assertEquals(Long.valueOf(12), fields.get(1).getVarId());
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

    private static RuleDefinitionInputFieldMapper inputFieldMapper(List<RuleDefinitionInputField> fields) {
        return (RuleDefinitionInputFieldMapper) Proxy.newProxyInstance(
                RuleDefinitionInputFieldMapper.class.getClassLoader(),
                new Class<?>[]{RuleDefinitionInputFieldMapper.class},
                (proxy, method, args) -> "selectList".equals(method.getName()) ? fields : null);
    }

    private static RuleDefinitionOutputFieldMapper outputFieldMapper(List<RuleDefinitionOutputField> fields) {
        return (RuleDefinitionOutputFieldMapper) Proxy.newProxyInstance(
                RuleDefinitionOutputFieldMapper.class.getClassLoader(),
                new Class<?>[]{RuleDefinitionOutputFieldMapper.class},
                (proxy, method, args) -> "selectList".equals(method.getName()) ? fields : null);
    }

    @SuppressWarnings("unchecked")
    private static <T> T mapper(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static List<String> names(List<RuleDefinitionInputField> fields) {
        return fields.stream().map(RuleDefinitionInputField::getScriptName).collect(Collectors.toList());
    }

    private static List<String> outputNames(List<RuleDefinitionOutputField> fields) {
        return fields.stream().map(RuleDefinitionOutputField::getScriptName).collect(Collectors.toList());
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
