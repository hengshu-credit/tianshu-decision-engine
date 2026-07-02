package com.bjjw.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjjw.rule.model.entity.RuleDataObjectField;
import com.bjjw.rule.model.entity.RuleDbDatasource;
import com.bjjw.rule.model.entity.RuleDefinition;
import com.bjjw.rule.model.entity.RuleDefinitionInputField;
import com.bjjw.rule.model.entity.RuleDefinitionOutputField;
import com.bjjw.rule.model.entity.RuleExternalApiConfig;
import com.bjjw.rule.model.entity.RuleExternalDatasource;
import com.bjjw.rule.model.entity.RuleListLibrary;
import com.bjjw.rule.model.entity.RuleModel;
import com.bjjw.rule.model.entity.RuleModelInputField;
import com.bjjw.rule.model.entity.RuleModelOutputField;
import com.bjjw.rule.model.entity.RuleProject;
import com.bjjw.rule.model.entity.RuleVariable;
import com.bjjw.rule.server.mapper.RuleDataObjectFieldMapper;
import com.bjjw.rule.server.mapper.RuleDbDatasourceMapper;
import com.bjjw.rule.server.mapper.RuleDefinitionInputFieldMapper;
import com.bjjw.rule.server.mapper.RuleDefinitionMapper;
import com.bjjw.rule.server.mapper.RuleDefinitionOutputFieldMapper;
import com.bjjw.rule.server.mapper.RuleExternalApiConfigMapper;
import com.bjjw.rule.server.mapper.RuleExternalDatasourceMapper;
import com.bjjw.rule.server.mapper.RuleListLibraryMapper;
import com.bjjw.rule.server.mapper.RuleModelInputFieldMapper;
import com.bjjw.rule.server.mapper.RuleModelMapper;
import com.bjjw.rule.server.mapper.RuleModelOutputFieldMapper;
import com.bjjw.rule.server.mapper.RuleProjectMapper;
import com.bjjw.rule.server.mapper.RuleVariableMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

@Service
public class RuleLineageService {

    @Resource private RuleProjectMapper projectMapper;
    @Resource private RuleVariableMapper variableMapper;
    @Resource private RuleDefinitionMapper definitionMapper;
    @Resource private RuleDefinitionInputFieldMapper definitionInputFieldMapper;
    @Resource private RuleDefinitionOutputFieldMapper definitionOutputFieldMapper;
    @Resource private RuleModelMapper modelMapper;
    @Resource private RuleModelInputFieldMapper modelInputFieldMapper;
    @Resource private RuleModelOutputFieldMapper modelOutputFieldMapper;
    @Resource private RuleExternalDatasourceMapper externalDatasourceMapper;
    @Resource private RuleExternalApiConfigMapper externalApiConfigMapper;
    @Resource private RuleDbDatasourceMapper dbDatasourceMapper;
    @Resource private RuleListLibraryMapper listLibraryMapper;
    @Resource private RuleDataObjectFieldMapper dataObjectFieldMapper;

    public List<Map<String, Object>> options(String nodeType, String keyword, Long projectId) {
        String type = normalizeType(nodeType);
        List<Map<String, Object>> result = new ArrayList<>();
        if ("PROJECT".equals(type)) {
            for (RuleProject item : projectMapper.selectList(new LambdaQueryWrapper<RuleProject>().orderByDesc(RuleProject::getCreateTime))) {
                addOption(result, "PROJECT", item.getId(), item.getProjectCode(), item.getProjectName(), keyword);
            }
        } else if ("VARIABLE".equals(type)) {
            LambdaQueryWrapper<RuleVariable> wrapper = new LambdaQueryWrapper<>();
            appendProjectScope(wrapper, projectId, RuleVariable::getProjectId, RuleVariable::getScope);
            wrapper.orderByDesc(RuleVariable::getCreateTime);
            for (RuleVariable item : variableMapper.selectList(wrapper)) {
                addOption(result, "VARIABLE", item.getId(), item.getVarCode(), item.getVarLabel(), keyword);
            }
        } else if ("RULE".equals(type)) {
            LambdaQueryWrapper<RuleDefinition> wrapper = new LambdaQueryWrapper<>();
            appendProject(wrapper, projectId, RuleDefinition::getProjectId);
            wrapper.orderByDesc(RuleDefinition::getCreateTime);
            for (RuleDefinition item : definitionMapper.selectList(wrapper)) {
                addOption(result, "RULE", item.getId(), item.getRuleCode(), item.getRuleName(), keyword);
            }
        } else if ("MODEL".equals(type)) {
            LambdaQueryWrapper<RuleModel> wrapper = new LambdaQueryWrapper<>();
            appendProjectScope(wrapper, projectId, RuleModel::getProjectId, RuleModel::getScope);
            wrapper.orderByDesc(RuleModel::getCreateTime);
            for (RuleModel item : modelMapper.selectList(wrapper)) {
                addOption(result, "MODEL", item.getId(), item.getModelCode(), item.getModelName(), keyword);
            }
        } else if ("API".equals(type)) {
            for (RuleExternalApiConfig item : externalApiConfigMapper.selectList(new LambdaQueryWrapper<RuleExternalApiConfig>().orderByDesc(RuleExternalApiConfig::getCreateTime))) {
                addOption(result, "API", item.getId(), item.getApiCode(), item.getApiName(), keyword);
            }
        } else if ("DB".equals(type)) {
            LambdaQueryWrapper<RuleDbDatasource> wrapper = new LambdaQueryWrapper<>();
            appendProjectScope(wrapper, projectId, RuleDbDatasource::getProjectId, RuleDbDatasource::getScope);
            wrapper.orderByDesc(RuleDbDatasource::getCreateTime);
            for (RuleDbDatasource item : dbDatasourceMapper.selectList(wrapper)) {
                addOption(result, "DB", item.getId(), item.getDatasourceCode(), item.getDatasourceName(), keyword);
            }
        } else if ("LIST".equals(type)) {
            LambdaQueryWrapper<RuleListLibrary> wrapper = new LambdaQueryWrapper<>();
            appendProjectScope(wrapper, projectId, RuleListLibrary::getProjectId, RuleListLibrary::getScope);
            wrapper.orderByDesc(RuleListLibrary::getCreateTime);
            for (RuleListLibrary item : listLibraryMapper.selectList(wrapper)) {
                addOption(result, "LIST", item.getId(), item.getListCode(), item.getListName(), keyword);
            }
        } else if ("DATASOURCE".equals(type)) {
            LambdaQueryWrapper<RuleExternalDatasource> wrapper = new LambdaQueryWrapper<>();
            appendProjectScope(wrapper, projectId, RuleExternalDatasource::getProjectId, RuleExternalDatasource::getScope);
            wrapper.orderByDesc(RuleExternalDatasource::getCreateTime);
            for (RuleExternalDatasource item : externalDatasourceMapper.selectList(wrapper)) {
                addOption(result, "DATASOURCE", item.getId(), item.getDatasourceCode(), item.getDatasourceName(), keyword);
            }
        }
        return result.size() > 80 ? new ArrayList<>(result.subList(0, 80)) : result;
    }

    public Map<String, Object> graph(String nodeType, Long nodeId, String direction) {
        FullGraph full = buildFullGraph();
        String startKey = nodeKey(normalizeType(nodeType), nodeId);
        if (!full.nodes.containsKey(startKey)) {
            throw new IllegalArgumentException("血缘起点不存在");
        }
        String dir = direction == null || direction.trim().isEmpty() ? "ALL" : direction.trim().toUpperCase();
        Set<String> selectedNodes = new LinkedHashSet<>();
        Set<String> selectedEdges = new LinkedHashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        selectedNodes.add(startKey);
        queue.add(startKey);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (Map<String, Object> edge : full.edges) {
                String from = (String) edge.get("from");
                String to = (String) edge.get("to");
                boolean downstream = ("ALL".equals(dir) || "DOWNSTREAM".equals(dir)) && current.equals(from);
                boolean upstream = ("ALL".equals(dir) || "UPSTREAM".equals(dir)) && current.equals(to);
                if (!downstream && !upstream) {
                    continue;
                }
                String next = downstream ? to : from;
                String edgeKey = from + "->" + to + ":" + edge.get("label");
                selectedEdges.add(edgeKey);
                if (selectedNodes.add(next)) {
                    queue.add(next);
                }
            }
        }
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (String key : selectedNodes) {
            nodes.add(full.nodes.get(key));
        }
        List<Map<String, Object>> edges = new ArrayList<>();
        for (Map<String, Object> edge : full.edges) {
            String edgeKey = edge.get("from") + "->" + edge.get("to") + ":" + edge.get("label");
            if (selectedEdges.contains(edgeKey)) {
                edges.add(edge);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("startNode", full.nodes.get(startKey));
        result.put("nodes", nodes);
        result.put("edges", edges);
        return result;
    }

    private FullGraph buildFullGraph() {
        FullGraph graph = new FullGraph();
        List<RuleProject> projects = projectMapper.selectList(new LambdaQueryWrapper<RuleProject>());
        for (RuleProject item : projects) {
            addNode(graph, "PROJECT", item.getId(), item.getProjectCode(), item.getProjectName());
        }
        for (RuleVariable item : variableMapper.selectList(new LambdaQueryWrapper<RuleVariable>())) {
            addNode(graph, "VARIABLE", item.getId(), item.getVarCode(), item.getVarLabel());
            addProjectEdge(graph, item.getProjectId(), nodeKey("VARIABLE", item.getId()));
            addVariableSourceEdges(graph, item);
        }
        for (RuleDataObjectField item : dataObjectFieldMapper.selectList(new LambdaQueryWrapper<RuleDataObjectField>())) {
            addNode(graph, "DATA_FIELD", item.getId(), item.getScriptName() != null ? item.getScriptName() : item.getVarCode(), item.getVarLabel());
            addProjectEdge(graph, item.getProjectId(), nodeKey("DATA_FIELD", item.getId()));
        }
        for (RuleDefinition item : definitionMapper.selectList(new LambdaQueryWrapper<RuleDefinition>())) {
            addNode(graph, "RULE", item.getId(), item.getRuleCode(), item.getRuleName());
            addProjectEdge(graph, item.getProjectId(), nodeKey("RULE", item.getId()));
        }
        for (RuleModel item : modelMapper.selectList(new LambdaQueryWrapper<RuleModel>())) {
            addNode(graph, "MODEL", item.getId(), item.getModelCode(), item.getModelName());
            addProjectEdge(graph, item.getProjectId(), nodeKey("MODEL", item.getId()));
        }
        for (RuleExternalDatasource item : externalDatasourceMapper.selectList(new LambdaQueryWrapper<RuleExternalDatasource>())) {
            addNode(graph, "DATASOURCE", item.getId(), item.getDatasourceCode(), item.getDatasourceName());
            addProjectEdge(graph, item.getProjectId(), nodeKey("DATASOURCE", item.getId()));
        }
        for (RuleExternalApiConfig item : externalApiConfigMapper.selectList(new LambdaQueryWrapper<RuleExternalApiConfig>())) {
            addNode(graph, "API", item.getId(), item.getApiCode(), item.getApiName());
            if (item.getDatasourceId() != null) {
                addEdge(graph, nodeKey("DATASOURCE", item.getDatasourceId()), nodeKey("API", item.getId()), "包含API");
            }
        }
        for (RuleDbDatasource item : dbDatasourceMapper.selectList(new LambdaQueryWrapper<RuleDbDatasource>())) {
            addNode(graph, "DB", item.getId(), item.getDatasourceCode(), item.getDatasourceName());
            addProjectEdge(graph, item.getProjectId(), nodeKey("DB", item.getId()));
        }
        for (RuleListLibrary item : listLibraryMapper.selectList(new LambdaQueryWrapper<RuleListLibrary>())) {
            addNode(graph, "LIST", item.getId(), item.getListCode(), item.getListName());
            addProjectEdge(graph, item.getProjectId(), nodeKey("LIST", item.getId()));
        }
        addRuleFieldEdges(graph);
        addModelFieldEdges(graph);
        return graph;
    }

    private void addVariableSourceEdges(FullGraph graph, RuleVariable variable) {
        if (variable == null || variable.getId() == null) {
            return;
        }
        JSONObject config = parseObject(variable.getSourceConfig());
        String target = nodeKey("VARIABLE", variable.getId());
        if ("API".equals(variable.getVarSource())) {
            Long apiConfigId = config.getLong("apiConfigId");
            if (apiConfigId != null) addEdge(graph, nodeKey("API", apiConfigId), target, "接口取数");
        } else if ("DB".equals(variable.getVarSource())) {
            Long datasourceId = config.getLong("datasourceId");
            if (datasourceId != null) addEdge(graph, nodeKey("DB", datasourceId), target, "数据库查询");
        } else if ("LIST".equals(variable.getVarSource())) {
            Long listId = config.getLong("listId");
            if (listId == null) listId = config.getLong("listLibraryId");
            if (listId != null) addEdge(graph, nodeKey("LIST", listId), target, "名单匹配");
        }
    }

    private void addRuleFieldEdges(FullGraph graph) {
        for (RuleDefinitionInputField field : definitionInputFieldMapper.selectList(new LambdaQueryWrapper<RuleDefinitionInputField>())) {
            String from = refNodeKey(field.getRefType(), field.getVarId());
            String to = nodeKey("RULE", field.getDefinitionId());
            if (from != null) addEdge(graph, from, to, "规则输入");
        }
        for (RuleDefinitionOutputField field : definitionOutputFieldMapper.selectList(new LambdaQueryWrapper<RuleDefinitionOutputField>())) {
            String from = nodeKey("RULE", field.getDefinitionId());
            String to = refNodeKey(field.getRefType(), field.getVarId());
            if (to != null) addEdge(graph, from, to, "规则输出");
        }
    }

    private void addModelFieldEdges(FullGraph graph) {
        for (RuleModelInputField field : modelInputFieldMapper.selectList(new LambdaQueryWrapper<RuleModelInputField>())) {
            String from = refNodeKey(field.getRefType(), field.getVarId());
            String to = nodeKey("MODEL", field.getModelId());
            if (from != null) addEdge(graph, from, to, "模型输入");
        }
        for (RuleModelOutputField field : modelOutputFieldMapper.selectList(new LambdaQueryWrapper<RuleModelOutputField>())) {
            String from = nodeKey("MODEL", field.getModelId());
            String to = refNodeKey(field.getRefType(), field.getVarId());
            if (to != null) addEdge(graph, from, to, "模型输出");
        }
    }

    private void addProjectEdge(FullGraph graph, Long projectId, String targetKey) {
        if (projectId != null && projectId > 0) {
            addEdge(graph, nodeKey("PROJECT", projectId), targetKey, "项目包含");
        }
    }

    private String refNodeKey(String refType, Long refId) {
        if (refId == null) return null;
        String type = normalizeType(refType);
        if ("CONSTANT".equals(type) || "VARIABLE".equals(type)) return nodeKey("VARIABLE", refId);
        if ("DATA_OBJECT".equals(type) || "DATA_FIELD".equals(type)) return nodeKey("DATA_FIELD", refId);
        if ("MODEL".equals(type)) return nodeKey("MODEL", refId);
        if ("API".equals(type) || "DB".equals(type) || "LIST".equals(type)) return nodeKey(type, refId);
        return null;
    }

    private void addNode(FullGraph graph, String type, Long id, String code, String label) {
        if (id == null) return;
        String key = nodeKey(type, id);
        if (graph.nodes.containsKey(key)) return;
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", key);
        node.put("type", type);
        node.put("refId", id);
        node.put("code", code);
        node.put("label", label != null && !label.trim().isEmpty() ? label : code);
        graph.nodes.put(key, node);
    }

    private void addEdge(FullGraph graph, String from, String to, String label) {
        if (from == null || to == null || from.equals(to)) return;
        Map<String, Object> edge = new LinkedHashMap<>();
        edge.put("from", from);
        edge.put("to", to);
        edge.put("label", label);
        graph.edges.add(edge);
    }

    private void addOption(List<Map<String, Object>> result, String type, Long id, String code, String label, String keyword) {
        if (!matches(keyword, code, label)) return;
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", type);
        item.put("id", id);
        item.put("code", code);
        item.put("label", label);
        item.put("displayName", (label == null || label.trim().isEmpty() ? code : label) + " (" + code + ")");
        result.add(item);
    }

    private boolean matches(String keyword, String code, String label) {
        if (keyword == null || keyword.trim().isEmpty()) return true;
        String lower = keyword.trim().toLowerCase();
        return (code != null && code.toLowerCase().contains(lower))
                || (label != null && label.toLowerCase().contains(lower));
    }

    private JSONObject parseObject(String json) {
        if (json == null || json.trim().isEmpty()) return new JSONObject();
        try {
            return JSON.parseObject(json);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private String nodeKey(String type, Long id) {
        return normalizeType(type) + ":" + id;
    }

    private String normalizeType(String type) {
        if (type == null || type.trim().isEmpty()) return "";
        String value = type.trim().toUpperCase();
        if ("DEFINITION".equals(value)) return "RULE";
        if ("DATABASE".equals(value)) return "DB";
        return value;
    }

    private <T> void appendProject(LambdaQueryWrapper<T> wrapper, Long projectId,
                                   com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, Long> projectColumn) {
        if (projectId != null && projectId > 0) {
            wrapper.eq(projectColumn, projectId);
        }
    }

    private <T> void appendProjectScope(LambdaQueryWrapper<T> wrapper, Long projectId,
                                        com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, Long> projectColumn,
                                        com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, String> scopeColumn) {
        if (projectId != null && projectId > 0) {
            wrapper.and(w -> w.eq(scopeColumn, RuleVariableService.SCOPE_GLOBAL)
                    .or()
                    .eq(scopeColumn, RuleVariableService.SCOPE_PROJECT)
                    .eq(projectColumn, projectId));
        }
    }

    private static class FullGraph {
        private final Map<String, Map<String, Object>> nodes = new LinkedHashMap<>();
        private final List<Map<String, Object>> edges = new ArrayList<>();
    }
}
