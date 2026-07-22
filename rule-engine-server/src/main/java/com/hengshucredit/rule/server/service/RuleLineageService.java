package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleDbDatasource;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.model.entity.RuleListLibrary;
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
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
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
            LambdaQueryWrapper<RuleModel> wrapper = withoutModelContent();
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

    public Map<String, Object> graph(String nodeType, Long nodeId, String direction, Integer maxDepth) {
        FullGraph full = buildFullGraph();
        String startKey = nodeKey(normalizeType(nodeType), nodeId);
        if (!full.nodes.containsKey(startKey)) {
            throw new IllegalArgumentException("血缘起点不存在");
        }
        String dir = normalizeDirection(direction);
        int depth = normalizeMaxDepth(maxDepth);
        Set<String> selectedNodes = new LinkedHashSet<>();
        Set<String> selectedEdges = new LinkedHashSet<>();
        selectedNodes.add(startKey);
        if ("ALL".equals(dir) || "UPSTREAM".equals(dir)) {
            traverse(full, startKey, "UPSTREAM", depth, selectedNodes, selectedEdges);
        }
        if ("ALL".equals(dir) || "DOWNSTREAM".equals(dir)) {
            traverse(full, startKey, "DOWNSTREAM", depth, selectedNodes, selectedEdges);
        }
        return graphResult(full, startKey, selectedNodes, selectedEdges);
    }

    private void traverse(FullGraph full, String startKey, String direction, int maxDepth,
                          Set<String> selectedNodes, Set<String> selectedEdges) {
        Set<String> visited = new LinkedHashSet<>();
        Queue<TraversalStep> queue = new ArrayDeque<>();
        visited.add(startKey);
        queue.add(new TraversalStep(startKey, 0));
        while (!queue.isEmpty()) {
            TraversalStep step = queue.poll();
            if (step.depth >= maxDepth) continue;
            List<Map<String, Object>> related = "UPSTREAM".equals(direction)
                    ? full.incomingEdges.getOrDefault(step.nodeKey, Collections.emptyList())
                    : full.outgoingEdges.getOrDefault(step.nodeKey, Collections.emptyList());
            for (Map<String, Object> edge : related) {
                String next = "UPSTREAM".equals(direction)
                        ? (String) edge.get("from")
                        : (String) edge.get("to");
                if (!full.nodes.containsKey(next)) continue;
                selectedNodes.add(next);
                selectedEdges.add(edgeKey(edge));
                if (visited.add(next)) {
                    queue.add(new TraversalStep(next, step.depth + 1));
                }
            }
        }
    }

    private Map<String, Object> graphResult(FullGraph full, String startKey,
                                            Set<String> selectedNodes, Set<String> selectedEdges) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (String key : selectedNodes) {
            Map<String, Object> node = full.nodes.get(key);
            if (node != null) nodes.add(nodeWithRelations(full, key, node));
        }
        List<Map<String, Object>> edges = new ArrayList<>();
        for (Map<String, Object> edge : full.edges) {
            if (selectedEdges.contains(edgeKey(edge))) {
                edges.add(edge);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("startNode", nodeWithRelations(full, startKey, full.nodes.get(startKey)));
        result.put("nodes", nodes);
        result.put("edges", edges);
        return result;
    }

    private Map<String, Object> nodeWithRelations(FullGraph full, String key, Map<String, Object> source) {
        Map<String, Object> node = new LinkedHashMap<>(source);
        node.put("hasUpstream", hasValidNeighbor(full, key, "UPSTREAM"));
        node.put("hasDownstream", hasValidNeighbor(full, key, "DOWNSTREAM"));
        return node;
    }

    private boolean hasValidNeighbor(FullGraph full, String key, String direction) {
        List<Map<String, Object>> related = "UPSTREAM".equals(direction)
                ? full.incomingEdges.getOrDefault(key, Collections.emptyList())
                : full.outgoingEdges.getOrDefault(key, Collections.emptyList());
        for (Map<String, Object> edge : related) {
            String next = "UPSTREAM".equals(direction)
                    ? (String) edge.get("from")
                    : (String) edge.get("to");
            if (full.nodes.containsKey(next)) return true;
        }
        return false;
    }

    private String edgeKey(Map<String, Object> edge) {
        return edge.get("from") + "->" + edge.get("to") + ":" + edge.get("label");
    }

    private String normalizeDirection(String direction) {
        String value = direction == null || direction.trim().isEmpty()
                ? "ALL" : direction.trim().toUpperCase();
        if (!"ALL".equals(value) && !"UPSTREAM".equals(value) && !"DOWNSTREAM".equals(value)) {
            throw new IllegalArgumentException("不支持的血缘方向");
        }
        return value;
    }

    private int normalizeMaxDepth(Integer maxDepth) {
        if (maxDepth == null) return Integer.MAX_VALUE;
        if (maxDepth <= 0) throw new IllegalArgumentException("血缘层级必须大于 0");
        return maxDepth;
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
        for (RuleModel item : modelMapper.selectList(withoutModelContent())) {
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

    private LambdaQueryWrapper<RuleModel> withoutModelContent() {
        return new LambdaQueryWrapper<RuleModel>()
                .select(RuleModel.class, field -> !"modelContent".equals(field.getProperty()));
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
        List<RuleModelOutputField> outputFields = modelOutputFieldMapper.selectList(
                new LambdaQueryWrapper<RuleModelOutputField>());
        Map<Long, Long> outputModelIds = new LinkedHashMap<>();
        for (RuleModelOutputField field : outputFields) {
            if (field.getId() != null && field.getModelId() != null) {
                outputModelIds.put(field.getId(), field.getModelId());
            }
        }
        Set<String> inputEdgeKeys = new LinkedHashSet<>();
        for (RuleModelInputField field : modelInputFieldMapper.selectList(new LambdaQueryWrapper<RuleModelInputField>())) {
            if (Integer.valueOf(0).equals(field.getStatus())) continue;
            String to = nodeKey("MODEL", field.getModelId());
            boolean operandConfigured = hasText(field.getSourceOperand()) || hasText(field.getDefaultOperand());
            if (operandConfigured) {
                List<JSONObject> references = new ArrayList<>();
                collectOperandReferences(field.getSourceOperand(), references);
                collectOperandReferences(field.getDefaultOperand(), references);
                for (JSONObject reference : references) {
                    String from = modelInputRefNodeKey(
                            reference.getString("refType"), reference.getLong("refId"), outputModelIds);
                    addModelInputEdge(graph, inputEdgeKeys, from, to);
                }
                continue;
            }
            String from = modelInputRefNodeKey(field.getRefType(), field.getVarId(), outputModelIds);
            addModelInputEdge(graph, inputEdgeKeys, from, to);
        }
        for (RuleModelOutputField field : outputFields) {
            String from = nodeKey("MODEL", field.getModelId());
            String to = refNodeKey(field.getRefType(), field.getVarId());
            if (to != null) addEdge(graph, from, to, "模型输出");
        }
    }

    private void collectOperandReferences(String operandJson, List<JSONObject> references) {
        if (!hasText(operandJson)) return;
        try {
            references.addAll(OperandValueResolver.collectReferences(operandJson));
        } catch (RuntimeException ignored) {
            // Invalid historical operands are not guessed from mutable code or label fields.
        }
    }

    private String modelInputRefNodeKey(String refType, Long refId, Map<Long, Long> outputModelIds) {
        if (refId == null || !hasText(refType)) return null;
        String type = normalizeType(refType);
        if ("MODEL_OUTPUT".equals(type)) {
            Long modelId = outputModelIds.get(refId);
            return modelId == null ? null : nodeKey("MODEL", modelId);
        }
        return refNodeKey(type, refId);
    }

    private void addModelInputEdge(FullGraph graph, Set<String> inputEdgeKeys,
                                   String from, String to) {
        if (from == null || to == null) return;
        if (inputEdgeKeys.add(from + "->" + to)) {
            addEdge(graph, from, to, "模型输入");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
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
        graph.outgoingEdges.computeIfAbsent(from, key -> new ArrayList<>()).add(edge);
        graph.incomingEdges.computeIfAbsent(to, key -> new ArrayList<>()).add(edge);
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
        private final Map<String, List<Map<String, Object>>> outgoingEdges = new LinkedHashMap<>();
        private final Map<String, List<Map<String, Object>>> incomingEdges = new LinkedHashMap<>();
    }

    private static class TraversalStep {
        private final String nodeKey;
        private final int depth;

        private TraversalStep(String nodeKey, int depth) {
            this.nodeKey = nodeKey;
            this.depth = depth;
        }
    }
}
