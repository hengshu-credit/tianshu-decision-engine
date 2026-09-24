package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleModelInputField;
import com.hengshucredit.rule.model.entity.RuleModelOutputField;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDataObjectMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionInputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionOutputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalApiConfigMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalDatasourceMapper;
import com.hengshucredit.rule.server.mapper.RuleModelMapper;
import com.hengshucredit.rule.server.mapper.RuleModelOutputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleVariableMapper;
import com.hengshucredit.rule.server.artifact.PublishedRuleFieldSnapshotResolver;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuleFieldAnalyzerTest {

    private final RuleFieldAnalyzer analyzer = new RuleFieldAnalyzer();

    @Test
    public void visualRuntimeInputsKeepDataObjectReferenceEvenWhenPublicInputIsExpanded() {
        String model = "{\"nodes\":[],\"edges\":[{\"conditionConfig\":{\"type\":\"leaf\","
                + "\"leftOperand\":{\"kind\":\"PATH\",\"refType\":\"DATA_OBJECT\",\"refId\":268,"
                + "\"value\":\"metrics.found\"},\"operator\":\"==\","
                + "\"rightOperand\":{\"kind\":\"LITERAL\",\"value\":true,\"valueType\":\"BOOLEAN\"}}}]}";

        List<RuleDefinitionInputField> fields = analyzer.extractDirectModelInputFields(model, "FLOW");

        assertEquals(1, fields.size());
        assertEquals(Long.valueOf(268), fields.get(0).getVarId());
        assertEquals("DATA_OBJECT", fields.get(0).getRefType());
        assertEquals("metrics.found", fields.get(0).getScriptName());
    }

    @Test
    public void persistResolvedFieldsReplacesProjectionWithoutResolvingAgain()
            throws Exception {
        List<String> writes = new ArrayList<>();
        List<RuleDefinitionInputField> insertedInputs = new ArrayList<>();
        List<RuleDefinitionOutputField> insertedOutputs = new ArrayList<>();
        setField(analyzer, "inputFieldMapper",
                mapper(RuleDefinitionInputFieldMapper.class,
                        (proxy, method, args) -> {
                            if ("delete".equals(method.getName())) {
                                writes.add("delete-inputs");
                                return 1;
                            }
                            if ("insert".equals(method.getName())) {
                                writes.add("insert-input");
                                insertedInputs.add(
                                        (RuleDefinitionInputField) args[0]);
                                return 1;
                            }
                            return null;
                        }));
        setField(analyzer, "outputFieldMapper",
                mapper(RuleDefinitionOutputFieldMapper.class,
                        (proxy, method, args) -> {
                            if ("delete".equals(method.getName())) {
                                writes.add("delete-outputs");
                                return 1;
                            }
                            if ("insert".equals(method.getName())) {
                                writes.add("insert-output");
                                insertedOutputs.add(
                                        (RuleDefinitionOutputField) args[0]);
                                return 1;
                            }
                            return null;
                        }));
        RuleDefinitionInputField input = new RuleDefinitionInputField();
        input.setFieldName("request.score");
        input.setScriptName("request.score");
        input.setVarId(91L);
        input.setRefType("DATA_OBJECT");
        RuleDefinitionOutputField output =
                new RuleDefinitionOutputField();
        output.setFieldName("decision");
        output.setScriptName("decision");
        RuleFieldAnalyzer.ResolvedFields fields =
                new RuleFieldAnalyzer.ResolvedFields(
                        Collections.singletonList(input),
                        Collections.singletonList(output));

        analyzer.persistResolvedFields(30L, fields);

        assertEquals(Arrays.asList("delete-inputs", "delete-outputs",
                "insert-input", "insert-output"), writes);
        assertEquals(Long.valueOf(91L), insertedInputs.get(0).getVarId());
        assertEquals("request.score",
                insertedInputs.get(0).getScriptName());
        assertEquals(Long.valueOf(30L),
                insertedInputs.get(0).getDefinitionId());
        assertEquals(Integer.valueOf(0),
                insertedInputs.get(0).getSortOrder());
        assertEquals("decision",
                insertedOutputs.get(0).getScriptName());
    }

    @Test
    public void persistResolvedFieldsRejectsZeroInputInsert()
            throws Exception {
        RuleFieldAnalyzer target = new RuleFieldAnalyzer();
        setField(target, "inputFieldMapper",
                mapper(RuleDefinitionInputFieldMapper.class,
                        (proxy, method, args) -> "insert".equals(
                                method.getName()) ? 0 : 1));
        setField(target, "outputFieldMapper",
                mapper(RuleDefinitionOutputFieldMapper.class,
                        (proxy, method, args) -> 1));
        RuleDefinitionInputField input =
                new RuleDefinitionInputField();
        input.setFieldName("score");

        IllegalStateException error = org.junit.Assert.assertThrows(
                IllegalStateException.class,
                () -> target.persistResolvedFields(
                        30L, new RuleFieldAnalyzer.ResolvedFields(
                                Collections.singletonList(input),
                                Collections.emptyList())));

        assertTrue(error.getMessage().contains("输入字段"));
    }

    @Test
    public void persistResolvedFieldsRejectsZeroOutputInsert()
            throws Exception {
        RuleFieldAnalyzer target = new RuleFieldAnalyzer();
        setField(target, "inputFieldMapper",
                mapper(RuleDefinitionInputFieldMapper.class,
                        (proxy, method, args) -> 1));
        setField(target, "outputFieldMapper",
                mapper(RuleDefinitionOutputFieldMapper.class,
                        (proxy, method, args) -> "insert".equals(
                                method.getName()) ? 0 : 1));
        RuleDefinitionOutputField output =
                new RuleDefinitionOutputField();
        output.setFieldName("decision");

        IllegalStateException error = org.junit.Assert.assertThrows(
                IllegalStateException.class,
                () -> target.persistResolvedFields(
                        30L, new RuleFieldAnalyzer.ResolvedFields(
                                Collections.emptyList(),
                                Collections.singletonList(output))));

        assertTrue(error.getMessage().contains("输出字段"));
    }

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
    public void dataObjectFieldReferenceExpandsToVariableByStableId() throws Exception {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new Configuration(), ""), RuleVariable.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new Configuration(), ""), RuleDataObject.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new Configuration(), ""), RuleDataObjectField.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new Configuration(), ""), RuleModel.class);

        RuleVariable age = inputVariable(9L, "age");
        age.setProjectId(4L);
        age.setScope("PROJECT");
        age.setVarType("NUMBER");

        RuleDataObject request = new RuleDataObject();
        request.setId(20L);
        request.setProjectId(4L);
        request.setScope("PROJECT");
        request.setObjectCode("request");
        request.setScriptName("request");
        request.setStatus(1);

        RuleDataObjectField ageAlias = new RuleDataObjectField();
        ageAlias.setId(30L);
        ageAlias.setProjectId(4L);
        ageAlias.setScope("PROJECT");
        ageAlias.setObjectId(20L);
        ageAlias.setVarCode("ageAlias");
        ageAlias.setScriptName("ageAlias");
        ageAlias.setVarLabel("对象年龄");
        ageAlias.setVarType("NUMBER");
        ageAlias.setRefVariableId(9L);
        ageAlias.setStatus(1);

        setField(analyzer, "ruleVariableMapper", mapper(
                RuleVariableMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? Collections.singletonList(age) : null));
        setField(analyzer, "dataObjectMapper", mapper(
                RuleDataObjectMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? Collections.singletonList(request) : null));
        setField(analyzer, "dataObjectFieldMapper", mapper(
                RuleDataObjectFieldMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? Collections.singletonList(ageAlias) : null));
        setField(analyzer, "modelMapper", mapper(
                RuleModelMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? Collections.emptyList() : null));
        setField(analyzer, "modelOutputFieldMapper", mapper(
                RuleModelOutputFieldMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? Collections.emptyList() : null));

        RuleDefinitionInputField objectField = inputField(
                "request.ageAlias", "DATA_OBJECT", "DATA_OBJECT", 30L);

        List<RuleDefinitionInputField> resolved =
                analyzer.resolveInputFields(Collections.singletonList(objectField), 4L);

        assertEquals(Collections.singletonList("age"), names(resolved));
        assertEquals(Long.valueOf(9L), resolved.get(0).getVarId());
        assertEquals("VARIABLE", resolved.get(0).getRefType());
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
    public void scriptExtractsInputsFromRightHandSide() throws Exception {
        setField(analyzer, "qlScriptFieldResolver", scriptResolver(Collections.emptyMap()));
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
        assertFalse(outputs.contains("result.level"));
    }

    @Test
    public void scriptExplicitResultMapExtractsReturnedKeysAsOutputs() throws Exception {
        setField(analyzer, "qlScriptFieldResolver", scriptResolver(Collections.emptyMap()));
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
    public void scriptInputWithoutStableReferenceIsNotExpandedByMatchingVariableCode() throws Exception {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new Configuration(), ""), RuleModel.class);
        RuleVariable variable = new RuleVariable();
        variable.setId(302L);
        variable.setProjectId(4L);
        variable.setScope("PROJECT");
        variable.setVarCode("api_score");
        variable.setScriptName("api_score");
        variable.setVarType("NUMBER");
        variable.setVarSource("API");
        variable.setSourceConfig("{\"paramMapping\":{\"id\":\"$.request.id\"}}");
        variable.setStatus(1);
        Map<Long, RuleVariable> variables = Collections.singletonMap(302L, variable);
        setField(analyzer, "qlScriptFieldResolver", scriptResolver(variables));
        setField(analyzer, "ruleVariableMapper", mapper(RuleVariableMapper.class,
                (proxy, method, args) -> {
                    if ("selectList".equals(method.getName())) return Collections.singletonList(variable);
                    if ("selectById".equals(method.getName())) return variables.get(((Number) args[0]).longValue());
                    return null;
                }));
        setField(analyzer, "dataObjectFieldMapper", mapper(RuleDataObjectFieldMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? Collections.emptyList() : null));
        setField(analyzer, "dataObjectMapper", mapper(RuleDataObjectMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? Collections.emptyList() : null));
        setField(analyzer, "modelMapper", mapper(RuleModelMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? Collections.emptyList() : null));
        setField(analyzer, "modelOutputFieldMapper", mapper(RuleModelOutputFieldMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? Collections.emptyList() : null));
        setField(analyzer, "variableSourceResolver", new VariableSourceResolver());
        String json = "{\"script\":\"risk = api_score; risk\",\"scriptVarRefs\":[]}";

        RuleFieldAnalyzer.ResolvedFields fields =
                analyzer.resolveFields(null, json, "SCRIPT", 4L);

        assertEquals(Collections.singletonList("api_score"), names(fields.getInputFields()));
        assertNull(fields.getInputFields().get(0).getVarId());
        assertTrue(fields.getDiagnostics().stream()
                .anyMatch(issue -> "SCRIPT_INPUT_REF_MISSING".equals(issue.getCode())));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void scriptObjectLeafTypesAndResultOutputsFollowStableFieldIdsWithoutInvokingApi()
            throws Exception {
        ScriptShapeFixture fixture = new ScriptShapeFixture(analyzer);
        fixture.apiObjectVariable(302L, "icekredit_vn_credit_profile_features", 7L);
        fixture.apiResponseObject(7L, 10L);
        fixture.fields(
                apiShapeField(101L, 10L, null, "credit_score_v1", "NUMBER"),
                apiShapeField(113L, 10L, null, "credit_apply_count_1m", "INTEGER"));
        fixture.configure();
        String script = "credit_score_v1 = "
                + "icekredit_vn_credit_profile_features.credit_score_v1;\n"
                + "credit_apply_count_1m = "
                + "icekredit_vn_credit_profile_features.credit_apply_count_1m;\n"
                + "_result = {\"credit_score_v1\": credit_score_v1,"
                + "\"credit_apply_count_1m\": credit_apply_count_1m}\n_result";
        String json = "{\"script\":" + com.alibaba.fastjson.JSON.toJSONString(script)
                + ",\"scriptVarRefs\":[{\"refCode\":\"icekredit_vn_credit_profile_features\","
                + "\"varId\":302,\"refType\":\"VARIABLE\"}]}";

        RuleFieldAnalyzer.ResolvedFields fields =
                analyzer.resolveFields(null, json, "SCRIPT", 4L);

        assertEquals("NUMBER", ((Map<String, Object>) fields.getInputPropertySchemas().get(
                "icekredit_vn_credit_profile_features.credit_score_v1")).get("x-rule-type"));
        assertEquals("INTEGER", ((Map<String, Object>) fields.getInputPropertySchemas().get(
                "icekredit_vn_credit_profile_features.credit_apply_count_1m")).get("x-rule-type"));
        assertEquals("NUMBER", outputType(fields, "credit_score_v1"));
        assertEquals("INTEGER", outputType(fields, "credit_apply_count_1m"));
        assertEquals(0, fixture.apiInvocations.get());
    }

    @Test
    public void scriptReadBelowOpenObjectRequiresStableDescendantFieldId() throws Exception {
        ScriptShapeFixture fixture = new ScriptShapeFixture(analyzer);
        fixture.apiObjectVariable(302L, "api_features", 7L);
        fixture.apiResponseObject(7L, 10L);
        fixture.configure();
        String json = "{\"script\":\"value = api_features.unknown_descendant;"
                + "_result = {\\\"value\\\": value}; _result\","
                + "\"scriptVarRefs\":[{\"refCode\":\"api_features\",\"varId\":302,"
                + "\"refType\":\"VARIABLE\"}]}";

        RuleFieldAnalyzer.ResolvedFields fields =
                analyzer.resolveFields(null, json, "SCRIPT", 4L);

        assertTrue(fields.getDiagnostics().stream()
                .anyMatch(issue -> "OBJECT_SHAPE_INCOMPLETE".equals(issue.getCode())
                        && "WARNING".equals(issue.getSeverity())));
        assertTrue(fields.getDiagnostics().stream()
                .anyMatch(issue -> "SCRIPT_INPUT_REF_MISSING".equals(issue.getCode())
                        && "ERROR".equals(issue.getSeverity())));
        assertEquals(0, fixture.apiInvocations.get());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void scriptDataObjectParentIdProvesTypedDescendantThroughSchema()
            throws Exception {
        ScriptShapeFixture fixture = new ScriptShapeFixture(analyzer);
        fixture.apiResponseObject(7L, 10L);
        fixture.fields(
                apiShapeField(141L, 10L, null, "address", "OBJECT"),
                apiShapeField(142L, 10L, 141L, "city", "STRING"));
        fixture.configure();
        String script = "city = response_10.address.city;"
                + " _result = {\"city\": city}; _result";
        String json = "{\"script\":"
                + com.alibaba.fastjson.JSON.toJSONString(script)
                + ",\"scriptVarRefs\":[{\"refCode\":\"response_10.address\","
                + "\"varId\":141,\"refType\":\"DATA_OBJECT\"}]}";

        RuleFieldAnalyzer.ResolvedFields fields =
                analyzer.resolveFields(null, json, "SCRIPT", 4L);

        RuleDefinitionInputField input = fields.getInputFields().get(0);
        assertEquals(Long.valueOf(141L), input.getVarId());
        assertEquals("STRING", input.getFieldType());
        assertEquals("STRING", ((Map<String, Object>) fields.getInputPropertySchemas()
                .get("response_10.address.city")).get("x-rule-type"));
        assertFalse(fields.getDiagnostics().stream()
                .anyMatch(issue -> "SCRIPT_INPUT_REF_MISSING".equals(issue.getCode())));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void scriptInlineListElementLeafTypeUsesNormalizedIndexPath() throws Exception {
        ScriptShapeFixture fixture = new ScriptShapeFixture(analyzer);
        fixture.apiObjectVariable(302L, "api_features", 7L);
        fixture.apiResponseObject(7L, 10L);
        RuleDataObjectField list = apiShapeField(
                142L, 10L, null, "app_list", "LIST");
        RuleDataObjectField child = apiShapeField(
                143L, 10L, 142L, "app_name", "STRING");
        fixture.fields(list, child);
        fixture.configure();
        String json = "{\"script\":\"appName = api_features.app_list[0].app_name;"
                + "_result = {\\\"appName\\\": appName}; _result\","
                + "\"scriptVarRefs\":[{\"refCode\":\"api_features\",\"varId\":302,"
                + "\"refType\":\"VARIABLE\"}]}";

        RuleFieldAnalyzer.ResolvedFields fields =
                analyzer.resolveFields(null, json, "SCRIPT", 4L);

        Map<String, Object> property = (Map<String, Object>)
                fields.getInputPropertySchemas().get("api_features.app_list[0].app_name");
        assertEquals("STRING", property.get("x-rule-type"));
        assertEquals("STRING", outputType(fields, "appName"));
        assertFalse(fields.getDiagnostics().stream()
                .anyMatch(issue -> "SCRIPT_INPUT_REF_MISSING".equals(issue.getCode())));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void scriptDynamicListIndexKeepsOpenListShapeWithoutGuessingStringOutput()
            throws Exception {
        ScriptShapeFixture fixture = new ScriptShapeFixture(analyzer);
        fixture.apiObjectVariable(302L, "api_features", 7L);
        fixture.apiResponseObject(7L, 10L);
        fixture.fields(
                apiShapeField(142L, 10L, null, "app_list", "LIST"),
                apiShapeField(143L, 10L, 142L, "app_name", "STRING"));
        fixture.configure();
        String json = "{\"script\":\"index = 0;"
                + "appName = api_features.app_list[index].app_name;"
                + "_result = {\\\"appName\\\": appName}; _result\","
                + "\"scriptVarRefs\":[{\"refCode\":\"api_features\",\"varId\":302,"
                + "\"refType\":\"VARIABLE\"}]}";

        RuleFieldAnalyzer.ResolvedFields fields =
                analyzer.resolveFields(null, json, "SCRIPT", 4L);

        Map<String, Object> property = (Map<String, Object>)
                fields.getInputPropertySchemas().get("api_features.app_list");
        assertEquals("LIST", property.get("x-rule-type"));
        assertTrue(fields.getDiagnostics().stream()
                .anyMatch(issue -> "OBJECT_SHAPE_INCOMPLETE".equals(issue.getCode())
                        && "WARNING".equals(issue.getSeverity())));
        assertFalse(fields.getDiagnostics().stream()
                .anyMatch(issue -> "SCRIPT_INPUT_REF_MISSING".equals(issue.getCode())
                        && "$.script.api_features.app_list".equals(issue.getPath())));
        assertNull(outputType(fields, "appName"));
    }

    @Test
    public void scriptApiShapeRejectsDisabledApiConfigWithoutInvokingApi()
            throws Exception {
        ScriptShapeFixture fixture = scoreApiFixture();
        fixture.apiConfigStatus(7L, 0);

        RuleFieldAnalyzer.ResolvedFields fields = resolveScoreScript(fixture);

        assertFalse(fields.getInputPropertySchemas().containsKey("api_features.score"));
        assertNull(outputType(fields, "score"));
        assertDiagnostic(fields, "REFERENCE_TYPE_MISMATCH", "$.apiConfig.7");
        assertEquals(0, fixture.apiInvocations.get());
    }

    @Test
    public void scriptApiShapeRejectsDisabledDatasourceWithoutInvokingApi()
            throws Exception {
        ScriptShapeFixture fixture = scoreApiFixture();
        fixture.datasourceStatus(70L, 0);

        RuleFieldAnalyzer.ResolvedFields fields = resolveScoreScript(fixture);

        assertFalse(fields.getInputPropertySchemas().containsKey("api_features.score"));
        assertNull(outputType(fields, "score"));
        assertDiagnostic(fields, "REFERENCE_TYPE_MISMATCH", "$.datasource.70");
        assertEquals(0, fixture.apiInvocations.get());
    }

    @Test
    public void scriptApiShapeRejectsCrossProjectDatasourceWithoutInvokingApi()
            throws Exception {
        ScriptShapeFixture fixture = scoreApiFixture();
        fixture.datasourceProject(70L, 9L, "PROJECT");

        RuleFieldAnalyzer.ResolvedFields fields = resolveScoreScript(fixture);

        assertFalse(fields.getInputPropertySchemas().containsKey("api_features.score"));
        assertNull(outputType(fields, "score"));
        assertDiagnostic(fields, "REFERENCE_TYPE_MISMATCH", "$.datasource.70");
        assertEquals(0, fixture.apiInvocations.get());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void scriptApiShapeAcceptsActiveSameProjectDatasourceWithoutInvokingApi()
            throws Exception {
        ScriptShapeFixture fixture = scoreApiFixture();

        RuleFieldAnalyzer.ResolvedFields fields = resolveScoreScript(fixture);

        assertEquals("NUMBER", ((Map<String, Object>) fields
                .getInputPropertySchemas().get("api_features.score")).get("x-rule-type"));
        assertEquals("NUMBER", outputType(fields, "score"));
        assertFalse(fields.getDiagnostics().stream()
                .anyMatch(issue -> "REFERENCE_TYPE_MISMATCH".equals(issue.getCode())));
        assertEquals(0, fixture.apiInvocations.get());
    }

    @Test
    public void scriptExplicitResultTypeUsesAssignmentBeforeResultInsteadOfLateWrite()
            throws Exception {
        ScriptShapeFixture fixture = scoreApiFixture();
        fixture.configure();
        String json = scriptJson(
                "x = api_features.score; _result = {\"x\": x};"
                        + " x = \"late\"; _result");

        RuleFieldAnalyzer.ResolvedFields fields =
                analyzer.resolveFields(null, json, "SCRIPT", 4L);

        assertEquals("NUMBER", outputType(fields, "x"));
        assertEquals(0, fixture.apiInvocations.get());
    }

    @Test
    public void scriptExplicitResultTypeDoesNotResolveFutureAssignment()
            throws Exception {
        ScriptShapeFixture fixture = scoreApiFixture();
        fixture.configure();
        String json = scriptJson(
                "_result = {\"x\": x}; x = api_features.score; _result");

        RuleFieldAnalyzer.ResolvedFields fields =
                analyzer.resolveFields(null, json, "SCRIPT", 4L);

        assertNull(outputType(fields, "x"));
        assertEquals(0, fixture.apiInvocations.get());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void scriptNestedConstantListIndexUsesStableLeafType()
            throws Exception {
        ScriptShapeFixture fixture = nestedListApiFixture();
        fixture.configure();
        String json = scriptJson(
                "value = api_features.outer.items[0].score;"
                        + " _result = {\"value\": value}; _result");

        RuleFieldAnalyzer.ResolvedFields fields =
                analyzer.resolveFields(null, json, "SCRIPT", 4L);

        assertEquals("NUMBER", ((Map<String, Object>) fields.getInputPropertySchemas()
                .get("api_features.outer.items[0].score")).get("x-rule-type"));
        assertEquals("NUMBER", outputType(fields, "value"));
        assertFalse(fields.getDiagnostics().stream()
                .anyMatch(issue -> "SCRIPT_INPUT_REF_MISSING".equals(issue.getCode())));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void scriptNestedDynamicListIndexRecognizesStableIntermediateButKeepsOutputOpen()
            throws Exception {
        ScriptShapeFixture fixture = nestedListApiFixture();
        fixture.configure();
        String json = scriptJson(
                "index = 0; value = api_features.outer.items[index].score;"
                        + " _result = {\"value\": value}; _result");

        RuleFieldAnalyzer.ResolvedFields fields =
                analyzer.resolveFields(null, json, "SCRIPT", 4L);

        assertEquals("LIST", ((Map<String, Object>) fields.getInputPropertySchemas()
                .get("api_features.outer.items")).get("x-rule-type"));
        assertFalse(fields.getDiagnostics().stream()
                .anyMatch(issue -> "SCRIPT_INPUT_REF_MISSING".equals(issue.getCode())
                        && "$.script.api_features.outer.items".equals(issue.getPath())));
        assertNull(outputType(fields, "value"));
    }

    @Test
    public void scriptApiResponseRootNameConflictIsDiagnosedAndKeepsLowestStableId()
            throws Exception {
        ScriptShapeFixture fixture = new ScriptShapeFixture(analyzer);
        fixture.apiObjectVariable(302L, "api_features", 7L);
        fixture.apiResponseObject(7L, 10L);
        fixture.fields(
                apiShapeField(101L, 10L, null, "score", "NUMBER"),
                apiShapeField(113L, 10L, null, "score", "STRING"));

        RuleFieldAnalyzer.ResolvedFields fields = resolveScoreScript(fixture);

        assertDiagnostic(fields, "OBJECT_SHAPE_CONFLICT", "$.field.113");
        assertEquals("NUMBER", outputType(fields, "score"));
        assertEquals(0, fixture.apiInvocations.get());
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
    public void listSourceExpansionUsesEveryStructuredQueryOperand() throws Exception {
        RuleDefinitionInputField listHit = new RuleDefinitionInputField();
        listHit.setFieldName("riskHit");
        listHit.setScriptName("riskHit");
        listHit.setFieldType("NUMBER");
        listHit.setVarId(9L);
        listHit.setRefType("VARIABLE");

        Map<String, Object> meta = new HashMap<>();
        meta.put("varSource", "LIST");
        meta.put("sourceConfig", "{\"queryOperands\":["
                + "{\"kind\":\"REFERENCE\",\"value\":\"idcard_no\",\"code\":\"idcard_no\","
                + "\"label\":\"身份证号\",\"valueType\":\"STRING\",\"refId\":6,"
                + "\"refType\":\"VARIABLE\"},"
                + "{\"kind\":\"REFERENCE\",\"value\":\"mobile_no\",\"code\":\"mobile_no\","
                + "\"label\":\"手机号\",\"valueType\":\"STRING\",\"refId\":7,"
                + "\"refType\":\"VARIABLE\"}]}"
        );
        Map<String, Map<String, Object>> varMetaMap = new HashMap<>();
        varMetaMap.put("riskhit", meta);

        Method expand = RuleFieldAnalyzer.class.getDeclaredMethod(
                "expandModelInputFields", List.class, Map.class);
        expand.setAccessible(true);
        List<RuleDefinitionInputField> fields =
                (List<RuleDefinitionInputField>) expand.invoke(
                        analyzer, Collections.singletonList(listHit), varMetaMap);
        List<String> names = fields.stream()
                .map(RuleDefinitionInputField::getScriptName)
                .collect(Collectors.toList());

        assertFalse(names.contains("riskHit"));
        assertEquals(Arrays.asList("idcard_no", "mobile_no"), names);
        assertEquals(Long.valueOf(6L), fields.get(0).getVarId());
        assertEquals(Long.valueOf(7L), fields.get(1).getVarId());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void listSourceExpansionDoesNotRestoreDerivedVariableAfterDependencyDeduplication()
            throws Exception {
        RuleDefinitionInputField blackHit = new RuleDefinitionInputField();
        blackHit.setScriptName("blackHit");
        blackHit.setVarId(9L);
        blackHit.setRefType("VARIABLE");
        RuleDefinitionInputField whiteHit = new RuleDefinitionInputField();
        whiteHit.setScriptName("whiteHit");
        whiteHit.setVarId(10L);
        whiteHit.setRefType("VARIABLE");

        String sourceConfig = "{\"queryOperands\":["
                + "{\"kind\":\"REFERENCE\",\"value\":\"idcard_no\",\"refId\":6,"
                + "\"refType\":\"VARIABLE\"},"
                + "{\"kind\":\"REFERENCE\",\"value\":\"mobile_no\",\"refId\":7,"
                + "\"refType\":\"VARIABLE\"}]}";
        Map<String, Object> blackMeta = new HashMap<>();
        blackMeta.put("varSource", "LIST");
        blackMeta.put("sourceConfig", sourceConfig);
        Map<String, Object> whiteMeta = new HashMap<>();
        whiteMeta.put("varSource", "LIST");
        whiteMeta.put("sourceConfig", sourceConfig);
        Map<String, Map<String, Object>> varMetaMap = new HashMap<>();
        varMetaMap.put("blackhit", blackMeta);
        varMetaMap.put("whitehit", whiteMeta);

        Method expand = RuleFieldAnalyzer.class.getDeclaredMethod(
                "expandModelInputFields", List.class, Map.class);
        expand.setAccessible(true);
        List<RuleDefinitionInputField> fields =
                (List<RuleDefinitionInputField>) expand.invoke(
                        analyzer, Arrays.asList(blackHit, whiteHit), varMetaMap);
        List<String> names = fields.stream()
                .map(RuleDefinitionInputField::getScriptName)
                .collect(Collectors.toList());

        assertEquals(Arrays.asList("idcard_no", "mobile_no"), names);
    }

    @Test
    public void resolvedRuleFieldsExposeListQueryInputsNotRuntimeSource()
            throws Exception {
        RuleFieldAnalyzer localAnalyzer = new RuleFieldAnalyzer();
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new Configuration(), ""),
                RuleModel.class);
        RuleVariable listHit = new RuleVariable();
        listHit.setId(9L);
        listHit.setProjectId(4L);
        listHit.setScope("PROJECT");
        listHit.setVarCode("riskHit");
        listHit.setScriptName("riskHit");
        listHit.setVarLabel("名单命中");
        listHit.setVarType("NUMBER");
        listHit.setVarSource("LIST");
        listHit.setStatus(1);
        listHit.setSourceConfig("{\"queryOperands\":["
                + "{\"kind\":\"REFERENCE\",\"value\":\"idcard_no\",\"refId\":6,"
                + "\"refType\":\"VARIABLE\"},"
                + "{\"kind\":\"REFERENCE\",\"value\":\"mobile_no\",\"refId\":7,"
                + "\"refType\":\"VARIABLE\"}]}");
        RuleVariable idcard = inputVariable(6L, "idcard_no");
        RuleVariable mobile = inputVariable(7L, "mobile_no");
        setField(localAnalyzer, "ruleVariableMapper", mapper(
                RuleVariableMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? Arrays.asList(listHit, idcard, mobile) : null));
        setField(localAnalyzer, "dataObjectMapper", mapper(
                RuleDataObjectMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? Collections.emptyList() : null));
        setField(localAnalyzer, "dataObjectFieldMapper", mapper(
                RuleDataObjectFieldMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? Collections.emptyList() : null));
        setField(localAnalyzer, "modelMapper", mapper(
                RuleModelMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? Collections.emptyList() : null));
        setField(localAnalyzer, "modelOutputFieldMapper", mapper(
                RuleModelOutputFieldMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? Collections.emptyList() : null));

        RuleFieldAnalyzer.ResolvedFields fields = localAnalyzer.resolveFields(
                null,
                "{\"rules\":[{\"conditionRoot\":{\"type\":\"group\","
                        + "\"op\":\"AND\",\"children\":[{\"type\":\"leaf\","
                        + "\"leftOperand\":{\"kind\":\"REFERENCE\",\"value\":\"riskHit\","
                        + "\"refId\":9,\"refType\":\"VARIABLE\"},\"operator\":\"==\","
                        + "\"rightOperand\":{\"kind\":\"LITERAL\",\"value\":\"1\","
                        + "\"valueType\":\"NUMBER\"}}]}}]}",
                "RULE_SET", 4L);

        assertEquals(Arrays.asList("idcard_no", "mobile_no"),
                names(fields.getInputFields()));
    }

    @Test
    public void scriptResolvedFieldsExposeListQueryInputsNotRuntimeSource()
            throws Exception {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new Configuration(), ""),
                RuleModel.class);
        RuleVariable listHit = new RuleVariable();
        listHit.setId(9L);
        listHit.setProjectId(4L);
        listHit.setScope("PROJECT");
        listHit.setVarCode("riskHit");
        listHit.setScriptName("riskHit");
        listHit.setVarLabel("名单命中");
        listHit.setVarType("NUMBER");
        listHit.setVarSource("LIST");
        listHit.setStatus(1);
        listHit.setSourceConfig("{\"queryOperands\":["
                + "{\"kind\":\"REFERENCE\",\"value\":\"idcard_no\",\"refId\":6,"
                + "\"refType\":\"VARIABLE\"},"
                + "{\"kind\":\"REFERENCE\",\"value\":\"mobile_no\",\"refId\":7,"
                + "\"refType\":\"VARIABLE\"}]}");
        RuleVariable idcard = inputVariable(6L, "idcard_no");
        RuleVariable mobile = inputVariable(7L, "mobile_no");
        Map<Long, RuleVariable> variables = new HashMap<>();
        variables.put(9L, listHit);
        variables.put(6L, idcard);
        variables.put(7L, mobile);
        setField(analyzer, "qlScriptFieldResolver", scriptResolver(variables));
        setField(analyzer, "ruleVariableMapper", mapper(
                RuleVariableMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? Arrays.asList(listHit, idcard, mobile)
                        : "selectById".equals(method.getName())
                        ? variables.get(((Number) args[0]).longValue()) : null));
        setField(analyzer, "dataObjectMapper", mapper(
                RuleDataObjectMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? Collections.emptyList() : null));
        setField(analyzer, "dataObjectFieldMapper", mapper(
                RuleDataObjectFieldMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? Collections.emptyList() : null));
        setField(analyzer, "modelMapper", mapper(
                RuleModelMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? Collections.emptyList() : null));
        setField(analyzer, "modelOutputFieldMapper", mapper(
                RuleModelOutputFieldMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? Collections.emptyList() : null));

        RuleFieldAnalyzer.ResolvedFields fields = analyzer.resolveFields(
                null,
                "{\"script\":\"result = riskHit; result\","
                        + "\"scriptVarRefs\":[{\"refCode\":\"riskHit\","
                        + "\"varId\":9,\"refType\":\"VARIABLE\"}]}",
                "SCRIPT", 4L);

        assertEquals(Arrays.asList("idcard_no", "mobile_no"),
                names(fields.getInputFields()));
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
    public void computedVariableWithoutSourceExpressionRemainsInjectableForStandaloneTest() throws Exception {
        Map<String, Map<String, Object>> varMetaMap = new HashMap<>();
        Map<String, Object> computedMeta = new HashMap<>();
        computedMeta.put("varSource", "COMPUTED");
        computedMeta.put("scriptName", "risk_factor");
        computedMeta.put("varType", "DOUBLE");
        computedMeta.put("varId", 218L);
        varMetaMap.put("risk_factor", computedMeta);
        Method expand = RuleFieldAnalyzer.class.getDeclaredMethod(
                "expandModelInputFields", List.class, Map.class);
        expand.setAccessible(true);
        RuleDefinitionInputField field = inputField(
                "risk_factor", "VARIABLE", "COMPUTED", 218L);
        field.setFieldType("DOUBLE");

        List<RuleDefinitionInputField> result =
                (List<RuleDefinitionInputField>) expand.invoke(
                        analyzer, Collections.singletonList(field), varMetaMap);

        assertEquals(Collections.singletonList("risk_factor"), names(result));
        assertEquals("DOUBLE", result.get(0).getFieldType());
        assertEquals(Long.valueOf(218L), result.get(0).getVarId());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void apiObjectPropertyAccessExpandsByStableRootReference() throws Exception {
        setField(analyzer, "variableSourceResolver", new VariableSourceResolver());

        RuleExternalApiConfig apiConfig = new RuleExternalApiConfig();
        apiConfig.setId(7L);
        apiConfig.setRequestObjectId(301L);
        RuleDataObject requestObject = new RuleDataObject();
        requestObject.setId(301L);
        requestObject.setScriptName("request");
        RuleDataObjectField requestId = new RuleDataObjectField();
        requestId.setId(201L);
        requestId.setObjectId(301L);
        requestId.setScriptName("id");
        requestId.setVarCode("id");
        requestId.setVarLabel("请求ID");
        requestId.setVarType("STRING");
        requestId.setStatus(1);
        setField(analyzer, "externalApiConfigMapper", mapper(RuleExternalApiConfigMapper.class,
                (proxy, method, args) -> "selectById".equals(method.getName()) ? apiConfig : null));
        setField(analyzer, "dataObjectMapper", mapper(RuleDataObjectMapper.class,
                (proxy, method, args) -> "selectById".equals(method.getName()) ? requestObject : null));
        setField(analyzer, "dataObjectFieldMapper", mapper(RuleDataObjectFieldMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? Collections.singletonList(requestId) : null));

        String json = "{\"scriptVarRefs\":[{\"refCode\":\"api_features\",\"varId\":101,\"refType\":\"VARIABLE\"}]}";
        Method collectRefs = RuleFieldAnalyzer.class.getDeclaredMethod("collectExplicitRefs", String.class);
        collectRefs.setAccessible(true);
        Map<String, Object> refs = (Map<String, Object>) collectRefs.invoke(analyzer, json);
        Method applyInputRef = RuleFieldAnalyzer.class.getDeclaredMethod("applyExplicitRef",
                RuleDefinitionInputField.class, Map.class);
        applyInputRef.setAccessible(true);

        RuleDefinitionInputField property = inputField(
                "api_features.credit_score_v1", null, null, null);
        applyInputRef.invoke(analyzer, property, refs);

        Map<String, Object> apiMeta = new HashMap<>();
        apiMeta.put("id", 101L);
        apiMeta.put("refType", "VARIABLE");
        apiMeta.put("scriptName", "api_features");
        apiMeta.put("varSource", "API");
        apiMeta.put("sourceConfig", "{\"apiConfigId\":7,\"paramMapping\":{\"id\":\"$.request.id\"}}");
        Map<String, Map<String, Object>> varMetaMap = new HashMap<>();
        varMetaMap.put("api_features", apiMeta);

        Method expand = RuleFieldAnalyzer.class.getDeclaredMethod("expandModelInputFields", List.class, Map.class);
        expand.setAccessible(true);
        List<RuleDefinitionInputField> result = (List<RuleDefinitionInputField>) expand.invoke(
                analyzer, Collections.singletonList(property), varMetaMap);

        assertEquals(Long.valueOf(101L), property.getVarId());
        assertEquals("VARIABLE", property.getRefType());
        assertEquals(Collections.singletonList("request.id"), names(result));
        assertEquals(Long.valueOf(201L), result.get(0).getVarId());
        assertEquals("DATA_OBJECT", result.get(0).getRefType());
    }

    @Test
    public void directApiObjectPropertyKeepsItsStableVariableReferenceForRuntimeResolution() throws Exception {
        RuleVariable variable = new RuleVariable();
        variable.setId(101L);
        variable.setProjectId(4L);
        variable.setScope("PROJECT");
        variable.setScriptName("api_features");
        variable.setVarCode("api_features");
        variable.setVarType("OBJECT");
        variable.setVarSource("API");
        variable.setStatus(1);
        setField(analyzer, "qlScriptFieldResolver",
                scriptResolver(Collections.singletonMap(101L, variable)));
        String json = "{\"script\":\"credit_score_v1 = api_features.credit_score_v1;\","
                + "\"scriptVarRefs\":[{\"refCode\":\"api_features\",\"varId\":101,"
                + "\"refType\":\"VARIABLE\"}]}";

        List<RuleDefinitionInputField> fields = analyzer.extractDirectModelInputFields(json, "SCRIPT");

        assertEquals(1, fields.size());
        assertEquals("api_features.credit_score_v1", fields.get(0).getScriptName());
        assertEquals("VARIABLE", fields.get(0).getRefType());
        assertEquals(Long.valueOf(101L), fields.get(0).getVarId());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void apiRequestObjectExpansionAlsoIncludesApiConfigDependencies() throws Exception {
        RuleExternalApiConfig apiConfig = new RuleExternalApiConfig();
        apiConfig.setId(7L);
        apiConfig.setRequestObjectId(301L);
        apiConfig.setHeaderConfig("{\"X-Tenant\":\"$.tenantId\"}");

        RuleDataObject requestObject = new RuleDataObject();
        requestObject.setId(301L);
        requestObject.setScriptName("request");
        RuleDataObjectField requestId = new RuleDataObjectField();
        requestId.setId(201L);
        requestId.setObjectId(301L);
        requestId.setScriptName("id");
        requestId.setVarCode("id");
        requestId.setVarLabel("请求ID");
        requestId.setVarType("STRING");
        requestId.setStatus(1);

        setField(analyzer, "externalApiConfigMapper", mapper(RuleExternalApiConfigMapper.class,
                (proxy, method, args) -> "selectById".equals(method.getName()) ? apiConfig : null));
        setField(analyzer, "dataObjectMapper", mapper(RuleDataObjectMapper.class,
                (proxy, method, args) -> "selectById".equals(method.getName()) ? requestObject : null));
        setField(analyzer, "dataObjectFieldMapper", mapper(RuleDataObjectFieldMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? Collections.singletonList(requestId) : null));
        setField(analyzer, "variableSourceResolver", new VariableSourceResolver() {
            @Override
            Set<String> collectVariableDependencies(RuleVariable variable) {
                return Collections.singleton("tenantId");
            }
        });

        Map<String, Object> apiMeta = new HashMap<>();
        apiMeta.put("id", 101L);
        apiMeta.put("refType", "VARIABLE");
        apiMeta.put("scriptName", "api_features");
        apiMeta.put("varSource", "API");
        apiMeta.put("sourceConfig", "{\"apiConfigId\":7}");
        Map<String, Map<String, Object>> varMetaMap = new HashMap<>();
        varMetaMap.put("api_features", apiMeta);

        Method expand = RuleFieldAnalyzer.class.getDeclaredMethod(
                "expandModelInputFields", List.class, Map.class);
        expand.setAccessible(true);
        RuleDefinitionInputField apiField = inputField(
                "api_features", "VARIABLE", "API", 101L);
        List<RuleDefinitionInputField> result = (List<RuleDefinitionInputField>) expand.invoke(
                analyzer, Collections.singletonList(apiField), varMetaMap);

        assertTrue(names(result).contains("request.id"));
        assertTrue(names(result).contains("tenantId"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void apiRequestObjectExpansionPreservesNestedFieldPaths() throws Exception {
        RuleExternalApiConfig apiConfig = new RuleExternalApiConfig();
        apiConfig.setId(7L);
        apiConfig.setRequestObjectId(301L);
        RuleDataObject requestObject = new RuleDataObject();
        requestObject.setId(301L);
        requestObject.setScriptName("request");
        RuleDataObjectField profile = new RuleDataObjectField();
        profile.setId(201L);
        profile.setObjectId(301L);
        profile.setScriptName("profile");
        profile.setVarCode("profile");
        profile.setVarType("OBJECT");
        profile.setStatus(1);
        RuleDataObjectField name = new RuleDataObjectField();
        name.setId(202L);
        name.setObjectId(301L);
        name.setParentFieldId(201L);
        name.setScriptName("name");
        name.setVarCode("name");
        name.setVarType("STRING");
        name.setStatus(1);
        setField(analyzer, "externalApiConfigMapper", mapper(RuleExternalApiConfigMapper.class,
                (proxy, method, args) -> "selectById".equals(method.getName()) ? apiConfig : null));
        setField(analyzer, "dataObjectMapper", mapper(RuleDataObjectMapper.class,
                (proxy, method, args) -> "selectById".equals(method.getName()) ? requestObject : null));
        setField(analyzer, "dataObjectFieldMapper", mapper(RuleDataObjectFieldMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? Arrays.asList(profile, name) : null));

        Map<String, Object> apiMeta = new HashMap<>();
        apiMeta.put("id", 101L);
        apiMeta.put("refType", "VARIABLE");
        apiMeta.put("scriptName", "api_features");
        apiMeta.put("varSource", "API");
        apiMeta.put("sourceConfig", "{\"apiConfigId\":7}");
        Map<String, Map<String, Object>> varMetaMap = new HashMap<>();
        varMetaMap.put("api_features", apiMeta);

        Method expand = RuleFieldAnalyzer.class.getDeclaredMethod(
                "expandModelInputFields", List.class, Map.class);
        expand.setAccessible(true);
        RuleDefinitionInputField apiField = inputField(
                "api_features", "VARIABLE", "API", 101L);
        List<RuleDefinitionInputField> result = (List<RuleDefinitionInputField>) expand.invoke(
                analyzer, Collections.singletonList(apiField), varMetaMap);

        assertTrue(names(result).contains("request.profile.name"));
        assertFalse(names(result).contains("request.name"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void dbSourceExpansionPreservesProjectScopeForDependencyLookup() throws Exception {
        AtomicReference<Long> observedProjectId = new AtomicReference<>();
        setField(analyzer, "variableSourceResolver", new VariableSourceResolver() {
            @Override
            Set<String> collectVariableDependencies(RuleVariable variable) {
                observedProjectId.set(variable.getProjectId());
                return Collections.singleton("customerId");
            }
        });

        Map<String, Object> dbMeta = new HashMap<>();
        dbMeta.put("id", 101L);
        dbMeta.put("refType", "VARIABLE");
        dbMeta.put("scriptName", "db_score");
        dbMeta.put("varSource", "DB");
        dbMeta.put("projectId", 4L);
        dbMeta.put("sourceConfig", "{\"params\":[]}");
        Map<String, Map<String, Object>> varMetaMap = new HashMap<>();
        varMetaMap.put("db_score", dbMeta);

        Method expand = RuleFieldAnalyzer.class.getDeclaredMethod(
                "expandModelInputFields", List.class, Map.class);
        expand.setAccessible(true);
        RuleDefinitionInputField dbField = inputField(
                "db_score", "VARIABLE", "DB", 101L);
        List<RuleDefinitionInputField> result = (List<RuleDefinitionInputField>) expand.invoke(
                analyzer, Collections.singletonList(dbField), varMetaMap);

        assertEquals(Long.valueOf(4L), observedProjectId.get());
        assertTrue(names(result).contains("customerId"));
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
        age.setValidationRuleIds("[1,2]");
        age.setValidationOverride(1);
        RuleDefinitionInputField scoreField = inputField("HYBASE_X115", "DATA_OBJECT", "DATA_OBJECT", 25L);
        setField(analyzer, "inputFieldMapper", inputFieldMapper(Arrays.asList(age, scoreField)));

        String json = "{\"nodes\":[{\"actionData\":[{\"type\":\"rule-call\",\"ruleId\":1}]}]}";
        Method loadRuleCallInputs = RuleFieldAnalyzer.class.getDeclaredMethod("loadRuleCallInputFields", String.class);
        loadRuleCallInputs.setAccessible(true);
        List<RuleDefinitionInputField> calledInputs = (List<RuleDefinitionInputField>) loadRuleCallInputs.invoke(analyzer, json);
        assertTrue(names(calledInputs).contains("age"));
        assertTrue(names(calledInputs).contains("HYBASE_X115"));
        RuleDefinitionInputField inheritedAge = calledInputs.stream()
                .filter(field -> "age".equals(field.getScriptName())).findFirst().get();
        assertEquals("[1,2]", inheritedAge.getValidationRuleIds());
        assertEquals("子规则配置复制到父规则后不能被误判为父规则覆盖",
                Integer.valueOf(0), inheritedAge.getValidationOverride());

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
    public void lifecycleRuleCallExpansionUsesFrozenSnapshotWithoutMainProjectionQuery()
            throws Exception {
        AtomicInteger currentProjectionLoads = new AtomicInteger();
        RuleDefinitionInputField stale = inputField(
                "stale_projection", "VARIABLE", "INPUT", 8L);
        setField(analyzer, "inputFieldMapper", mapper(
                RuleDefinitionInputFieldMapper.class, (proxy, method, args) -> {
                    if ("selectList".equals(method.getName())) {
                        currentProjectionLoads.incrementAndGet();
                        return Collections.singletonList(stale);
                    }
                    return null;
                }));
        setField(analyzer, "outputFieldMapper", outputFieldMapper(Collections.emptyList()));
        setField(analyzer, "ruleVariableMapper", mapper(RuleVariableMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? Collections.emptyList() : null));
        setField(analyzer, "dataObjectMapper", mapper(RuleDataObjectMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? Collections.emptyList() : null));
        setField(analyzer, "dataObjectFieldMapper", mapper(RuleDataObjectFieldMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? Collections.emptyList() : null));
        setField(analyzer, "modelMapper", mapper(RuleModelMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? Collections.emptyList() : null));
        RuleDefinitionInputField frozen = inputField(
                "frozen_input", "VARIABLE", "INPUT", 7L);
        setField(analyzer, "publishedFieldSnapshotResolver",
                new PublishedRuleFieldSnapshotResolver() {
                    @Override
                    public RuleFieldAnalyzer.ResolvedFields resolve(Long definitionId) {
                        return new RuleFieldAnalyzer.ResolvedFields(
                                Collections.singletonList(frozen), Collections.emptyList());
                    }
                });

        RuleFieldAnalyzer.ResolvedFields fields = analyzer.resolveFields(
                null,
                "{\"nodes\":[{\"actionData\":[{\"type\":\"rule-call\",\"ruleId\":101}]}]}",
                "FLOW", 4L);

        assertTrue(names(fields.getInputFields()).contains("frozen_input"));
        assertFalse(names(fields.getInputFields()).contains("stale_projection"));
        assertEquals(0, currentProjectionLoads.get());
    }

    @Test
    public void parentStructuredOutputIsNotMadeLocalBySameNamedScriptChildOutput()
            throws Exception {
        configureEmptyStructuredFieldMetadata();
        RuleDefinitionOutputField childLocal = new RuleDefinitionOutputField();
        childLocal.setFieldName("shared_result");
        childLocal.setScriptName("shared_result");
        childLocal.setFieldType("NUMBER");
        childLocal.setStatus(1);
        setField(analyzer, "publishedFieldSnapshotResolver",
                new PublishedRuleFieldSnapshotResolver() {
                    @Override
                    public RuleFieldAnalyzer.ResolvedFields resolve(Long definitionId) {
                        return new RuleFieldAnalyzer.ResolvedFields(
                                Collections.emptyList(),
                                Collections.singletonList(childLocal),
                                Collections.emptyList(),
                                Collections.singleton("shared_result"),
                                Collections.emptyMap(), Collections.emptyMap());
                    }
                });

        RuleFieldAnalyzer.ResolvedFields fields = analyzer.resolveFields(
                null,
                "{\"nodes\":[{\"actionData\":["
                        + "{\"type\":\"assign\",\"target\":\"shared_result\",\"value\":\"1\"},"
                        + "{\"type\":\"rule-call\",\"ruleId\":101}]}]}",
                "FLOW", 4L);

        RuleDefinitionOutputField retained = fields.getOutputFields().stream()
                .filter(field -> "shared_result".equals(field.getScriptName()))
                .findFirst().orElseThrow();
        assertFalse(fields.isLocalOutput(retained));
        assertFalse(fields.getLocalOutputNames().contains("shared_result"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void outerValidationOverrideWinsWhileNonOverrideInheritsChildRules() throws Exception {
        Method merge = RuleFieldAnalyzer.class.getDeclaredMethod("mergeInputValidation",
                RuleDefinitionInputField.class, RuleDefinitionInputField.class);
        merge.setAccessible(true);

        RuleDefinitionInputField inherited = inputField("age", "VARIABLE", "INPUT", 37L);
        inherited.setValidationOverride(0);
        RuleDefinitionInputField child = inputField("age", "VARIABLE", "INPUT", 37L);
        child.setValidationRuleIds("[1]");
        child.setValidationOverride(0);
        merge.invoke(analyzer, inherited, child);
        assertEquals("[1]", inherited.getValidationRuleIds());

        RuleDefinitionInputField outer = inputField("age", "VARIABLE", "INPUT", 37L);
        outer.setValidationRuleIds("[9]");
        outer.setValidationOverride(1);
        merge.invoke(analyzer, outer, child);
        assertEquals("[9]", outer.getValidationRuleIds());

        RuleDefinitionInputField explicitlyCleared = inputField("age", "VARIABLE", "INPUT", 37L);
        explicitlyCleared.setValidationRuleIds("[]");
        explicitlyCleared.setValidationOverride(1);
        merge.invoke(analyzer, explicitlyCleared, child);
        assertEquals("[]", explicitlyCleared.getValidationRuleIds());
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
    public void fieldMetadataNeverAssociatesByCodeWithoutIdAndRefType() throws Exception {
        RuleDefinitionInputField field = inputField("riskFactor", null, "INPUT", null);
        Map<String, Map<String, Object>> varMetaMap = new HashMap<>();
        varMetaMap.put("riskfactor", leafMeta("riskFactor", "INPUT", 241L));

        Method enrich = RuleFieldAnalyzer.class.getDeclaredMethod("enrichFieldFromMeta",
                RuleDefinitionInputField.class, Map.class, Map.class, Map.class);
        enrich.setAccessible(true);
        enrich.invoke(analyzer, field, varMetaMap,
                Collections.singletonMap("riskFactor", 241L),
                Collections.singletonMap("riskFactor", "VARIABLE"));

        assertNull(field.getVarId());
        assertNull(field.getRefType());
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

    @Test
    @SuppressWarnings("unchecked")
    public void modelOperandDependenciesCarryModelFieldDefaults() throws Exception {
        RuleModelInputField modelField = new RuleModelInputField();
        modelField.setFieldName("age");
        modelField.setFieldType("INTEGER");
        modelField.setDefaultValue("18");
        modelField.setValidValues("[18,35]");
        modelField.setSourceOperand("{\"kind\":\"REFERENCE\",\"refId\":37,"
                + "\"refType\":\"VARIABLE\",\"code\":\"age\","
                + "\"valueType\":\"INTEGER\"}");

        Method copy = RuleFieldAnalyzer.class.getDeclaredMethod(
                "copyModelInputFields", RuleModelInputField.class);
        copy.setAccessible(true);
        List<RuleDefinitionInputField> fields = (List<RuleDefinitionInputField>) copy.invoke(
                analyzer, modelField);

        assertEquals(1, fields.size());
        assertEquals("18", fields.get(0).getDefaultValue());
        assertEquals("[18,35]", fields.get(0).getValidValues());
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

    private static RuleDataObjectField apiShapeField(Long id, Long objectId,
                                                     Long parentId, String code,
                                                     String type) {
        RuleDataObjectField field = new RuleDataObjectField();
        field.setId(id);
        field.setProjectId(4L);
        field.setScope("PROJECT");
        field.setObjectId(objectId);
        field.setParentFieldId(parentId);
        field.setVarCode(code);
        field.setVarLabel(code);
        field.setScriptName(code);
        field.setVarType(type);
        field.setSortOrder(id.intValue());
        field.setStatus(1);
        return field;
    }

    private static String outputType(RuleFieldAnalyzer.ResolvedFields fields, String name) {
        return fields.getOutputFields().stream()
                .filter(field -> name.equals(field.getScriptName()))
                .findFirst()
                .map(RuleDefinitionOutputField::getFieldType)
                .orElse(null);
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

    private static RuleVariable inputVariable(Long id, String scriptName) {
        RuleVariable variable = new RuleVariable();
        variable.setId(id);
        variable.setProjectId(0L);
        variable.setScope("GLOBAL");
        variable.setVarCode(scriptName);
        variable.setScriptName(scriptName);
        variable.setVarLabel(scriptName);
        variable.setVarType("STRING");
        variable.setVarSource("INPUT");
        variable.setStatus(1);
        return variable;
    }

    private static QLScriptFieldResolver scriptResolver(Map<Long, RuleVariable> variables) {
        return new QLScriptFieldResolver(
                mapper(RuleVariableMapper.class,
                        (proxy, method, args) -> "selectById".equals(method.getName())
                                ? variables.get(((Number) args[0]).longValue()) : null),
                mapper(RuleDataObjectMapper.class, (proxy, method, args) -> null),
                mapper(RuleDataObjectFieldMapper.class, (proxy, method, args) -> null),
                mapper(RuleModelMapper.class, (proxy, method, args) -> null),
                mapper(RuleModelOutputFieldMapper.class, (proxy, method, args) -> null));
    }

    private void configureEmptyStructuredFieldMetadata() throws Exception {
        setField(analyzer, "ruleVariableMapper", mapper(RuleVariableMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? Collections.emptyList() : null));
        setField(analyzer, "dataObjectMapper", mapper(RuleDataObjectMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? Collections.emptyList() : null));
        setField(analyzer, "dataObjectFieldMapper", mapper(RuleDataObjectFieldMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? Collections.emptyList() : null));
        setField(analyzer, "modelMapper", mapper(RuleModelMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? Collections.emptyList() : null));
        setField(analyzer, "modelOutputFieldMapper", mapper(
                RuleModelOutputFieldMapper.class,
                (proxy, method, args) -> "selectList".equals(method.getName())
                        ? Collections.emptyList() : null));
    }

    private ScriptShapeFixture scoreApiFixture() {
        ScriptShapeFixture fixture = new ScriptShapeFixture(analyzer);
        fixture.apiObjectVariable(302L, "api_features", 7L);
        fixture.apiResponseObject(7L, 10L);
        fixture.fields(apiShapeField(
                101L, 10L, null, "score", "NUMBER"));
        return fixture;
    }

    private ScriptShapeFixture nestedListApiFixture() {
        ScriptShapeFixture fixture = new ScriptShapeFixture(analyzer);
        fixture.apiObjectVariable(302L, "api_features", 7L);
        fixture.apiResponseObject(7L, 10L);
        fixture.fields(
                apiShapeField(141L, 10L, null, "outer", "OBJECT"),
                apiShapeField(142L, 10L, 141L, "items", "LIST"),
                apiShapeField(143L, 10L, 142L, "score", "NUMBER"));
        return fixture;
    }

    private RuleFieldAnalyzer.ResolvedFields resolveScoreScript(
            ScriptShapeFixture fixture) throws Exception {
        fixture.configure();
        return analyzer.resolveFields(
                null,
                scriptJson("score = api_features.score;"
                        + " _result = {\"score\": score}; _result"),
                "SCRIPT", 4L);
    }

    private static String scriptJson(String script) {
        return "{\"script\":" + com.alibaba.fastjson.JSON.toJSONString(script)
                + ",\"scriptVarRefs\":[{\"refCode\":\"api_features\","
                + "\"varId\":302,\"refType\":\"VARIABLE\"}]}";
    }

    private static void assertDiagnostic(
            RuleFieldAnalyzer.ResolvedFields fields,
            String code,
            String path) {
        assertTrue(fields.getDiagnostics().stream()
                .anyMatch(issue -> code.equals(issue.getCode())
                        && path.equals(issue.getPath())));
    }

    private static final class ScriptShapeFixture {

        private final RuleFieldAnalyzer analyzer;
        private final Map<Long, RuleVariable> variables = new HashMap<>();
        private final Map<Long, RuleDataObject> objects = new HashMap<>();
        private final Map<Long, RuleDataObjectField> fields = new HashMap<>();
        private final Map<Long, RuleExternalApiConfig> apiConfigs = new HashMap<>();
        private final Map<Long, RuleExternalDatasource> datasources = new HashMap<>();
        private final AtomicInteger apiInvocations = new AtomicInteger();

        private ScriptShapeFixture(RuleFieldAnalyzer analyzer) {
            this.analyzer = analyzer;
        }

        private void apiObjectVariable(Long id, String scriptName, Long apiConfigId) {
            RuleVariable variable = new RuleVariable();
            variable.setId(id);
            variable.setProjectId(4L);
            variable.setScope("PROJECT");
            variable.setVarCode(scriptName);
            variable.setScriptName(scriptName);
            variable.setVarType("OBJECT");
            variable.setVarSource("API");
            variable.setSourceConfig("{\"apiConfigId\":" + apiConfigId + "}");
            variable.setStatus(1);
            variables.put(id, variable);
        }

        private void apiResponseObject(Long apiConfigId, Long objectId) {
            RuleExternalApiConfig config = new RuleExternalApiConfig();
            config.setId(apiConfigId);
            config.setDatasourceId(70L);
            config.setResponseObjectId(objectId);
            config.setStatus(1);
            apiConfigs.put(apiConfigId, config);

            RuleExternalDatasource datasource = new RuleExternalDatasource();
            datasource.setId(70L);
            datasource.setProjectId(4L);
            datasource.setScope("PROJECT");
            datasource.setDatasourceCode("datasource_70");
            datasource.setDatasourceName("datasource_70");
            datasource.setStatus(1);
            datasources.put(70L, datasource);

            RuleDataObject object = new RuleDataObject();
            object.setId(objectId);
            object.setProjectId(4L);
            object.setScope("PROJECT");
            object.setObjectCode("response_" + objectId);
            object.setScriptName("response_" + objectId);
            object.setStatus(1);
            objects.put(objectId, object);
        }

        private void apiConfigStatus(Long apiConfigId, Integer status) {
            apiConfigs.get(apiConfigId).setStatus(status);
        }

        private void datasourceStatus(Long datasourceId, Integer status) {
            datasources.get(datasourceId).setStatus(status);
        }

        private void datasourceProject(
                Long datasourceId, Long projectId, String scope) {
            RuleExternalDatasource datasource = datasources.get(datasourceId);
            datasource.setProjectId(projectId);
            datasource.setScope(scope);
        }

        private void fields(RuleDataObjectField... values) {
            Arrays.stream(values).forEach(value -> fields.put(value.getId(), value));
        }

        private void configure() throws Exception {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new Configuration(), ""), RuleVariable.class);
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new Configuration(), ""), RuleDataObject.class);
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new Configuration(), ""), RuleDataObjectField.class);
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new Configuration(), ""), RuleModel.class);
            RuleVariableMapper variableMapper = mapper(RuleVariableMapper.class,
                    (proxy, method, args) -> {
                        if ("selectById".equals(method.getName())) {
                            return variables.get(((Number) args[0]).longValue());
                        }
                        return "selectList".equals(method.getName())
                                ? new ArrayList<>(variables.values()) : null;
                    });
            RuleDataObjectMapper objectMapper = mapper(RuleDataObjectMapper.class,
                    (proxy, method, args) -> {
                        if ("selectById".equals(method.getName())) {
                            return objects.get(((Number) args[0]).longValue());
                        }
                        return "selectList".equals(method.getName())
                                ? new ArrayList<>(objects.values()) : null;
                    });
            RuleDataObjectFieldMapper fieldMapper = mapper(RuleDataObjectFieldMapper.class,
                    (proxy, method, args) -> {
                        if ("selectById".equals(method.getName())) {
                            return fields.get(((Number) args[0]).longValue());
                        }
                        return "selectList".equals(method.getName())
                                ? new ArrayList<>(fields.values()) : null;
                    });
            RuleExternalApiConfigMapper apiMapper =
                    mapper(RuleExternalApiConfigMapper.class,
                            (proxy, method, args) -> "selectById".equals(method.getName())
                                    ? apiConfigs.get(((Number) args[0]).longValue()) : null);
            RuleExternalDatasourceMapper datasourceMapper =
                    mapper(RuleExternalDatasourceMapper.class,
                            (proxy, method, args) -> "selectById".equals(method.getName())
                                    ? datasources.get(((Number) args[0]).longValue()) : null);
            RuleModelMapper ruleModelMapper = mapper(RuleModelMapper.class,
                    (proxy, method, args) -> "selectList".equals(method.getName())
                            ? Collections.emptyList() : null);
            RuleModelOutputFieldMapper outputMapper =
                    mapper(RuleModelOutputFieldMapper.class,
                            (proxy, method, args) -> "selectList".equals(method.getName())
                                    ? Collections.emptyList() : null);
            VariableSourceResolver sourceResolver = new VariableSourceResolver();
            setField(sourceResolver, "apiConfigMapper", apiMapper);
            setField(sourceResolver, "externalApiInvokeService",
                    new ExternalApiInvokeService() {
                        @Override
                        public Map<String, Object> invoke(
                                Long apiConfigId, Map<String, Object> params) {
                            apiInvocations.incrementAndGet();
                            return Collections.emptyMap();
                        }

                        @Override
                        public Map<String, Object> invoke(
                                RuleExternalApiConfig apiConfig, Map<String, Object> params) {
                            apiInvocations.incrementAndGet();
                            return Collections.emptyMap();
                        }
                    });

            setField(analyzer, "ruleVariableMapper", variableMapper);
            setField(analyzer, "dataObjectMapper", objectMapper);
            setField(analyzer, "dataObjectFieldMapper", fieldMapper);
            setField(analyzer, "externalApiConfigMapper", apiMapper);
            setField(analyzer, "externalDatasourceMapper", datasourceMapper);
            setField(analyzer, "modelMapper", ruleModelMapper);
            setField(analyzer, "modelOutputFieldMapper", outputMapper);
            setField(analyzer, "variableSourceResolver", sourceResolver);
            setField(analyzer, "qlScriptFieldResolver", new QLScriptFieldResolver(
                    variableMapper, objectMapper, fieldMapper,
                    ruleModelMapper, outputMapper));
            setField(analyzer, "dataObjectSchemaResolver",
                    new DataObjectSchemaResolver(objectMapper, fieldMapper));
        }
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
