package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hengshucredit.rule.model.dto.ApiDocDTO;
import com.hengshucredit.rule.model.dto.ProjectAuthDTO;
import com.hengshucredit.rule.model.entity.RuleApiDocScenario;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.model.entity.RuleProjectAuth;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.server.artifact.PublishedRuleFieldSnapshotResolver;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDataObjectMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionContentMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionInputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionOutputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleFunctionMapper;
import com.hengshucredit.rule.server.mapper.RuleVariableMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuleProjectServiceTest {

    @Test
    public void getModelTypeLabelSupportsCurrentAndLegacyEnums() throws Exception {
        RuleProjectService service = new RuleProjectService();
        Method method = RuleProjectService.class.getDeclaredMethod("getModelTypeLabel", String.class);
        method.setAccessible(true);

        assertEquals("交叉表", method.invoke(service, "CROSS"));
        assertEquals("交叉表", method.invoke(service, "CROSS_TABLE"));
        assertEquals("评分卡", method.invoke(service, "SCORE"));
        assertEquals("评分卡", method.invoke(service, "SCORE_CARD"));
        assertEquals("复杂交叉表", method.invoke(service, "CROSS_ADV"));
        assertEquals("复杂交叉表", method.invoke(service, "CROSS_TABLE_ADV"));
        assertEquals("复杂评分卡", method.invoke(service, "SCORE_ADV"));
        assertEquals("复杂评分卡", method.invoke(service, "SCORE_CARD_ADV"));
        assertEquals("规则集", method.invoke(service, "RULE_SET"));
    }
    @Test
    public void exportApiDocProjectScopeIncludesLinkedGlobalRules() throws Exception {
        RuleProjectService service = new RuleProjectService();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), RuleDefinition.class);
        LambdaQueryWrapper<RuleDefinition> wrapper = new LambdaQueryWrapper<>();
        Method method = RuleProjectService.class.getDeclaredMethod("appendProjectRuleScope",
                LambdaQueryWrapper.class, Long.class);
        method.setAccessible(true);

        method.invoke(service, wrapper, 12L);

        String sql = wrapper.getCustomSqlSegment();
        assertTrue(sql.contains("rule_definition_ref"));
        assertTrue(sql.contains("rdr.definition_id = rule_definition.id"));
        assertTrue(sql.contains("rdr.project_id = 12"));
    }

    @Test
    public void projectCreationPersistsFixedTraceScopeCodeAfterIdAllocation() {
        RecordingProjectService service = new RecordingProjectService();
        ReflectionTestUtils.setField(service, "projectAuthService", new ProjectAuthService(null) {
            @Override
            public RuleProjectAuth saveLegacyToken(RuleProject project, String token) {
                return null;
            }
        });
        RuleProject project = new RuleProject();
        project.setProjectCode("PROJECT_A");

        service.createProjectWithToken(project);

        assertEquals("00A7", project.getTraceScopeCode());
        assertTrue(service.updated);
    }

    @Test
    public void exportApiDocIncludesOnlySafeEnabledAuthenticationMetadata() {
        ExportFixture fixture = exportFixture();

        ApiDocDTO doc = fixture.service.exportApiDoc(7L);

        assertEquals(2, doc.getAuthentications().size());
        ApiDocDTO.AuthenticationInfo apiKey = doc.getAuthentications().get(1);
        assertEquals("API_KEY", apiKey.getAuthType());
        assertEquals("HEADER", apiKey.getPlacement());
        assertEquals("X-Partner-Key", apiKey.getParameterName());
        String json = JSON.toJSONString(apiKey);
        assertFalse(json.contains("secret"));
        assertFalse(json.contains("identifier"));
        assertFalse(json.contains("authCode"));
        assertFalse(json.contains("REAL_"));
    }

    @Test
    public void exportApiDocOmitsDefinitionsWithoutAnActivePublishedRow() {
        ExportFixture fixture = exportFixture();
        RuleDefinition draft = new RuleDefinition();
        draft.setId(99L);
        draft.setProjectId(7L);
        draft.setRuleCode("DRAFT_ONLY");
        draft.setRuleName("草稿规则");
        draft.setModelType("SCRIPT");
        draft.setStatus(1);
        ReflectionTestUtils.setField(fixture.service, "definitionMapper",
                listMapper(RuleDefinitionMapper.class, Arrays.asList(
                        fixture.definition(), draft)));
        RulePublished published = new RulePublished();
        published.setDefinitionId(21L);
        published.setVersion(3);
        published.setStatus(1);
        ReflectionTestUtils.setField(fixture.service, "publishedMapper",
                listMapper(com.hengshucredit.rule.server.mapper.RulePublishedMapper.class,
                        Collections.singletonList(published)));

        ApiDocDTO doc = fixture.service.exportApiDoc(7L);

        assertEquals(1, doc.getRules().size());
        assertEquals("RISK_RULE", doc.getRules().get(0).getRuleCode());
    }

    @Test
    public void exportApiDocIncludesOnlySelectedCurrentPublishedScenarios() {
        ExportFixture fixture = exportFixture();

        ApiDocDTO doc = fixture.service.exportApiDoc(7L);

        assertEquals("风险拒绝", doc.getRules().get(0).getScenarios().get(0).getScenarioName());
        assertEquals(Long.valueOf(21L), fixture.scenarioService.definitionId);
        assertEquals(Integer.valueOf(3), fixture.scenarioService.publishedVersion);
    }

    @Test
    public void exportApiDocUsesFrozenPublishedFieldsAndExcludesRuntimeSources() {
        ExportFixture fixture = exportFixture();
        RuleVariable input = new RuleVariable();
        input.setId(101L);
        input.setVarCode("customer_age");
        input.setVarLabel("客户年龄");
        input.setScriptName("customerAge");
        input.setVarType("INTEGER");
        input.setVarSource("INPUT");
        input.setStatus(1);
        RuleVariable api = new RuleVariable();
        api.setId(102L);
        api.setVarCode("credit_score");
        api.setScriptName("creditScore");
        api.setVarType("INTEGER");
        api.setVarSource("API");
        api.setStatus(1);
        ReflectionTestUtils.setField(fixture.service, "variableMapper", listMapper(
                RuleVariableMapper.class, Arrays.asList(input, api)));
        RulePublished published = new RulePublished();
        published.setDefinitionId(21L);
        published.setRevisionId(8L);
        published.setArtifactId(9L);
        published.setArtifactDigest("digest");
        published.setVersion(3);
        published.setStatus(1);
        ReflectionTestUtils.setField(fixture.service, "publishedMapper", listMapper(
                com.hengshucredit.rule.server.mapper.RulePublishedMapper.class,
                Collections.singletonList(published)));
        RuleDefinitionInputField inputField = new RuleDefinitionInputField();
        inputField.setVarId(101L);
        inputField.setRefType("VARIABLE");
        inputField.setFieldName("customer_age");
        inputField.setScriptName("customerAge");
        inputField.setFieldType("INTEGER");
        inputField.setStatus(1);
        RuleDefinitionInputField apiField = new RuleDefinitionInputField();
        apiField.setVarId(102L);
        apiField.setRefType("VARIABLE");
        apiField.setFieldName("credit_score");
        apiField.setScriptName("creditScore");
        apiField.setFieldType("INTEGER");
        apiField.setStatus(1);
        RuleDefinitionOutputField outputField = new RuleDefinitionOutputField();
        outputField.setVarId(101L);
        outputField.setRefType("VARIABLE");
        outputField.setFieldName("decision");
        outputField.setScriptName("decision");
        outputField.setFieldType("STRING");
        outputField.setStatus(1);
        ReflectionTestUtils.setField(fixture.service, "publishedFieldSnapshotResolver",
                new PublishedRuleFieldSnapshotResolver() {
                    @Override
                    public RuleFieldAnalyzer.ResolvedFields resolve(RulePublished ignored) {
                        return new RuleFieldAnalyzer.ResolvedFields(
                                Arrays.asList(inputField, apiField),
                                Collections.singletonList(outputField));
                    }
                });
        ReflectionTestUtils.setField(fixture.service, "contentMapper", mapper(
                RuleDefinitionContentMapper.class, (proxy, method, args) -> {
                    throw new AssertionError("published export must not read the work draft");
                }));

        ApiDocDTO.RuleInfo rule = fixture.service.exportApiDoc(7L).getRules().get(0);

        assertEquals("VERIFIED", rule.getSchemaTrust());
        assertEquals(1, rule.getInputVariables().size());
        assertEquals("customer_age", rule.getInputVariables().get(0).getVarCode());
        assertTrue(rule.getInputVariables().get(0).getRequired());
        assertTrue(rule.getInputSchema().toString().contains("customer_age"));
    }

    @Test
    public void openApiProjectionKeepsMappingsAndMasksResponseHeaderValues() throws Exception {
        RuleProjectService service = new RuleProjectService();
        Method method = RuleProjectService.class.getDeclaredMethod("openApiContractProjection", String.class);
        method.setAccessible(true);
        String json = "{\"enabled\":true,\"requestMappings\":[{\"targetRefType\":\"VARIABLE\",\"targetVarId\":7,\"sourceType\":\"BODY\",\"sourcePath\":\"$.age\",\"required\":true,\"targetType\":\"INTEGER\"}],"
                + "\"responseMappings\":[{\"sourceRefType\":\"VARIABLE\",\"sourceVarId\":8,\"targetField\":\"decision\"}],"
                + "\"envelopeTemplate\":{\"data\":\"${data}\"},\"dataPath\":\"$.data\",\"successDataTemplate\":\"${response}\","
                + "\"errorDataTemplate\":{\"message\":\"${status.message}\"},\"responseHeaders\":{\"X-Trace\":\"secret-value\"}}";

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> projection = (java.util.Map<String, Object>) method.invoke(service, json);

        assertTrue(projection.containsKey("requestMappings"));
        assertTrue(projection.containsKey("responseMappings"));
        assertTrue(projection.containsKey("responseHeaderNames"));
        assertFalse(projection.containsKey("responseHeaders"));
        assertFalse(JSON.toJSONString(projection).contains("secret-value"));
    }

    private ExportFixture exportFixture() {
        ExportProjectService service = new ExportProjectService();
        RuleProject project = new RuleProject();
        project.setId(7L);
        project.setProjectCode("credit");
        project.setProjectName("授信项目");
        service.project = project;

        ProjectAuthDTO basic = auth("BASIC_MAIN", "基础认证", "BASIC", 1);
        basic.setIdentifier("REAL_USERNAME");
        basic.setIdentifierMasked("re****me");
        basic.setSecret("REAL_PASSWORD");
        basic.setSecretMasked("re****rd");
        ProjectAuthDTO apiKey = auth("PARTNER_KEY", "合作方 Key", "API_KEY", 1);
        apiKey.setPlacement("HEADER");
        apiKey.setParameterName("X-Partner-Key");
        apiKey.setSecret("REAL_API_KEY");
        apiKey.setSecretMasked("re****ey");
        ProjectAuthDTO disabled = auth("HMAC_OLD", "旧 HMAC", "HMAC", 0);
        disabled.setSecret("REAL_HMAC_SECRET");

        FakeProjectAuthService authService = new FakeProjectAuthService();
        authService.auths = Arrays.asList(basic, apiKey, disabled);
        FakeScenarioService scenarioService = new FakeScenarioService();
        RuleApiDocScenario scenario = new RuleApiDocScenario();
        scenario.setId(31L);
        scenario.setScenarioName("风险拒绝");
        scenario.setRequestJson("{\"params\":{\"age\":17}}");
        scenario.setResponseJson("{\"code\":200}");
        scenarioService.scenarios = Collections.singletonList(scenario);

        RuleDefinition definition = new RuleDefinition();
        definition.setId(21L);
        definition.setProjectId(7L);
        definition.setRuleCode("RISK_RULE");
        definition.setRuleName("风险规则");
        definition.setModelType("TABLE");
        definition.setCurrentVersion(4);
        definition.setPublishedVersion(3);
        definition.setStatus(1);

        ReflectionTestUtils.setField(service, "projectAuthService", authService);
        ReflectionTestUtils.setField(service, "apiDocScenarioService", scenarioService);
        ReflectionTestUtils.setField(service, "definitionMapper", listMapper(
                RuleDefinitionMapper.class, Collections.singletonList(definition)));
        ReflectionTestUtils.setField(service, "variableMapper", listMapper(RuleVariableMapper.class, Collections.emptyList()));
        ReflectionTestUtils.setField(service, "dataObjectMapper", listMapper(RuleDataObjectMapper.class, Collections.emptyList()));
        ReflectionTestUtils.setField(service, "fieldMapper", listMapper(RuleDataObjectFieldMapper.class, Collections.emptyList()));
        ReflectionTestUtils.setField(service, "contentMapper", listMapper(RuleDefinitionContentMapper.class, Collections.emptyList()));
        ReflectionTestUtils.setField(service, "inputFieldMapper", listMapper(RuleDefinitionInputFieldMapper.class, Collections.emptyList()));
        ReflectionTestUtils.setField(service, "outputFieldMapper", listMapper(RuleDefinitionOutputFieldMapper.class, Collections.emptyList()));
        ReflectionTestUtils.setField(service, "functionMapper", listMapper(RuleFunctionMapper.class, Collections.emptyList()));
        ReflectionTestUtils.setField(service, "ruleModelVarParser", new RuleModelVarParser());

        return new ExportFixture(service, scenarioService, definition);
    }

    @Test
    public void apiDocumentationUsesResolvedUpstreamInsteadOfPersistedDerivedRoot() {
        ExportFixture fixture = exportFixture();
        var content = new com.hengshucredit.rule.model.entity.RuleDefinitionContent();
        content.setModelJson("{}");
        var derived = new com.hengshucredit.rule.model.entity.RuleDefinitionInputField();
        derived.setVarId(1L); derived.setRefType("VARIABLE"); derived.setFieldName("historyMetric"); derived.setScriptName("historyMetric");
        var upstream = new com.hengshucredit.rule.model.entity.RuleDefinitionInputField();
        upstream.setVarId(2L); upstream.setRefType("VARIABLE"); upstream.setFieldName("contacts"); upstream.setScriptName("contacts"); upstream.setFieldType("LIST");
        ReflectionTestUtils.setField(fixture.service, "contentMapper", mapper(RuleDefinitionContentMapper.class,
                (proxy, method, args) -> "selectOne".equals(method.getName()) ? content : defaultValue(method.getReturnType())));
        ReflectionTestUtils.setField(fixture.service, "inputFieldMapper", listMapper(RuleDefinitionInputFieldMapper.class, List.of(derived)));
        ReflectionTestUtils.setField(fixture.service, "ruleFieldAnalyzer", new RuleFieldAnalyzer() {
            @Override public ResolvedFields resolveFields(Long id, String modelJson, String modelType, Long projectId) {
                assertEquals(Long.valueOf(7), projectId);
                return new ResolvedFields(List.of(upstream), List.of());
            }
        });
        ApiDocDTO doc = fixture.service.exportApiDoc(7L);
        assertEquals(1, doc.getRules().get(0).getInputVariables().size());
        assertEquals("contacts", doc.getRules().get(0).getInputVariables().get(0).getVarCode());
        assertEquals("LIST", doc.getRules().get(0).getInputVariables().get(0).getVarType());
    }

    private ProjectAuthDTO auth(String code, String name, String type, int status) {
        ProjectAuthDTO auth = new ProjectAuthDTO();
        auth.setAuthCode(code);
        auth.setAuthName(name);
        auth.setAuthType(type);
        auth.setStatus(status);
        auth.setTokenTtlSeconds(7200);
        auth.setTokenGraceSeconds(600);
        return auth;
    }

    private static <T> T listMapper(Class<T> type, List<?> result) {
        return mapper(type, (proxy, method, args) -> {
            if ("selectList".equals(method.getName())) return result;
            return defaultValue(method.getReturnType());
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T mapper(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == long.class) return 0L;
        return 0;
    }

    private static class RecordingProjectService extends RuleProjectService {
        private boolean updated;

        @Override
        public boolean save(RuleProject entity) {
            entity.setId(367L);
            return true;
        }

        @Override
        public boolean updateById(RuleProject entity) {
            updated = true;
            return true;
        }
    }

    private static class ExportProjectService extends RuleProjectService {
        private RuleProject project;

        @Override
        public RuleProject getById(Serializable id) {
            return project;
        }
    }

    private static class FakeProjectAuthService extends ProjectAuthService {
        private List<ProjectAuthDTO> auths = new ArrayList<>();

        private FakeProjectAuthService() {
            super(null);
        }

        @Override
        public List<ProjectAuthDTO> listAuths(Long projectId) {
            return auths;
        }
    }

    private static class FakeScenarioService extends RuleApiDocScenarioService {
        private Long definitionId;
        private Integer publishedVersion;
        private List<RuleApiDocScenario> scenarios = Collections.emptyList();

        @Override
        public List<RuleApiDocScenario> listExportable(Long definitionId, Integer publishedVersion) {
            this.definitionId = definitionId;
            this.publishedVersion = publishedVersion;
            return scenarios;
        }

        @Override
        public List<RuleApiDocScenario> listExportable(Long definitionId, Integer publishedVersion,
                                                       Long publishedRevisionId, String publishedArtifactDigest) {
            this.definitionId = definitionId;
            this.publishedVersion = publishedVersion;
            return scenarios;
        }
    }

    private static class ExportFixture {
        private final RuleProjectService service;
        private final FakeScenarioService scenarioService;
        private final RuleDefinition definition;

        private ExportFixture(RuleProjectService service, FakeScenarioService scenarioService, RuleDefinition definition) {
            this.service = service;
            this.scenarioService = scenarioService;
            this.definition = definition;
        }

        private RuleDefinition definition() { return definition; }
    }
}
