package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
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
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuleLineageServiceTest {

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
