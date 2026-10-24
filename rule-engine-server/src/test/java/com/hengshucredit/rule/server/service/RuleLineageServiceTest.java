package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionContent;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleModelInputField;
import com.hengshucredit.rule.model.entity.RuleModelOutputField;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDataObjectMapper;
import com.hengshucredit.rule.server.mapper.RuleDbDatasourceMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionInputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionContentMapper;
import com.hengshucredit.rule.server.mapper.RulePublishedMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionOutputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalApiConfigMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalDatasourceMapper;
import com.hengshucredit.rule.server.mapper.RuleListLibraryMapper;
import com.hengshucredit.rule.server.mapper.RuleModelInputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleModelMapper;
import com.hengshucredit.rule.server.mapper.RuleModelOutputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleProjectMapper;
import com.hengshucredit.rule.server.mapper.RuleVariableMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.hengshucredit.rule.server.governance.GovernanceImpactService;
import com.hengshucredit.rule.server.governance.GovernanceIssue;
import com.hengshucredit.rule.server.governance.ResourceSnapshot;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RuleLineageServiceTest {
    @Test
    public void graphCarriesExactVariableTypeForSharedContentDisplay() {
        RuleLineageService service = serviceWithProjectBranch(false);
        RuleVariable value = variable(1L, "Account_AGE");
        value.setVarType("INTEGER");
        setMapper(service, "variableMapper", RuleVariableMapper.class, List.of(value));
        Map<String, Object> graph = service.graph("VARIABLE", 1L, "ALL", 1);
        Object start = graph.get("startNode");
        assertTrue(start instanceof Map);
        assertEquals("INTEGER", ((Map<?, ?>) start).get("valueType"));
        assertEquals("Account_AGE", ((Map<?, ?>) start).get("code"));
        assertEquals(1L, ((Map<?, ?>) start).get("refId"));
    }
    @Test
    public void variableSourcesIncludeAllListsAndStructuredReferences() {
        RuleLineageService service = serviceWithProjectBranch(false);
        RuleVariable listed = variable(4L, "listed"); listed.setVarSource("LIST");
        listed.setSourceConfig("{\"listIds\":[21,22,21],\"fields\":[{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\",\"refId\":1}]}");
        RuleVariable derived = variable(5L, "derived"); derived.setVarSource("DERIVED");
        derived.setSourceConfig("{\"mode\":\"EXPRESSION\",\"operand\":{\"kind\":\"FUNCTION\",\"functionId\":7,\"args\":[{\"kind\":\"REFERENCE\",\"refType\":\"VARIABLE\",\"refId\":4}]}}");
        setMapper(service, "variableMapper", RuleVariableMapper.class, List.of(variable(1L, "original"), listed, derived));
        var first = new com.hengshucredit.rule.model.entity.RuleListLibrary(); first.setId(21L); first.setListCode("first");
        var second = new com.hengshucredit.rule.model.entity.RuleListLibrary(); second.setId(22L); second.setListCode("second");
        setMapper(service, "listLibraryMapper", RuleListLibraryMapper.class, List.of(first, second));
        var function = new com.hengshucredit.rule.model.entity.RuleFunction(); function.setId(7L); function.setFuncCode("calculate");
        setMapper(service, "functionMapper", com.hengshucredit.rule.server.mapper.RuleFunctionMapper.class, List.of(function));
        Set<String> ids = nodeIds(service.graph("VARIABLE", 5L, "UPSTREAM", 3));
        assertTrue(ids.containsAll(Set.of("LIST:21", "LIST:22", "FUNCTION:7", "VARIABLE:4", "VARIABLE:1")));
        assertEquals(1, service.directReferenceConsumers("LIST", 22L).size());
        assertEquals(1, service.directReferenceConsumers("FUNCTION", 7L).size());
        assertEquals(2, edges(service.graph("VARIABLE", 4L, "UPSTREAM", 1)).stream()
                .filter(edge -> "名单匹配".equals(edge.get("label"))).count());
    }
    @Test
    public void graphDoesNotLoadDatasourceCredentialsOrConnectionBodies() {
        RuleLineageService service = serviceWithProjectBranch(false);
        RuleDbDatasourceMapper mapper = (RuleDbDatasourceMapper) Proxy.newProxyInstance(
                RuleDbDatasourceMapper.class.getClassLoader(), new Class<?>[]{RuleDbDatasourceMapper.class}, (proxy, method, args) -> {
                    if (!"selectList".equals(method.getName())) return defaultValue(method.getReturnType());
                    var query = (com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) args[0];
                    String selected = query.getSqlSelect();
                    assertNotNull("构图必须明确裁剪列，不能 SELECT *", selected);
                    assertTrue(selected.contains("datasource_code"));
                    assertFalse(selected.contains("password"));
                    assertFalse(selected.contains("private_key"));
                    assertFalse(selected.contains("jdbc_url"));
                    return List.of();
                });
        ReflectionTestUtils.setField(service, "dbDatasourceMapper", mapper);
        assertTrue(nodeIds(service.graph("VARIABLE", 1L, "ALL", 1)).contains("VARIABLE:1"));
    }
    @Test
    public void referencedFunctionMustExistInGraphAndRemainProtected() {
        RuleLineageService service = serviceWithProjectBranch(false);
        var function = new com.hengshucredit.rule.model.entity.RuleFunction();
        function.setId(7L); function.setProjectId(1L); function.setFuncCode("Keep_Function"); function.setFuncName("业务函数"); function.setStatus(1);
        setMapper(service, "functionMapper", com.hengshucredit.rule.server.mapper.RuleFunctionMapper.class, List.of(function));
        RuleDefinitionContent content = new RuleDefinitionContent(); content.setDefinitionId(10L);
        content.setModelJson("{\"operand\":{\"kind\":\"FUNCTION\",\"functionId\":7,\"args\":[]}}");
        setMapper(service, "definitionContentMapper", RuleDefinitionContentMapper.class, List.of(content));
        assertEquals("引用函数的规则必须可被影响分析识别", 1, service.directReferenceConsumers("FUNCTION", 7L).size());
        assertTrue(nodeIds(service.graph("FUNCTION", 7L, "UPSTREAM", 1)).contains("PROJECT:1"));
    }

    @Test
    public void deletingRuleDoesNotTreatItsOutputTargetAsAConsumerOrReverseTheVisualGraph() {
        RuleLineageService service = serviceWithProjectBranch(false);
        Map<String, Object> visual = service.graph("RULE", 10L, "DOWNSTREAM", 1);
        assertTrue(nodeIds(visual).contains("VARIABLE:3"));
        assertTrue(edges(visual).stream().anyMatch(edge -> "RULE:10".equals(edge.get("from"))
                && "VARIABLE:3".equals(edge.get("to")) && "规则输出".equals(edge.get("label"))));

        assertTrue("规则持有输出变量ID，不是输出变量持有规则ID", impact(service, "RULE", 10L).isEmpty());
    }

    @Test
    public void deletingAVariableWrittenByARuleRemainsBlocked() {
        RuleLineageService service = serviceWithProjectBranch(false);
        List<GovernanceIssue> issues = impact(service, "VARIABLE", 3L);
        assertEquals(1, issues.size());
        assertTrue(issues.get(0).message().contains("RULE"));
    }

    @Test
    public void deletingModelDoesNotTreatItsOutputVariableAsAConsumer() {
        RuleLineageService service = serviceWithModelOutput();
        assertTrue(nodeIds(service.graph("MODEL", 11L, "DOWNSTREAM", 1)).contains("VARIABLE:3"));
        assertTrue(impact(service, "MODEL", 11L).isEmpty());
    }

    @Test
    public void deletingAVariableWrittenByAModelRemainsBlocked() {
        List<GovernanceIssue> issues = impact(serviceWithModelOutput(), "VARIABLE", 3L);
        assertEquals(1, issues.size());
        assertTrue(issues.get(0).message().contains("MODEL"));
    }

    @Test
    public void outputReferencesUseRefTypeAndNeverGuessFromEqualNumericIds() {
        RuleLineageService service = serviceWithProjectBranch(false);
        setMapper(service, "modelMapper", RuleModelMapper.class, List.of(model(3L, "referenced-model")));
        RuleDefinitionOutputField output = new RuleDefinitionOutputField();
        output.setDefinitionId(10L); output.setVarId(3L); output.setRefType("MODEL");
        setMapper(service, "definitionOutputFieldMapper", RuleDefinitionOutputFieldMapper.class, List.of(output));
        assertFalse(impact(service, "MODEL", 3L).isEmpty());
        assertTrue("同值变量ID不能与模型引用混淆", impact(service, "VARIABLE", 3L).isEmpty());
        assertTrue(impact(service, "RULE", 10L).isEmpty());
    }

    @Test
    public void disabledAndOfflineOutputWritersRemainConsumersButDeletedWritersDoNot() {
        for (Integer status : List.of(0, 2, -1)) {
            RuleLineageService service = serviceWithProjectBranch(false);
            RuleDefinition rule = new RuleDefinition(); rule.setId(10L); rule.setStatus(status);
            setMapper(service, "definitionMapper", RuleDefinitionMapper.class, List.of(rule));
            assertEquals("仅status=-1释放引用", status == -1, impact(service, "VARIABLE", 3L).isEmpty());
        }
    }

    @Test
    public void aCurrentChildRuleCallBlocksDeletingTheReferencedRule() {
        RuleLineageService service = serviceWithRuleCaller(1, true, false, 0);
        List<GovernanceIssue> issues = impact(service, "RULE", 10L);
        assertEquals(1, issues.size());
        assertTrue("必须保护实际调用者，而非误报输出目标", issues.get(0).message().contains("parent-rule"));
        assertFalse(issues.get(0).message().contains("output"));
    }

    @Test public void staticVersionCallsRemainDeletionBlockersAfterDraftRemovesThem() {
        RuleLineageService service = serviceWithRuleCaller(1, false, false, 1);
        setMapper(service, "definitionInputFieldMapper", RuleDefinitionInputFieldMapper.class, List.of());
        setMapper(service, "definitionOutputFieldMapper", RuleDefinitionOutputFieldMapper.class, List.of());
        RulePublished published = new RulePublished(); published.setDefinitionId(11L); published.setStatus(1);
        published.setModelJson("{\"script\":\"return resourceVersionValue('CONSTANT','3','BUSINESS_VERSION:91','');\"}");
        setMapper(service, "publishedMapper", RulePublishedMapper.class, List.of(published));
        var issues = impact(service, "VARIABLE", 3L);
        assertTrue(issues.toString(), issues.stream().anyMatch(issue -> issue.message().contains("parent-rule")));
    }

    @Test public void callableOlderBusinessLinesRemainConsumersWhenLatestNoLongerReferencesResource() {
        for (Class<?> type : List.of(com.hengshucredit.rule.model.entity.RuleVersionBinding.class, com.hengshucredit.rule.model.entity.RuleDefinitionVersion.class))
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), "version-consumers"), type);
        RuleLineageService service = serviceWithRuleCaller(1, false, false, 0);
        setMapper(service, "definitionInputFieldMapper", RuleDefinitionInputFieldMapper.class, List.of());
        setMapper(service, "definitionOutputFieldMapper", RuleDefinitionOutputFieldMapper.class, List.of());
        var binding = new com.hengshucredit.rule.model.entity.RuleVersionBinding(); binding.setDefinitionId(11L); binding.setSnapshotId(77L); binding.setStatus(1);
        var historical = new com.hengshucredit.rule.model.entity.RuleDefinitionVersion(); historical.setId(77L); historical.setDefinitionId(11L);
        historical.setModelJson("{\"script\":\"return resourceVersionValue('CONSTANT','3','BUSINESS_VERSION:91','');\"}");
        setMapper(service, "ruleVersionBindings", com.hengshucredit.rule.server.mapper.RuleVersionBindingMapper.class, List.of(binding));
        setMapper(service, "ruleVersionSnapshots", com.hengshucredit.rule.server.mapper.RuleDefinitionVersionMapper.class, List.of(historical));
        assertTrue(impact(service, "VARIABLE", 3L).stream().anyMatch(issue -> issue.message().contains("parent-rule")));
        binding.setStatus(0);
        assertTrue(impact(service, "VARIABLE", 3L).isEmpty());
    }

    @Test
    public void anOnlinePublishedCallRemainsProtectedAfterItsDraftRemovesTheCall() {
        RuleLineageService service = serviceWithRuleCaller(1, false, true, 1);
        List<GovernanceIssue> issues = impact(service, "RULE", 10L);
        assertEquals(1, issues.size());
        assertTrue(issues.get(0).message().contains("parent-rule"));
    }

    @Test
    public void deletedCallersAndOfflineOnlyPublishedCallsDoNotCreateFalseConsumers() {
        assertTrue(impact(serviceWithRuleCaller(-1, true, true, 1), "RULE", 10L).isEmpty());
        assertTrue(impact(serviceWithRuleCaller(1, false, true, 0), "RULE", 10L).isEmpty());
    }

    private static List<GovernanceIssue> impact(RuleLineageService service, String type, Long id) {
        return new GovernanceImpactService(service).analyze(type, id, "DELETE",
                ResourceSnapshot.ofJson("{\"id\":" + id + "}"));
    }

    private RuleLineageService serviceWithModelOutput() {
        RuleLineageService service = serviceWithProjectBranch(false);
        setMapper(service, "definitionMapper", RuleDefinitionMapper.class, List.of());
        setMapper(service, "modelMapper", RuleModelMapper.class, List.of(model(11L, "writer")));
        RuleModelOutputField output = new RuleModelOutputField();
        output.setModelId(11L); output.setRefType("VARIABLE"); output.setVarId(3L);
        setMapper(service, "modelOutputFieldMapper", RuleModelOutputFieldMapper.class, List.of(output));
        return service;
    }

    private RuleLineageService serviceWithRuleCaller(int status, boolean currentCall, boolean publishedCall, int publishedStatus) {
        RuleLineageService service = serviceWithProjectBranch(false);
        RuleDefinition child = new RuleDefinition(); child.setId(10L); child.setRuleName("child-rule");
        RuleDefinition parent = new RuleDefinition(); parent.setId(11L); parent.setRuleName("parent-rule"); parent.setStatus(status);
        setMapper(service, "definitionMapper", RuleDefinitionMapper.class, List.of(child, parent));
        String call = "{\"nodes\":[{\"type\":\"rule-call\",\"ruleId\":10}]}";
        RuleDefinitionContent content = new RuleDefinitionContent(); content.setDefinitionId(11L);
        content.setModelJson(currentCall ? call : "{\"nodes\":[]}");
        setMapper(service, "definitionContentMapper", RuleDefinitionContentMapper.class, List.of(content));
        RulePublished published = new RulePublished(); published.setDefinitionId(11L); published.setStatus(publishedStatus);
        published.setModelJson(publishedCall ? call : "{\"nodes\":[]}");
        setMapper(service, "publishedMapper", RulePublishedMapper.class, List.of(published));
        return service;
    }

    @Test
    public void deletedRulesAndVariablesDoNotBlockProjectDeletion() {
        RuleLineageService service = serviceWithProjectBranch(false);
        RuleVariable input = variable(1L, "removed-input"); input.setStatus(-1);
        RuleVariable output = variable(3L, "removed-output"); output.setStatus(-1);
        RuleDefinition rule = new RuleDefinition(); rule.setId(10L); rule.setProjectId(1L); rule.setStatus(-1);
        setMapper(service, "variableMapper", RuleVariableMapper.class, List.of(input, output));
        setMapper(service, "definitionMapper", RuleDefinitionMapper.class, List.of(rule));

        assertEquals(Set.of("PROJECT:1"), nodeIds(service.graph("PROJECT", 1L, "DOWNSTREAM", 1)));
        assertTrue(new com.hengshucredit.rule.server.governance.GovernanceImpactService(service).analyze(
                "PROJECT", 1L, "DELETE", com.hengshucredit.rule.server.governance.ResourceSnapshot.ofJson("{\"id\":1}")).isEmpty());
    }

    @Test
    public void disabledConsumersRemainVisibleAndProtectTheirReferences() {
        RuleLineageService service = serviceWithProjectBranch(false);
        RuleVariable disabled = variable(1L, "disabled-input"); disabled.setStatus(0);
        RuleDefinition offline = new RuleDefinition(); offline.setId(10L); offline.setProjectId(1L); offline.setStatus(2);
        setMapper(service, "variableMapper", RuleVariableMapper.class, List.of(disabled));
        setMapper(service, "definitionMapper", RuleDefinitionMapper.class, List.of(offline));
        assertTrue(nodeIds(service.graph("PROJECT", 1L, "DOWNSTREAM", 1)).containsAll(Set.of("VARIABLE:1", "RULE:10")));
        assertFalse(new com.hengshucredit.rule.server.governance.GovernanceImpactService(service).analyze(
                "PROJECT", 1L, "DELETE", com.hengshucredit.rule.server.governance.ResourceSnapshot.ofJson("{\"id\":1}")).isEmpty());
    }

    @Test
    public void fieldsOfDeletedObjectCannotReappearAsIndependentProjectConsumers() {
        RuleLineageService service = serviceWithProjectBranch(false);
        setMapper(service, "variableMapper", RuleVariableMapper.class, List.of());
        setMapper(service, "definitionMapper", RuleDefinitionMapper.class, List.of());
        RuleDataObject object = new RuleDataObject(); object.setId(30L); object.setProjectId(1L); object.setStatus(-1);
        RuleDataObjectField field = dataField(24L, "removed-field"); field.setObjectId(30L); field.setProjectId(1L);
        setMapper(service, "dataObjectMapper", RuleDataObjectMapper.class, List.of(object));
        setMapper(service, "dataObjectFieldMapper", RuleDataObjectFieldMapper.class, List.of(field));
        assertEquals(Set.of("PROJECT:1"), nodeIds(service.graph("PROJECT", 1L, "DOWNSTREAM", 2)));
        org.junit.Assert.assertThrows(IllegalArgumentException.class, () -> service.graph("DATA_FIELD", 24L, "ALL", 1));
    }

    @BeforeClass
    public static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), RuleModel.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), RuleDefinitionContent.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), RulePublished.class);
    }

    @Test
    public void allKeepsDirectionsSeparateAndStopsAtTwoHops() {
        RuleLineageService service = serviceWithProjectBranch(false);

        Map<String, Object> graph = service.graph("VARIABLE", 1L, "ALL", 2);
        Set<String> ids = nodeIds(graph);

        assertTrue(ids.contains("PROJECT:1"));
        assertTrue(ids.contains("VARIABLE:1"));
        assertTrue(ids.contains("RULE:10"));
        assertTrue(ids.contains("VARIABLE:3"));
        assertFalse("项目节点不能反向扩散到兄弟变量", ids.contains("VARIABLE:2"));
    }

    @Test
    public void maxDepthOneDoesNotReturnSecondHop() {
        Map<String, Object> graph = serviceWithProjectBranch(false)
                .graph("VARIABLE", 1L, "DOWNSTREAM", 1);

        assertTrue(nodeIds(graph).contains("RULE:10"));
        assertFalse(nodeIds(graph).contains("VARIABLE:3"));
    }

    @Test
    public void returnedNodesExposeDirectionalExpansionFlags() {
        Map<String, Object> graph = serviceWithProjectBranch(false)
                .graph("VARIABLE", 1L, "ALL", 2);
        Map<String, Object> start = node(graph, "VARIABLE:1");
        Map<String, Object> output = node(graph, "VARIABLE:3");

        assertEquals(Boolean.TRUE, start.get("hasUpstream"));
        assertEquals(Boolean.TRUE, start.get("hasDownstream"));
        assertEquals(Boolean.TRUE, output.get("hasUpstream"));
        assertEquals(Boolean.FALSE, output.get("hasDownstream"));
    }

    @Test
    public void directedTraversalStopsWhenGraphContainsCycle() {
        Map<String, Object> graph = serviceWithProjectBranch(true)
                .graph("VARIABLE", 1L, "DOWNSTREAM", 10);

        assertEquals(new LinkedHashSet<>(Arrays.asList("VARIABLE:1", "RULE:10")), nodeIds(graph));
        assertEquals(2, edges(graph).size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonPositiveDepth() {
        serviceWithProjectBranch(false).graph("VARIABLE", 1L, "ALL", 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownDirection() {
        serviceWithProjectBranch(false).graph("VARIABLE", 1L, "SIDEWAYS", 2);
    }

    @Test
    public void modelQueriesExcludeFileContentAndKeepOtherMetadata() {
        RuleLineageService service = serviceWithProjectBranch(false);
        List<String> selections = new ArrayList<>();
        RuleModelMapper mapper = RuleModelMapper.class.cast(Proxy.newProxyInstance(
                RuleModelMapper.class.getClassLoader(),
                new Class<?>[]{RuleModelMapper.class},
                (proxy, method, args) -> {
                    if ("selectMapsPage".equals(method.getName())) {
                        var wrapper = (com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<?>) args[1];
                        selections.add(wrapper.getSqlSelect());
                        return args[0];
                    }
                    if ("selectList".equals(method.getName())) {
                        @SuppressWarnings("unchecked")
                        LambdaQueryWrapper<RuleModel> wrapper = (LambdaQueryWrapper<RuleModel>) args[0];
                        selections.add(wrapper.getSqlSelect());
                        return Collections.emptyList();
                    }
                    return defaultValue(method.getReturnType());
                }));
        ReflectionTestUtils.setField(service, "modelMapper", mapper);

        service.options("MODEL", "", null);
        service.graph("VARIABLE", 1L, "ALL", 2);

        assertEquals(2, selections.size());
        for (int index = 0; index < selections.size(); index++) {
            String selection = selections.get(index);
            assertNotNull("前端可见的模型查询必须显式声明返回字段", selection);
            String normalized = selection.toLowerCase();
            assertTrue("模型编码必须保留，实际投影：" + normalized,
                    normalized.contains("model_code") || normalized.contains("modelcode"));
            assertFalse("模型文件内容必须排除，实际投影：" + normalized,
                    normalized.contains("model_content") || normalized.contains("modelcontent"));
            assertFalse("图与目录都不使用模型配置，避免加载大字段，实际投影：" + normalized,
                    normalized.contains("model_config") || normalized.contains("modelconfig"));
            assertFalse("图与目录不使用模型文件名，实际投影：" + normalized,
                    normalized.contains("model_file_name") || normalized.contains("modelfilename"));
            assertFalse("图与目录不使用模型文件大小，实际投影：" + normalized,
                    normalized.contains("model_file_size") || normalized.contains("modelfilesize"));
        }
    }

    @Test
    public void modelInputsFollowManagedOperandReferences() {
        RuleModel upstream = model(5L, "detector");
        RuleModel target = model(11L, "gender_age");

        RuleModelOutputField upstreamOutput = new RuleModelOutputField();
        upstreamOutput.setId(7L);
        upstreamOutput.setModelId(5L);

        List<RuleModelInputField> inputs = Arrays.asList(
                modelInput(11L, 24L, "",
                        "{\"kind\":\"FUNCTION\",\"args\":[{\"kind\":\"REFERENCE\",\"refId\":24,\"refType\":\"DATA_OBJECT\",\"code\":\"request.face\"}]}",
                        null, 1),
                modelInput(11L, 7L, "MODEL_OUTPUT",
                        "{\"kind\":\"REFERENCE\",\"refId\":7,\"refType\":\"MODEL_OUTPUT\",\"code\":\"detector.faces\"}",
                        null, 1),
                modelInput(11L, null, null,
                        "{\"kind\":\"LITERAL\",\"value\":\"fallback\"}",
                        "{\"kind\":\"REFERENCE\",\"refId\":36,\"refType\":\"DATA_OBJECT\",\"code\":\"request.idcard\"}", 1),
                modelInput(11L, 25L, "DATA_OBJECT", null, null, 1),
                modelInput(11L, 99L, "DATA_OBJECT",
                        "{\"kind\":\"PATH\",\"code\":\"request.unresolved\"}", null, 1),
                modelInput(11L, 37L, "DATA_OBJECT", null, null, 0),
                modelInput(11L, null, null,
                        "{\"kind\":\"PATH\",\"refId\":50,\"refType\":\"DATA_OBJECT\",\"code\":\"request.parent.missingLeaf\"}",
                        null, 1),
                modelInput(11L, null, null,
                        "{\"kind\":\"REFERENCE\",\"refId\":24,\"refType\":\"DATA_OBJECT\",\"code\":\"request.face\"}",
                        null, 1));

        RuleLineageService service = new RuleLineageService();
        setMapper(service, "definitionContentMapper", RuleDefinitionContentMapper.class, Collections.emptyList());
        setMapper(service, "publishedMapper", RulePublishedMapper.class, Collections.emptyList());
        setMapper(service, "dataObjectMapper", RuleDataObjectMapper.class, Collections.emptyList());
        setMapper(service, "projectMapper", RuleProjectMapper.class, Collections.emptyList());
        setMapper(service, "variableMapper", RuleVariableMapper.class, Collections.emptyList());
        setMapper(service, "definitionMapper", RuleDefinitionMapper.class, Collections.emptyList());
        setMapper(service, "definitionInputFieldMapper", RuleDefinitionInputFieldMapper.class, Collections.emptyList());
        setMapper(service, "definitionOutputFieldMapper", RuleDefinitionOutputFieldMapper.class, Collections.emptyList());
        setMapper(service, "modelMapper", RuleModelMapper.class, Arrays.asList(upstream, target));
        setMapper(service, "modelInputFieldMapper", RuleModelInputFieldMapper.class, inputs);
        setMapper(service, "modelOutputFieldMapper", RuleModelOutputFieldMapper.class,
                Collections.singletonList(upstreamOutput));
        setMapper(service, "externalDatasourceMapper", RuleExternalDatasourceMapper.class, Collections.emptyList());
        setMapper(service, "externalApiConfigMapper", RuleExternalApiConfigMapper.class, Collections.emptyList());
        setMapper(service, "dbDatasourceMapper", RuleDbDatasourceMapper.class, Collections.emptyList());
        setMapper(service, "listLibraryMapper", RuleListLibraryMapper.class, Collections.emptyList());
        setMapper(service, "dataObjectFieldMapper", RuleDataObjectFieldMapper.class, Arrays.asList(
                dataField(24L, "face"), dataField(25L, "legacy"), dataField(36L, "idcard"),
                dataField(37L, "disabled"), dataField(50L, "parent"), dataField(99L, "unresolved")));

        Map<String, Object> graph = service.graph("MODEL", 11L, "UPSTREAM", 1);
        Set<String> ids = nodeIds(graph);

        assertEquals(new LinkedHashSet<>(Arrays.asList(
                "MODEL:11", "DATA_FIELD:24", "MODEL:5", "DATA_FIELD:36",
                "DATA_FIELD:25", "DATA_FIELD:50")), ids);
        assertFalse(ids.contains("DATA_FIELD:37"));
        assertFalse(ids.contains("DATA_FIELD:99"));
        assertEquals("重复引用只能生成一条模型输入边", 5, edges(graph).size());
    }

    private static RuleLineageService serviceWithProjectBranch(boolean cycle) {
        RuleProject project = new RuleProject();
        project.setId(1L);
        project.setProjectCode("credit");
        project.setProjectName("信贷项目");

        RuleVariable start = variable(1L, "start");
        RuleVariable sibling = variable(2L, "sibling");
        RuleVariable output = variable(3L, "output");

        RuleDefinition rule = new RuleDefinition();
        rule.setId(10L);
        rule.setProjectId(1L);
        rule.setRuleCode("approve");
        rule.setRuleName("审批规则");

        RuleDefinitionInputField input = new RuleDefinitionInputField();
        input.setDefinitionId(10L);
        input.setVarId(1L);
        input.setRefType("VARIABLE");

        RuleDefinitionOutputField ruleOutput = new RuleDefinitionOutputField();
        ruleOutput.setDefinitionId(10L);
        ruleOutput.setVarId(cycle ? 1L : 3L);
        ruleOutput.setRefType("VARIABLE");

        RuleLineageService service = new RuleLineageService();
        setMapper(service, "definitionContentMapper", RuleDefinitionContentMapper.class, Collections.emptyList());
        setMapper(service, "publishedMapper", RulePublishedMapper.class, Collections.emptyList());
        setMapper(service, "dataObjectMapper", RuleDataObjectMapper.class, Collections.emptyList());
        setMapper(service, "projectMapper", RuleProjectMapper.class, Collections.singletonList(project));
        setMapper(service, "variableMapper", RuleVariableMapper.class, Arrays.asList(start, sibling, output));
        setMapper(service, "definitionMapper", RuleDefinitionMapper.class, Collections.singletonList(rule));
        setMapper(service, "definitionInputFieldMapper", RuleDefinitionInputFieldMapper.class, Collections.singletonList(input));
        setMapper(service, "definitionOutputFieldMapper", RuleDefinitionOutputFieldMapper.class, Collections.singletonList(ruleOutput));
        setMapper(service, "modelMapper", RuleModelMapper.class, Collections.emptyList());
        setMapper(service, "modelInputFieldMapper", RuleModelInputFieldMapper.class, Collections.emptyList());
        setMapper(service, "modelOutputFieldMapper", RuleModelOutputFieldMapper.class, Collections.emptyList());
        setMapper(service, "externalDatasourceMapper", RuleExternalDatasourceMapper.class, Collections.emptyList());
        setMapper(service, "externalApiConfigMapper", RuleExternalApiConfigMapper.class, Collections.emptyList());
        setMapper(service, "dbDatasourceMapper", RuleDbDatasourceMapper.class, Collections.emptyList());
        setMapper(service, "listLibraryMapper", RuleListLibraryMapper.class, Collections.emptyList());
        setMapper(service, "dataObjectFieldMapper", RuleDataObjectFieldMapper.class, Collections.emptyList());
        return service;
    }

    @Test
    public void fieldCarriesItsObjectAtDepthBoundaryAndKeepsOriginalCode() {
        RuleLineageService service = serviceWithProjectBranch(false);
        RuleDataObject object = new RuleDataObject();
        object.setId(30L);
        object.setObjectCode("Request_ABC");
        object.setObjectLabel("申请数据");
        RuleDataObjectField field = dataField(24L, "Camel_Field");
        field.setObjectId(30L);
        RuleDefinitionInputField input = new RuleDefinitionInputField();
        input.setDefinitionId(10L);
        input.setVarId(24L);
        input.setRefType("DATA_OBJECT");
        setMapper(service, "dataObjectMapper", RuleDataObjectMapper.class, Collections.singletonList(object));
        setMapper(service, "dataObjectFieldMapper", RuleDataObjectFieldMapper.class, Collections.singletonList(field));
        setMapper(service, "definitionInputFieldMapper", RuleDefinitionInputFieldMapper.class, Collections.singletonList(input));

        Map<String, Object> graph = service.graph("RULE", 10L, "UPSTREAM", 1);
        Map<String, Object> fieldNode = node(graph, "DATA_FIELD:24");
        assertEquals("Camel_Field", fieldNode.get("code"));
        @SuppressWarnings("unchecked")
        Map<String, Object> parent = (Map<String, Object>) fieldNode.get("dataObject");
        assertNotNull(parent);
        assertEquals("DATA_OBJECT:30", parent.get("id"));
        assertEquals("Request_ABC", parent.get("code"));
        Map<String, Object> objectGraph = service.graph("DATA_OBJECT", 30L, "DOWNSTREAM", 1);
        assertEquals(new LinkedHashSet<>(Arrays.asList("DATA_OBJECT:30", "DATA_FIELD:24")), nodeIds(objectGraph));
        assertEquals("包含字段", edges(objectGraph).get(0).get("label"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void nestedFieldsKeepParentIdsAndAncestorsAtDepthBoundary() {
        RuleLineageService service = serviceWithProjectBranch(false);
        RuleDataObject object = new RuleDataObject();
        object.setId(30L);
        object.setObjectCode("Request");
        RuleDataObjectField parent = dataField(24L, "profile");
        parent.setObjectId(30L);
        RuleDataObjectField child = dataField(25L, "credit");
        child.setObjectId(30L);
        child.setParentFieldId(24L);
        RuleDataObjectField leaf = dataField(26L, "score");
        leaf.setObjectId(30L);
        leaf.setParentFieldId(25L);
        setMapper(service, "dataObjectMapper", RuleDataObjectMapper.class, Collections.singletonList(object));
        setMapper(service, "dataObjectFieldMapper", RuleDataObjectFieldMapper.class, Arrays.asList(leaf, child, parent));
        Map<String, Object> graph = service.graph("DATA_OBJECT", 30L, "DOWNSTREAM", 1);
        assertEquals(new LinkedHashSet<>(Arrays.asList("DATA_OBJECT:30", "DATA_FIELD:24")), nodeIds(graph));
        assertEquals(Boolean.TRUE, node(graph, "DATA_FIELD:24").get("hasFieldChildren"));
        graph = service.graph("DATA_FIELD", 25L, "DOWNSTREAM", 1);
        assertEquals(new LinkedHashSet<>(Arrays.asList("DATA_FIELD:25", "DATA_FIELD:26")), nodeIds(graph));
        Map<String, Object> leafNode = node(graph, "DATA_FIELD:26");
        assertEquals("DATA_FIELD:25", leafNode.get("parentNodeId"));
        List<Map<String, Object>> ancestors = (List<Map<String, Object>>) leafNode.get("ancestorFields");
        assertEquals(2, ancestors.size());
        assertEquals("DATA_FIELD:24", ancestors.get(0).get("id"));
        assertEquals("DATA_FIELD:25", ancestors.get(1).get("id"));
        assertEquals("DATA_OBJECT:30", ancestors.get(0).get("parentNodeId"));
    }

    private static RuleVariable variable(Long id, String code) {
        RuleVariable variable = new RuleVariable();
        variable.setId(id);
        variable.setProjectId(1L);
        variable.setScope(RuleVariableService.SCOPE_PROJECT);
        variable.setVarCode(code);
        variable.setVarLabel(code);
        return variable;
    }

    private static RuleModel model(Long id, String code) {
        RuleModel model = new RuleModel();
        model.setId(id);
        model.setModelCode(code);
        model.setModelName(code);
        return model;
    }

    private static RuleModelInputField modelInput(Long modelId, Long varId, String refType,
                                                   String sourceOperand, String defaultOperand,
                                                   Integer status) {
        RuleModelInputField field = new RuleModelInputField();
        field.setModelId(modelId);
        field.setVarId(varId);
        field.setRefType(refType);
        field.setSourceOperand(sourceOperand);
        field.setDefaultOperand(defaultOperand);
        field.setStatus(status);
        return field;
    }

    private static RuleDataObjectField dataField(Long id, String code) {
        RuleDataObjectField field = new RuleDataObjectField();
        field.setId(id);
        field.setVarCode(code);
        field.setVarLabel(code);
        return field;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> nodeIds(Map<String, Object> graph) {
        Set<String> result = new LinkedHashSet<>();
        for (Map<String, Object> item : (List<Map<String, Object>>) graph.get("nodes")) {
            result.add((String) item.get("id"));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> node(Map<String, Object> graph, String id) {
        for (Map<String, Object> item : (List<Map<String, Object>>) graph.get("nodes")) {
            if (id.equals(item.get("id"))) {
                return item;
            }
        }
        throw new AssertionError("Missing node " + id);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> edges(Map<String, Object> graph) {
        return (List<Map<String, Object>>) graph.get("edges");
    }

    private static <T> void setMapper(RuleLineageService service, String fieldName,
                                      Class<T> mapperType, List<?> rows) {
        T mapper = mapperType.cast(Proxy.newProxyInstance(
                mapperType.getClassLoader(),
                new Class<?>[]{mapperType},
                (proxy, method, args) -> {
                    if ("selectList".equals(method.getName())) return rows;
                    return defaultValue(method.getReturnType());
                }));
        ReflectionTestUtils.setField(service, fieldName, mapper);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == void.class) return null;
        return 0;
    }
}
