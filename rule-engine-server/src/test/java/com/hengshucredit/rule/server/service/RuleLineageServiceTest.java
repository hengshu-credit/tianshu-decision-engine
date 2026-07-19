package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleModelInputField;
import com.hengshucredit.rule.model.entity.RuleModelOutputField;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDbDatasourceMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionInputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionMapper;
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

    @BeforeClass
    public static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), RuleModel.class);
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
        for (String selection : selections) {
            assertNotNull("前端可见的模型查询必须显式声明返回字段", selection);
            String normalized = selection.toLowerCase();
            assertTrue("模型编码必须保留，实际投影：" + normalized,
                    normalized.contains("model_code") || normalized.contains("modelcode"));
            assertTrue("模型配置必须保留，实际投影：" + normalized,
                    normalized.contains("model_config") || normalized.contains("modelconfig"));
            assertTrue("模型文件名必须保留，实际投影：" + normalized,
                    normalized.contains("model_file_name") || normalized.contains("modelfilename"));
            assertTrue("模型文件大小必须保留，实际投影：" + normalized,
                    normalized.contains("model_file_size") || normalized.contains("modelfilesize"));
            assertFalse("模型文件内容必须排除，实际投影：" + normalized,
                    normalized.contains("model_content") || normalized.contains("modelcontent"));
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
