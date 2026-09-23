package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.core.script.QLScriptAnalysis;
import com.hengshucredit.rule.core.script.QLScriptAnalyzer;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionContent;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleModelOutputField;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.artifact.RuleDependencyClosureService;
import com.hengshucredit.rule.server.artifact.RuleSchemaService;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDataObjectMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalApiConfigMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalDatasourceMapper;
import com.hengshucredit.rule.server.mapper.RuleModelMapper;
import com.hengshucredit.rule.server.mapper.RuleModelOutputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleVariableMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class IcekreditQlOfflineRegressionTest {
    private static final Long PROJECT_ID = 4L;
    private static final Long DEFINITION_ID = 30L;
    private static final Long REVISION_ID = 60L;
    private static final Long VARIABLE_ID = 302L;
    private static final Long API_CONFIG_ID = 9318L;
    private static final Long DATASOURCE_ID = 801L;
    private static final Long RESPONSE_OBJECT_ID = 701L;
    private static final String SCRIPT_ROOT =
            "icekredit_vn_credit_profile_features";
    private static final String ICEKREDIT_SCRIPT =
            "credit_score_v1 = icekredit_vn_credit_profile_features.credit_score_v1;\n"
            + "credit_apply_count_1m = "
            + "icekredit_vn_credit_profile_features.credit_apply_count_1m;\n"
            + "_result = {\"credit_score_v1\": credit_score_v1,"
            + "\"credit_apply_count_1m\": credit_apply_count_1m}\n_result";

    @BeforeClass
    public static void initializeMybatisMetadata() {
        Configuration configuration = new Configuration();
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(configuration, ""),
                RuleVariable.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(configuration, ""),
                RuleDataObject.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(configuration, ""),
                RuleDataObjectField.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(configuration, ""),
                RuleModel.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(configuration, ""),
                RuleModelOutputField.class);
    }

    @Test
    public void sanitizedSavedResponseExecutesQlWithoutExternalCall()
            throws Exception {
        Map<String, Object> response = loadFixture();
        Map<String, Object> features = mapAt(response, "body", "features");
        CountingApiService api = new CountingApiService();
        VariableSourceResolver resolver = resolverWithApiVariable(api);
        VariableResolveOptions options = VariableResolveOptions.defaults();
        // 离线回放显式导入可信的引擎取值记录，不把调用方同名参数当作已执行来源。
        VariableResolutionInvocationCache replay = new VariableResolutionInvocationCache();
        replay.resolveVariable("VARIABLE:" + VARIABLE_ID,
                () -> new SourceResolutionResult(0, SCRIPT_ROOT, true, features, Map.of(), List.of()));
        options.setInvocationCache(replay);
        options.setRequiredScriptNames(new LinkedHashSet<>(Arrays.asList(
                "icekredit_vn_credit_profile_features.credit_score_v1",
                "icekredit_vn_credit_profile_features.credit_apply_count_1m")));

        Map<String, Object> context = resolver.resolve(
                PROJECT_ID,
                Collections.singletonMap(SCRIPT_ROOT, features),
                options);
        RuleResult result =
                new QLExpressEngine().execute(ICEKREDIT_SCRIPT, context);

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertEquals(new LinkedHashSet<>(Arrays.asList(
                        "credit_score_v1", "credit_apply_count_1m")),
                ((Map<?, ?>) result.getResult()).keySet());
        assertEquals(600,
                ((Map<?, ?>) result.getResult()).get("credit_score_v1"));
        assertEquals(2,
                ((Map<?, ?>) result.getResult())
                        .get("credit_apply_count_1m"));
        assertEquals(0, api.callCount);
    }

    @Test
    public void staticPreflightStagesNeverInvokeExternalApi() {
        CountingApiService api = new CountingApiService();
        RuleVariable variable = apiVariable();
        List<RuleDataObjectField> objectFields = responseObjectFields();
        RuleDataObject responseObject = responseObject();
        RuleExternalApiConfig apiConfig = apiConfig();
        RuleExternalDatasource datasource = datasource();
        RuleVariableMapper variableMapper = mapper(
                RuleVariableMapper.class,
                Collections.singletonMap(VARIABLE_ID, variable),
                Collections.singletonList(variable));
        RuleDataObjectMapper objectMapper = mapper(
                RuleDataObjectMapper.class,
                Collections.singletonMap(RESPONSE_OBJECT_ID, responseObject),
                Collections.singletonList(responseObject));
        Map<Long, RuleDataObjectField> fieldsById = new LinkedHashMap<>();
        for (RuleDataObjectField field : objectFields) {
            fieldsById.put(field.getId(), field);
        }
        RuleDataObjectFieldMapper fieldMapper = mapper(
                RuleDataObjectFieldMapper.class, fieldsById, objectFields);
        RuleExternalApiConfigMapper apiConfigMapper = mapper(
                RuleExternalApiConfigMapper.class,
                Collections.singletonMap(API_CONFIG_ID, apiConfig),
                Collections.singletonList(apiConfig));
        RuleExternalDatasourceMapper datasourceMapper = mapper(
                RuleExternalDatasourceMapper.class,
                Collections.singletonMap(DATASOURCE_ID, datasource),
                Collections.singletonList(datasource));
        RuleModelMapper modelMapper =
                emptyMapper(RuleModelMapper.class);
        RuleModelOutputFieldMapper modelOutputFieldMapper =
                emptyMapper(RuleModelOutputFieldMapper.class);
        QLScriptFieldResolver fieldResolver = new QLScriptFieldResolver(
                variableMapper, objectMapper, fieldMapper,
                modelMapper, modelOutputFieldMapper);
        String modelJson = modelJson(true);

        QLScriptAnalysis analysis =
                new QLScriptAnalyzer().analyze(ICEKREDIT_SCRIPT);
        assertEquals(Arrays.asList(
                        "icekredit_vn_credit_profile_features.credit_score_v1",
                        "icekredit_vn_credit_profile_features.credit_apply_count_1m"),
                analysis.getDirectInputs());
        assertEquals(0, api.callCount);

        RuleFieldAnalyzer analyzer = new RuleFieldAnalyzer();
        ReflectionTestUtils.setField(
                analyzer, "qlScriptFieldResolver", fieldResolver);
        ReflectionTestUtils.setField(
                analyzer, "ruleVariableMapper", variableMapper);
        ReflectionTestUtils.setField(
                analyzer, "dataObjectMapper", objectMapper);
        ReflectionTestUtils.setField(
                analyzer, "dataObjectFieldMapper", fieldMapper);
        ReflectionTestUtils.setField(
                analyzer, "externalApiConfigMapper", apiConfigMapper);
        ReflectionTestUtils.setField(
                analyzer, "externalDatasourceMapper", datasourceMapper);
        ReflectionTestUtils.setField(
                analyzer, "modelMapper", modelMapper);
        ReflectionTestUtils.setField(
                analyzer, "modelOutputFieldMapper", modelOutputFieldMapper);
        ReflectionTestUtils.setField(
                analyzer, "dataObjectSchemaResolver",
                new DataObjectSchemaResolver(objectMapper, fieldMapper));
        ReflectionTestUtils.setField(
                analyzer, "variableSourceResolver",
                resolverWithApiVariable(api));

        RuleFieldAnalyzer.ResolvedFields resolved = analyzer.resolveFields(
                null, modelJson, "SCRIPT", PROJECT_ID);

        assertFalse(resolved.getDiagnostics().toString(),
                resolved.getDiagnostics().stream()
                        .anyMatch(issue -> "ERROR".equals(issue.getSeverity())));
        assertTrue("API 源变量是内部依赖，不应暴露为调用方输入",
                resolved.getInputFields().isEmpty());
        assertEquals(types(
                        "icekredit_vn_credit_profile_features.credit_score_v1",
                        "NUMBER",
                        "icekredit_vn_credit_profile_features.credit_apply_count_1m",
                        "INTEGER"),
                propertySchemaTypes(resolved.getInputPropertySchemas()));
        assertEquals(types(
                        "credit_score_v1", "NUMBER",
                        "credit_apply_count_1m", "INTEGER"),
                outputTypes(resolved.getOutputFields()));
        assertEquals(0, api.callCount);

        RuleSchemaService.SchemaSnapshot schema = new RuleSchemaService().build(
                resolved.getInputFields(), resolved.getOutputFields(),
                schemaOverrides(resolved.getInputPropertySchemas()),
                schemaOverrides(resolved.getOutputPropertySchemas()));

        assertTrue(schemaProperties(schema.getInputSchema()).isEmpty());
        assertEquals("number",
                schemaType(schema.getOutputSchema(), "credit_score_v1"));
        assertEquals("integer",
                schemaType(schema.getOutputSchema(), "credit_apply_count_1m"));
        assertEquals(0, api.callCount);

        OfflineClosureService closureService =
                new OfflineClosureService(modelJson, variable);
        RuleDependencyClosureService.DependencyClosure closure =
                closureService.resolve(
                        DEFINITION_ID, REVISION_ID, resolved);

        assertFalse(closure.getIssues().toString(), closure.hasErrors());
        assertTrue(closure.getDependencies().stream()
                .anyMatch(dependency ->
                        "VARIABLE:302".equals(dependency.getComponentId())));
        assertTrue(closure.getDependencies().stream()
                .anyMatch(dependency ->
                        "BINDING:EXTERNAL_API:9318"
                                .equals(dependency.getComponentId())));
        assertEquals(0, api.callCount);

        OfflineRepairService repairService = new OfflineRepairService(
                modelJson(false), modelJson, fieldResolver);
        RuleRevisionRepairService.RepairPreview preview =
                repairService.preview(DEFINITION_ID);

        assertEquals(Collections.singletonList("VARIABLE:302"),
                preview.getRecoverableReferenceKeys());
        assertTrue(preview.getUnresolvedInputs().isEmpty());
        assertEquals(0, api.callCount);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadFixture() throws IOException {
        try (InputStream input = getClass().getResourceAsStream(
                "/fixtures/icekredit-vn-credit-profile-sanitized.json")) {
            assertNotNull(input);
            String json = new String(
                    input.readAllBytes(), StandardCharsets.UTF_8);
            return JSON.parseObject(json, Map.class);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapAt(
            Map<String, Object> root, String... keys) {
        Object current = root;
        for (String key : keys) {
            current = ((Map<String, Object>) current).get(key);
        }
        return (Map<String, Object>) current;
    }

    private static final class CountingApiService
            extends ExternalApiInvokeService {
        private int callCount;

        @Override
        public Map<String, Object> invoke(
                Long apiConfigId, Map<String, Object> params) {
            callCount++;
            throw new AssertionError(
                    "离线回归禁止调用 ExternalApiInvokeService");
        }
    }

    private VariableSourceResolver resolverWithApiVariable(
            CountingApiService api) {
        RuleVariable variable = apiVariable();
        RuleVariableService variables = new RuleVariableService() {
            @Override
            public List<RuleVariable> listByProject(
                    Long projectId, String varSource) {
                return Collections.singletonList(variable);
            }

            @Override
            public RuleVariable getById(Serializable id) {
                return variable.getId().equals(id) ? variable : null;
            }
        };
        VariableSourceResolver resolver = new VariableSourceResolver();
        ReflectionTestUtils.setField(
                resolver, "variableService", variables);
        ReflectionTestUtils.setField(
                resolver, "externalApiInvokeService", api);
        return resolver;
    }

    private static RuleVariable apiVariable() {
        RuleVariable variable = new RuleVariable();
        variable.setId(VARIABLE_ID);
        variable.setProjectId(PROJECT_ID);
        variable.setScope("PROJECT");
        variable.setVarCode(SCRIPT_ROOT);
        variable.setVarLabel(SCRIPT_ROOT);
        variable.setScriptName(SCRIPT_ROOT);
        variable.setVarType("OBJECT");
        variable.setVarSource("API");
        variable.setSourceConfig(
                "{\"apiConfigId\":9318,\"resultPath\":\"body.features\"}");
        variable.setStatus(1);
        return variable;
    }

    private static RuleExternalApiConfig apiConfig() {
        RuleExternalApiConfig config = new RuleExternalApiConfig();
        config.setId(API_CONFIG_ID);
        config.setDatasourceId(DATASOURCE_ID);
        config.setResponseObjectId(RESPONSE_OBJECT_ID);
        config.setStatus(1);
        return config;
    }

    private static RuleExternalDatasource datasource() {
        RuleExternalDatasource datasource =
                new RuleExternalDatasource();
        datasource.setId(DATASOURCE_ID);
        datasource.setProjectId(PROJECT_ID);
        datasource.setScope("PROJECT");
        datasource.setStatus(1);
        return datasource;
    }

    private static RuleDataObject responseObject() {
        RuleDataObject object = new RuleDataObject();
        object.setId(RESPONSE_OBJECT_ID);
        object.setProjectId(PROJECT_ID);
        object.setScope("PROJECT");
        object.setObjectCode("sanitized_features");
        object.setScriptName("sanitized_features");
        object.setObjectType("OBJECT");
        object.setStatus(1);
        return object;
    }

    private static List<RuleDataObjectField> responseObjectFields() {
        return Arrays.asList(
                responseField(
                        702L, "credit_score_v1", "NUMBER", 0),
                responseField(
                        703L, "credit_apply_count_1m", "INTEGER", 1));
    }

    private static RuleDataObjectField responseField(
            Long id, String scriptName, String type, int sortOrder) {
        RuleDataObjectField field = new RuleDataObjectField();
        field.setId(id);
        field.setProjectId(PROJECT_ID);
        field.setScope("PROJECT");
        field.setObjectId(RESPONSE_OBJECT_ID);
        field.setVarCode(scriptName);
        field.setVarLabel(scriptName);
        field.setScriptName(scriptName);
        field.setVarType(type);
        field.setSortOrder(sortOrder);
        field.setStatus(1);
        return field;
    }

    private static String modelJson(boolean withReference) {
        JSONObject model = new JSONObject(true);
        model.put("script", ICEKREDIT_SCRIPT);
        if (withReference) {
            JSONArray refs = new JSONArray();
            JSONObject ref = new JSONObject(true);
            ref.put("varId", VARIABLE_ID);
            ref.put("refType", "VARIABLE");
            ref.put("refCode", SCRIPT_ROOT);
            refs.add(ref);
            model.put("scriptVarRefs", refs);
        }
        return model.toJSONString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> propertySchemaTypes(
            Map<String, Object> propertySchemas) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry
                : propertySchemas.entrySet()) {
            result.put(entry.getKey(),
                    (String) ((Map<String, Object>) entry.getValue())
                            .get("x-rule-type"));
        }
        return result;
    }

    private static Map<String, String> outputTypes(
            List<RuleDefinitionOutputField> fields) {
        Map<String, String> result = new LinkedHashMap<>();
        for (RuleDefinitionOutputField field : fields) {
            result.put(field.getFieldName(), field.getFieldType());
        }
        return result;
    }

    private static Map<String, String> types(
            String key1, String value1, String key2, String value2) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put(key1, value1);
        result.put(key2, value2);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static String schemaType(
            Map<String, Object> schema, String propertyName) {
        Map<String, Object> properties = schemaProperties(schema);
        return (String) ((Map<String, Object>)
                properties.get(propertyName)).get("type");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schemaProperties(
            Map<String, Object> schema) {
        return (Map<String, Object>) schema.get("properties");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> schemaOverrides(
            Map<String, Object> schemas) {
        return (Map<String, Map<String, Object>>) (Map<?, ?>) schemas;
    }

    private static <T> T emptyMapper(Class<T> type) {
        return mapper(type, Collections.emptyMap(), Collections.emptyList());
    }

    @SuppressWarnings("unchecked")
    private static <T> T mapper(
            Class<T> type, Map<Long, ?> valuesById, List<?> values) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> {
                    if ("selectById".equals(method.getName())) {
                        Object id = args == null || args.length == 0
                                ? null : args[0];
                        return id instanceof Number
                                ? valuesById.get(
                                        ((Number) id).longValue())
                                : null;
                    }
                    if ("selectList".equals(method.getName())) {
                        return values;
                    }
                    if ("toString".equals(method.getName())) {
                        return "OfflineMapper(" + type.getSimpleName() + ")";
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    return null;
                });
    }

    private static final class OfflineClosureService
            extends RuleDependencyClosureService {
        private final RuleDefinition definition;
        private final RuleRevision revision;
        private final RuleVariable variable;

        private OfflineClosureService(
                String modelJson, RuleVariable variable) {
            this.variable = variable;
            definition = new RuleDefinition();
            definition.setId(DEFINITION_ID);
            definition.setProjectId(PROJECT_ID);
            definition.setModelType("SCRIPT");
            definition.setStatus(1);
            revision = new RuleRevision();
            revision.setId(REVISION_ID);
            revision.setDefinitionId(DEFINITION_ID);
            revision.setModelJson(modelJson);
        }

        @Override
        protected RuleDefinition loadDefinition(Long definitionId) {
            return DEFINITION_ID.equals(definitionId)
                    ? definition : null;
        }

        @Override
        protected RuleRevision loadRevision(Long revisionId) {
            return REVISION_ID.equals(revisionId)
                    ? revision : null;
        }

        @Override
        protected RuleVariable loadVariable(Long variableId) {
            return VARIABLE_ID.equals(variableId)
                    ? variable : null;
        }
    }

    private static final class OfflineRepairService
            extends RuleRevisionRepairService {
        private final RuleDefinition definition;
        private final RuleDefinitionContent content;
        private final List<RuleRevision> revisions;

        private OfflineRepairService(
                String currentModelJson,
                String historicalModelJson,
                QLScriptFieldResolver fieldResolver) {
            definition = new RuleDefinition();
            definition.setId(DEFINITION_ID);
            definition.setProjectId(PROJECT_ID);
            definition.setModelType("SCRIPT");
            definition.setStatus(1);
            content = new RuleDefinitionContent();
            content.setDefinitionId(DEFINITION_ID);
            content.setModelJson(currentModelJson);
            RuleRevision historical = new RuleRevision();
            historical.setId(REVISION_ID);
            historical.setDefinitionId(DEFINITION_ID);
            historical.setRevisionNo(1);
            historical.setState("APPROVED");
            historical.setModelJson(historicalModelJson);
            historical.setContentDigest("sanitized-offline-revision");
            revisions = Collections.singletonList(historical);
            ReflectionTestUtils.setField(
                    this, "fieldResolver", fieldResolver);
        }

        @Override
        protected RuleDefinition loadDefinition(Long definitionId) {
            return DEFINITION_ID.equals(definitionId)
                    ? definition : null;
        }

        @Override
        protected RuleDefinitionContent loadContent(Long definitionId) {
            return DEFINITION_ID.equals(definitionId)
                    ? content : null;
        }

        @Override
        protected List<RuleRevision> loadRevisions(Long definitionId) {
            return DEFINITION_ID.equals(definitionId)
                    ? revisions : Collections.emptyList();
        }

        @Override
        protected List<RuleDefinitionInputField> loadInputFields(
                Long definitionId) {
            return Collections.emptyList();
        }

        @Override
        protected List<RuleDefinitionOutputField> loadOutputFields(
                Long definitionId) {
            return Collections.emptyList();
        }

        @Override
        protected RuleRevision currentDraft(Long definitionId) {
            return null;
        }
    }
}
