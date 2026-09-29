package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.dto.RuleValidationIssue;
import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleModelOutputField;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.mapper.RuleDataObjectMapper;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleModelMapper;
import com.hengshucredit.rule.server.mapper.RuleModelOutputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleVariableMapper;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class QLScriptFieldResolverTest {

    private static final String ICEKREDIT_SCRIPT =
            "credit_score_v1 = icekredit_vn_credit_profile_features.credit_score_v1;\n"
                    + "credit_apply_count_1m = "
                    + "icekredit_vn_credit_profile_features.credit_apply_count_1m;\n"
                    + "_result = {\"credit_score_v1\": credit_score_v1,"
                    + "\"credit_apply_count_1m\": credit_apply_count_1m}\n_result";

    private Fixture fixture;

    @Before
    public void setUp() {
        fixture = new Fixture();
    }

    @Test
    public void stableApiObjectVariableBindsBothPropertyReads() {
        RuleVariable variable = activeVariable(
                302L, 4L, "icekredit_vn_credit_profile_features", "OBJECT", "API");
        fixture.variable(302L, variable);
        String modelJson = JSON.toJSONString(Map.of(
                "script", ICEKREDIT_SCRIPT,
                "scriptVarRefs", List.of(Map.of(
                        "refCode", "icekredit_vn_credit_profile_features",
                        "varId", 302L,
                        "refType", "VARIABLE"))));

        RuleFieldAnalyzer.ResolvedFields fields = fixture.resolver.resolve(modelJson, 4L);

        assertEquals(Arrays.asList(
                        "icekredit_vn_credit_profile_features.credit_score_v1",
                        "icekredit_vn_credit_profile_features.credit_apply_count_1m"),
                inputNames(fields));
        assertTrue(fields.getInputFields().stream()
                .allMatch(field -> Long.valueOf(302L).equals(field.getVarId())
                        && "VARIABLE".equals(field.getRefType())));
        assertEquals(Arrays.asList("credit_score_v1", "credit_apply_count_1m"),
                outputNames(fields));
        assertTrue(fields.getOutputFields().stream()
                .allMatch(field -> field.getVarId() == null && field.getRefType() == null));
        assertEquals(Set.of("credit_score_v1", "credit_apply_count_1m"),
                fields.getLocalOutputNames());
    }

    @Test
    public void matchingVariableCodeWithoutScriptVarRefDoesNotBindId() {
        fixture.variable(302L, activeVariable(302L, 4L,
                "icekredit_vn_credit_profile_features", "OBJECT", "API"));

        RuleFieldAnalyzer.ResolvedFields fields = fixture.resolver.resolve(
                model(ICEKREDIT_SCRIPT, Collections.emptyList()), 4L);

        assertNull(fields.getInputFields().get(0).getVarId());
        assertTrue(codes(fields).contains("SCRIPT_INPUT_REF_MISSING"));
    }

    @Test
    public void missingStableIdReturnsReferenceNotFound() {
        RuleFieldAnalyzer.ResolvedFields fields = fixture.resolver.resolve(
                model(ICEKREDIT_SCRIPT, refs(ref(999999L, "VARIABLE",
                        "icekredit_vn_credit_profile_features"))), 4L);

        assertTrue(codes(fields).contains("REFERENCE_NOT_FOUND"));
    }

    @Test
    public void constantIdDeclaredAsVariableReturnsReferenceTypeMismatch() {
        fixture.variable(302L, activeConstant(302L, 4L,
                "icekredit_vn_credit_profile_features"));

        RuleFieldAnalyzer.ResolvedFields fields = fixture.resolver.resolve(
                model(ICEKREDIT_SCRIPT, refs(ref(302L, "VARIABLE",
                        "icekredit_vn_credit_profile_features"))), 4L);

        assertTrue(codes(fields).contains("REFERENCE_TYPE_MISMATCH"));
    }

    @Test
    public void projectScopedReferenceFromAnotherProjectIsRejected() {
        fixture.variable(302L, activeVariable(302L, 99L,
                "icekredit_vn_credit_profile_features", "OBJECT", "API"));

        RuleFieldAnalyzer.ResolvedFields fields = fixture.resolver.resolve(
                model(ICEKREDIT_SCRIPT, refs(ref(302L, "VARIABLE",
                        "icekredit_vn_credit_profile_features"))), 4L);

        assertTrue(codes(fields).contains("REFERENCE_TYPE_MISMATCH"));
    }

    @Test
    public void refCodeCannotOverrideCanonicalScriptRootLoadedById() {
        fixture.variable(302L, activeVariable(302L, 4L,
                "different_root", "OBJECT", "API"));

        RuleFieldAnalyzer.ResolvedFields fields = fixture.resolver.resolve(
                model(ICEKREDIT_SCRIPT, refs(ref(302L, "VARIABLE",
                        "icekredit_vn_credit_profile_features"))), 4L);

        assertTrue(codes(fields).contains("REFERENCE_TYPE_MISMATCH"));
    }

    @Test
    public void validationIssueKeepsStructuredReferenceMetadata() {
        RuleValidationIssue issue = RuleValidationIssue.error(
                        "REFERENCE_NOT_FOUND", "$.scriptVarRefs[0]", "引用不存在")
                .withRevisionId(12L)
                .withReference("VARIABLE", 302L)
                .withSafeDetail("fieldPath", "icekredit_vn_credit_profile_features.credit_score_v1")
                .withSafeDetail("digest", "sha256:fixture");

        assertEquals(Long.valueOf(12L), issue.getRevisionId());
        assertEquals("VARIABLE", issue.getRefType());
        assertEquals(Long.valueOf(302L), issue.getResourceId());
        assertEquals("icekredit_vn_credit_profile_features.credit_score_v1",
                issue.getDetails().get("fieldPath"));
        assertEquals("sha256:fixture", issue.getDetails().get("digest"));
    }

    @Test
    public void validationIssueRejectsSensitiveDetailKeys() {
        for (String key : Arrays.asList("token", "apiSecret", "passwordHash", "rawBody")) {
            assertThrows(IllegalArgumentException.class,
                    () -> RuleValidationIssue.error("TEST", "$", "test")
                            .withSafeDetail(key, "must-not-be-stored"));
        }
    }

    @Test
    public void malformedScriptReferenceEntriesProduceDeterministicDiagnostics() {
        List<Object> malformedRefs = Arrays.asList(
                "not-an-object",
                Map.of("refType", "VARIABLE", "refCode", "input"),
                Map.of("varId", 1L, "refCode", "input"),
                Map.of("varId", 1L, "refType", "VARIABLE"));
        String modelJson = JSON.toJSONString(Map.of(
                "script", "output = input; output",
                "scriptVarRefs", malformedRefs));

        RuleFieldAnalyzer.ResolvedFields fields = fixture.resolver.resolve(modelJson, 4L);

        assertEquals(4L, fields.getDiagnostics().stream()
                .filter(issue -> "SCRIPT_REFERENCE_INVALID".equals(issue.getCode()))
                .count());
        assertNull(fields.getInputFields().get(0).getVarId());
        assertTrue(codes(fields).contains("SCRIPT_INPUT_REF_MISSING"));
    }

    @Test
    public void conflictingIdentitiesForSameScriptRootNeverBindRegardlessOfOrder() {
        fixture.variable(1L, activeVariable(1L, 4L, "input", "NUMBER", "INPUT"));
        fixture.variable(2L, activeVariable(2L, 4L, "input", "NUMBER", "INPUT"));
        Map<String, Object> first = ref(1L, "VARIABLE", "input");
        Map<String, Object> second = ref(2L, "VARIABLE", "input");

        RuleFieldAnalyzer.ResolvedFields forward = fixture.resolver.resolve(
                model("output = input; output", refs(first, second)), 4L);
        RuleFieldAnalyzer.ResolvedFields reversed = fixture.resolver.resolve(
                model("output = input; output", refs(second, first)), 4L);

        assertTrue(codes(forward).contains("SCRIPT_REFERENCE_CONFLICT"));
        assertEquals(codes(forward), codes(reversed));
        assertNull(forward.getInputFields().get(0).getVarId());
        assertNull(reversed.getInputFields().get(0).getVarId());
    }

    @Test
    public void duplicateIdenticalIdentityForSameRootBindsOnceWithoutConflict() {
        fixture.variable(1L, activeVariable(1L, 4L, "input", "NUMBER", "INPUT"));
        Map<String, Object> stableRef = ref(1L, "VARIABLE", "input");

        RuleFieldAnalyzer.ResolvedFields fields = fixture.resolver.resolve(
                model("output = input; output", refs(stableRef, stableRef)), 4L);

        assertEquals(Long.valueOf(1L), fields.getInputFields().get(0).getVarId());
        assertFalse(codes(fields).contains("SCRIPT_REFERENCE_CONFLICT"));
    }

    @Test
    public void resolvedFieldsDefensivelySnapshotsAndExposesImmutableCollections() {
        List<RuleDefinitionInputField> inputs = new ArrayList<>();
        inputs.add(new RuleDefinitionInputField());
        List<RuleDefinitionOutputField> outputs = new ArrayList<>();
        outputs.add(new RuleDefinitionOutputField());
        List<RuleValidationIssue> diagnostics = new ArrayList<>();
        diagnostics.add(RuleValidationIssue.error("TEST", "$", "test"));
        Set<String> locals = new LinkedHashSet<>(Collections.singleton("local"));
        List<String> nestedInputTypes = new ArrayList<>(Collections.singleton("STRING"));
        Map<String, Object> inputSchemas = new LinkedHashMap<>();
        inputSchemas.put("types", nestedInputTypes);
        Map<String, Object> outputSchemas = new LinkedHashMap<>();
        outputSchemas.put("local", "STRING");
        RuleFieldAnalyzer.ResolvedFields fields = new RuleFieldAnalyzer.ResolvedFields(
                inputs, outputs, diagnostics, locals, inputSchemas, outputSchemas);

        inputs.clear();
        outputs.clear();
        diagnostics.clear();
        locals.clear();
        nestedInputTypes.add("NUMBER");
        inputSchemas.clear();
        outputSchemas.clear();

        assertEquals(1, fields.getInputFields().size());
        assertEquals(1, fields.getOutputFields().size());
        assertEquals(1, fields.getDiagnostics().size());
        assertEquals(Collections.singleton("local"), fields.getLocalOutputNames());
        assertEquals(Collections.singletonList("STRING"),
                fields.getInputPropertySchemas().get("types"));
        assertEquals("STRING", fields.getOutputPropertySchemas().get("local"));
        assertThrows(UnsupportedOperationException.class,
                () -> fields.getInputFields().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> fields.getOutputFields().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> fields.getDiagnostics().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> fields.getLocalOutputNames().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> fields.getInputPropertySchemas().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> ((List<?>) fields.getInputPropertySchemas().get("types")).clear());
    }

    @Test
    public void stableBoundOutputIsNotLocalEvenWhenItsNameIsListed() {
        RuleDefinitionOutputField output = new RuleDefinitionOutputField();
        output.setScriptName("local");
        output.setVarId(7L);
        output.setRefType("VARIABLE");
        RuleFieldAnalyzer.ResolvedFields fields = new RuleFieldAnalyzer.ResolvedFields(
                Collections.emptyList(), Collections.singletonList(output),
                Collections.emptyList(), Collections.singleton("local"),
                Collections.emptyMap(), Collections.emptyMap());

        assertFalse(fields.isLocalOutput(output));
    }

    @Test
    public void topLevelDataObjectFieldUsesCanonicalObjectRoot() {
        fixture.dataObject(10L, activeDataObject(10L, 4L, "contact"));
        fixture.dataField(20L, activeDataField(20L, 4L, 10L, null, "name", "STRING"));

        RuleFieldAnalyzer.ResolvedFields fields = fixture.resolver.resolve(
                model("output = contact.name; output",
                        refs(ref(20L, "DATA_OBJECT", "contact.name"))), 4L);

        assertEquals(Long.valueOf(20L), fields.getInputFields().get(0).getVarId());
        assertEquals("DATA_OBJECT", fields.getInputFields().get(0).getRefType());
        assertEquals("STRING", fields.getInputFields().get(0).getFieldType());
        assertFalse(codes(fields).contains("REFERENCE_TYPE_MISMATCH"));
    }

    @Test
    public void nestedDataObjectFieldUsesCanonicalObjectAndParentPath() {
        fixture.dataObject(10L, activeDataObject(10L, 4L, "contact"));
        fixture.dataField(21L, activeDataField(21L, 4L, 10L, null, "address", "OBJECT"));
        fixture.dataField(22L, activeDataField(22L, 4L, 10L, 21L, "city", "STRING"));

        RuleFieldAnalyzer.ResolvedFields fields = fixture.resolver.resolve(
                model("output = contact.address.city; output",
                        refs(ref(22L, "DATA_OBJECT", "contact.address.city"))), 4L);

        assertEquals(Long.valueOf(22L), fields.getInputFields().get(0).getVarId());
        assertEquals("DATA_OBJECT", fields.getInputFields().get(0).getRefType());
    }

    @Test
    public void dataObjectDescendantUsesLongestStableParentBinding() {
        fixture.dataObject(10L, activeDataObject(10L, 4L, "contact"));
        fixture.dataField(21L, activeDataField(
                21L, 4L, 10L, null, "address", "OBJECT"));

        RuleFieldAnalyzer.ResolvedFields fields = fixture.resolver.resolve(
                model("output = contact.address.city; output",
                        refs(ref(21L, "DATA_OBJECT", "contact.address"))), 4L);

        assertEquals(Long.valueOf(21L), fields.getInputFields().get(0).getVarId());
        assertEquals("DATA_OBJECT", fields.getInputFields().get(0).getRefType());
        assertFalse(codes(fields).contains("SCRIPT_INPUT_REF_MISSING"));
    }

    @Test
    public void disabledDataObjectRejectsOtherwiseValidField() {
        RuleDataObject object = activeDataObject(10L, 4L, "contact");
        object.setStatus(0);
        fixture.dataObject(10L, object);
        fixture.dataField(20L, activeDataField(20L, 4L, 10L, null, "name", "STRING"));

        RuleFieldAnalyzer.ResolvedFields fields = fixture.resolver.resolve(
                model("output = contact.name; output",
                        refs(ref(20L, "DATA_OBJECT", "contact.name"))), 4L);

        assertTrue(codes(fields).contains("REFERENCE_TYPE_MISMATCH"));
        assertNull(fields.getInputFields().get(0).getVarId());
    }

    @Test
    public void crossProjectDataObjectRejectsOtherwiseValidField() {
        fixture.dataObject(10L, activeDataObject(10L, 99L, "contact"));
        fixture.dataField(20L, activeDataField(20L, 4L, 10L, null, "name", "STRING"));

        RuleFieldAnalyzer.ResolvedFields fields = fixture.resolver.resolve(
                model("output = contact.name; output",
                        refs(ref(20L, "DATA_OBJECT", "contact.name"))), 4L);

        assertTrue(codes(fields).contains("REFERENCE_TYPE_MISMATCH"));
        assertNull(fields.getInputFields().get(0).getVarId());
    }

    @Test
    public void missingDataObjectParentFieldIsRejected() {
        fixture.dataObject(10L, activeDataObject(10L, 4L, "contact"));
        fixture.dataField(20L, activeDataField(20L, 4L, 10L, 999L, "city", "STRING"));

        RuleFieldAnalyzer.ResolvedFields fields = fixture.resolver.resolve(
                model("output = contact.address.city; output",
                        refs(ref(20L, "DATA_OBJECT", "contact.address.city"))), 4L);

        assertTrue(codes(fields).contains("REFERENCE_NOT_FOUND"));
        assertNull(fields.getInputFields().get(0).getVarId());
    }

    @Test
    public void dataObjectParentFromDifferentObjectIsRejected() {
        fixture.dataObject(10L, activeDataObject(10L, 4L, "contact"));
        fixture.dataField(21L, activeDataField(21L, 4L, 11L, null, "address", "OBJECT"));
        fixture.dataField(22L, activeDataField(22L, 4L, 10L, 21L, "city", "STRING"));

        RuleFieldAnalyzer.ResolvedFields fields = fixture.resolver.resolve(
                model("output = contact.address.city; output",
                        refs(ref(22L, "DATA_OBJECT", "contact.address.city"))), 4L);

        assertTrue(codes(fields).contains("REFERENCE_TYPE_MISMATCH"));
        assertNull(fields.getInputFields().get(0).getVarId());
    }

    @Test
    public void cyclicDataObjectParentChainIsRejected() {
        fixture.dataObject(10L, activeDataObject(10L, 4L, "contact"));
        fixture.dataField(20L, activeDataField(20L, 4L, 10L, 21L, "city", "STRING"));
        fixture.dataField(21L, activeDataField(21L, 4L, 10L, 20L, "address", "OBJECT"));

        RuleFieldAnalyzer.ResolvedFields fields = fixture.resolver.resolve(
                model("output = contact.address.city; output",
                        refs(ref(20L, "DATA_OBJECT", "contact.address.city"))), 4L);

        assertTrue(codes(fields).contains("REFERENCE_TYPE_MISMATCH"));
        assertNull(fields.getInputFields().get(0).getVarId());
    }

    @Test
    public void activeProjectModelBindsByStableId() {
        fixture.model(30L, activeModel(30L, 4L, "risk_model"));

        RuleFieldAnalyzer.ResolvedFields fields = fixture.resolver.resolve(
                model("output = risk_model; output",
                        refs(ref(30L, "MODEL", "risk_model"))), 4L);

        assertEquals(Long.valueOf(30L), fields.getInputFields().get(0).getVarId());
        assertEquals("MODEL", fields.getInputFields().get(0).getRefType());
    }

    @Test
    public void modelOutputBindsOnlyThroughActiveParentModel() {
        fixture.model(30L, activeModel(30L, 4L, "risk_model"));
        fixture.modelOutput(31L, modelOutput(31L, 30L, "score", "DOUBLE"));

        RuleFieldAnalyzer.ResolvedFields fields = fixture.resolver.resolve(
                model("output = risk_model.score; output",
                        refs(ref(31L, "MODEL_OUTPUT", "risk_model.score"))), 4L);

        assertEquals(Long.valueOf(31L), fields.getInputFields().get(0).getVarId());
        assertEquals("MODEL_OUTPUT", fields.getInputFields().get(0).getRefType());
        assertEquals("DOUBLE", fields.getInputFields().get(0).getFieldType());
    }

    @Test
    public void modelOutputRejectsDisabledOrCrossProjectParentModel() {
        RuleModel disabled = activeModel(30L, 4L, "risk_model");
        disabled.setStatus(0);
        fixture.model(30L, disabled);
        fixture.modelOutput(31L, modelOutput(31L, 30L, "score", "DOUBLE"));
        RuleFieldAnalyzer.ResolvedFields disabledFields = fixture.resolver.resolve(
                model("output = risk_model.score; output",
                        refs(ref(31L, "MODEL_OUTPUT", "risk_model.score"))), 4L);

        fixture.model(30L, activeModel(30L, 99L, "risk_model"));
        RuleFieldAnalyzer.ResolvedFields crossProjectFields = fixture.resolver.resolve(
                model("output = risk_model.score; output",
                        refs(ref(31L, "MODEL_OUTPUT", "risk_model.score"))), 4L);

        assertTrue(codes(disabledFields).contains("REFERENCE_TYPE_MISMATCH"));
        assertNull(disabledFields.getInputFields().get(0).getVarId());
        assertTrue(codes(crossProjectFields).contains("REFERENCE_TYPE_MISMATCH"));
        assertNull(crossProjectFields.getInputFields().get(0).getVarId());
    }

    private static RuleVariable activeVariable(Long id, Long projectId, String scriptName,
                                               String varType, String varSource) {
        RuleVariable variable = new RuleVariable();
        variable.setId(id);
        variable.setProjectId(projectId);
        variable.setScope("PROJECT");
        variable.setVarCode(scriptName);
        variable.setScriptName(scriptName);
        variable.setVarType(varType);
        variable.setVarSource(varSource);
        variable.setStatus(1);
        return variable;
    }

    private static RuleVariable activeConstant(Long id, Long projectId, String scriptName) {
        RuleVariable variable = activeVariable(id, projectId, scriptName, "STRING", "CONSTANT");
        variable.setDefaultValue("constant");
        return variable;
    }

    private static RuleDataObject activeDataObject(Long id, Long projectId, String scriptName) {
        RuleDataObject object = new RuleDataObject();
        object.setId(id);
        object.setProjectId(projectId);
        object.setScope("PROJECT");
        object.setObjectCode(scriptName);
        object.setScriptName(scriptName);
        object.setStatus(1);
        return object;
    }

    private static RuleDataObjectField activeDataField(Long id, Long projectId, Long objectId,
                                                       Long parentFieldId, String scriptName,
                                                       String varType) {
        RuleDataObjectField field = new RuleDataObjectField();
        field.setId(id);
        field.setProjectId(projectId);
        field.setScope("PROJECT");
        field.setObjectId(objectId);
        field.setParentFieldId(parentFieldId);
        field.setVarCode(scriptName);
        field.setScriptName(scriptName);
        field.setVarType(varType);
        field.setStatus(1);
        return field;
    }

    private static RuleModel activeModel(Long id, Long projectId, String modelCode) {
        RuleModel model = new RuleModel();
        model.setId(id);
        model.setProjectId(projectId);
        model.setScope("PROJECT");
        model.setModelCode(modelCode);
        model.setStatus(1);
        return model;
    }

    private static RuleModelOutputField modelOutput(Long id, Long modelId, String fieldName,
                                                    String fieldType) {
        RuleModelOutputField output = new RuleModelOutputField();
        output.setId(id);
        output.setModelId(modelId);
        output.setFieldName(fieldName);
        output.setFieldType(fieldType);
        return output;
    }

    private static String model(String script, List<Map<String, Object>> scriptVarRefs) {
        return JSON.toJSONString(Map.of("script", script, "scriptVarRefs", scriptVarRefs));
    }

    @SafeVarargs
    private static List<Map<String, Object>> refs(Map<String, Object>... refs) {
        return Arrays.asList(refs);
    }

    private static Map<String, Object> ref(Long varId, String refType, String refCode) {
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("varId", varId);
        ref.put("refType", refType);
        ref.put("refCode", refCode);
        return ref;
    }

    private static List<String> inputNames(RuleFieldAnalyzer.ResolvedFields fields) {
        return fields.getInputFields().stream()
                .map(field -> field.getScriptName())
                .collect(Collectors.toList());
    }

    private static List<String> outputNames(RuleFieldAnalyzer.ResolvedFields fields) {
        return fields.getOutputFields().stream()
                .map(field -> field.getScriptName())
                .collect(Collectors.toList());
    }

    private static Set<String> codes(RuleFieldAnalyzer.ResolvedFields fields) {
        return fields.getDiagnostics().stream()
                .map(RuleValidationIssue::getCode)
                .collect(Collectors.toSet());
    }

    private static final class Fixture {

        private final Map<Long, RuleVariable> variables = new LinkedHashMap<>();
        private final Map<Long, RuleDataObject> dataObjects = new LinkedHashMap<>();
        private final Map<Long, RuleDataObjectField> dataFields = new LinkedHashMap<>();
        private final Map<Long, RuleModel> models = new LinkedHashMap<>();
        private final Map<Long, RuleModelOutputField> modelOutputs = new LinkedHashMap<>();
        private final QLScriptFieldResolver resolver;

        private Fixture() {
            resolver = new QLScriptFieldResolver(
                    byIdMapper(RuleVariableMapper.class, variables),
                    byIdMapper(RuleDataObjectMapper.class, dataObjects),
                    byIdMapper(RuleDataObjectFieldMapper.class, dataFields),
                    byIdMapper(RuleModelMapper.class, models),
                    byIdMapper(RuleModelOutputFieldMapper.class, modelOutputs));
        }

        private void variable(Long id, RuleVariable variable) {
            variables.put(id, variable);
        }

        private void dataObject(Long id, RuleDataObject object) {
            dataObjects.put(id, object);
        }

        private void dataField(Long id, RuleDataObjectField field) {
            dataFields.put(id, field);
        }

        private void model(Long id, RuleModel model) {
            models.put(id, model);
        }

        private void modelOutput(Long id, RuleModelOutputField output) {
            modelOutputs.put(id, output);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T byIdMapper(Class<T> mapperType, Map<Long, ?> values) {
        return (T) Proxy.newProxyInstance(
                mapperType.getClassLoader(),
                new Class<?>[]{mapperType},
                (proxy, method, args) -> "selectById".equals(method.getName())
                        ? values.get(((Number) args[0]).longValue()) : null);
    }
}
