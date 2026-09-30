package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleDataObject;
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
import com.hengshucredit.rule.server.mapper.RuleDataObjectMapper;
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
import com.hengshucredit.rule.server.mapper.RuleFunctionMapper;
import com.hengshucredit.rule.server.mapper.RuleExperimentMapper;
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
    @Resource private RuleDataObjectMapper dataObjectMapper;
    @Resource private RuleFunctionMapper functionMapper;
    @Resource private RuleExperimentMapper experimentMapper;

    public List<Map<String, Object>> options(String nodeType, String keyword, Long projectId) {
        return pageOptions(nodeType, keyword, projectId, 1, 80).getRecords();
    }

    public Page<Map<String, Object>> pageOptions(String nodeType, String keyword, Long projectId,
                                                 int pageNum, int pageSize) {
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("候选分页参数无效：页码需大于 0，每页数量需为 1 至 100");
        }
        String type = normalizeType(nodeType);
        OptionSource source = switch (type) {
            case "PROJECT" -> new OptionSource(projectMapper, "project_code", "project_name", false, "id");
            case "RULE" -> new OptionSource(definitionMapper, "rule_code", "rule_name", true, "project_id");
            case "VARIABLE" -> new OptionSource(variableMapper, "var_code", "var_label", true, "project_id");
            case "DATA_OBJECT" -> new OptionSource(dataObjectMapper, "object_code", "object_label", true, "project_id");
            case "FUNCTION" -> new OptionSource(functionMapper, "func_code", "func_name", true, "project_id");
            case "MODEL" -> new OptionSource(modelMapper, "model_code", "model_name", true, "project_id");
            case "EXPERIMENT" -> new OptionSource(experimentMapper, "experiment_code", "experiment_name", false, "project_id");
            case "DB" -> new OptionSource(dbDatasourceMapper, "datasource_code", "datasource_name", true, "project_id");
            case "LIST" -> new OptionSource(listLibraryMapper, "list_code", "list_name", true, "project_id");
            case "DATASOURCE" -> new OptionSource(externalDatasourceMapper, "datasource_code", "datasource_name", true, "project_id");
            case "API" -> new OptionSource(externalApiConfigMapper, "api_code", "api_name", false, "datasource_id");
            default -> null;
        };
        if (source == null) return new Page<Map<String, Object>>(pageNum, pageSize).setRecords(List.of());
        Page<Map<String, Object>> result = queryOptions(source.mapper(), source, type, keyword, projectId, pageNum, pageSize);
        enrichOptionProjects(type, result.getRecords());
        return result;
    }

    private <T> Page<Map<String, Object>> queryOptions(BaseMapper<T> mapper, OptionSource source, String type,
                                                      String keyword, Long projectId, int pageNum, int pageSize) {
        QueryWrapper<T> query = new QueryWrapper<>();
        List<String> columns = new ArrayList<>(List.of("id", source.codeColumn() + " AS code",
                source.labelColumn() + " AS label", source.ownerColumn() + ("API".equals(type) ? " AS datasourceId" : " AS projectId")));
        if (source.scoped()) columns.add("scope");
        query.select(columns).orderByDesc("id");
        if (keyword != null && !keyword.trim().isEmpty()) {
            String search = keyword.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            query.and(w -> w.like(source.codeColumn(), search).or().like(source.labelColumn(), search));
        }
        if (projectId != null && projectId > 0 && !"PROJECT".equals(type) && !"API".equals(type)) {
            if (source.scoped() && !"RULE".equals(type)) {
                query.and(w -> w.eq("scope", "GLOBAL").or().eq("scope", "PROJECT").eq("project_id", projectId));
            } else query.eq("project_id", projectId);
        }
        return mapper.selectMapsPage(new Page<>(pageNum, pageSize), query);
    }

    private void enrichOptionProjects(String type, List<Map<String, Object>> records) {
        if (records.isEmpty()) return;
        if ("API".equals(type)) {
            Set<Long> datasourceIds = new LinkedHashSet<>();
            for (Map<String, Object> row : records) {
                if (row.get("datasourceId") instanceof Number id) datasourceIds.add(id.longValue());
            }
            Map<Long, RuleExternalDatasource> datasources = new LinkedHashMap<>();
            if (!datasourceIds.isEmpty()) {
                for (RuleExternalDatasource item : externalDatasourceMapper.selectList(new LambdaQueryWrapper<RuleExternalDatasource>()
                        .select(RuleExternalDatasource::getId, RuleExternalDatasource::getProjectId, RuleExternalDatasource::getScope)
                        .in(RuleExternalDatasource::getId, datasourceIds))) datasources.put(item.getId(), item);
            }
            for (Map<String, Object> row : records) {
                RuleExternalDatasource item = row.get("datasourceId") instanceof Number id ? datasources.get(id.longValue()) : null;
                row.put("projectId", item == null ? null : item.getProjectId());
                row.put("scope", item == null ? "UNBOUND" : item.getScope());
            }
        }
        Set<Long> projectIds = new LinkedHashSet<>();
        for (Map<String, Object> row : records) {
            if (row.get("projectId") instanceof Number id && id.longValue() > 0) projectIds.add(id.longValue());
        }
        Map<Long, RuleProject> projects = new LinkedHashMap<>();
        if (!projectIds.isEmpty()) {
            for (RuleProject project : projectMapper.selectList(new LambdaQueryWrapper<RuleProject>()
                    .select(RuleProject::getId, RuleProject::getProjectCode, RuleProject::getProjectName)
                    .in(RuleProject::getId, projectIds))) projects.put(project.getId(), project);
        }
        for (Map<String, Object> row : records) {
            Long id = row.get("projectId") instanceof Number value ? value.longValue() : null;
            row.put("type", type);
            if (row.get("scope") == null) row.put("scope", id != null && id > 0 ? "PROJECT" : "GLOBAL");
            RuleProject project = projects.get(id);
            if (project != null) {
                row.put("projectCode", project.getProjectCode());
                row.put("projectName", project.getProjectName());
            }
            Object label = row.get("label");
            row.put("displayName", (label == null || label.toString().trim().isEmpty() ? row.get("code") : label) + " (" + row.get("code") + ")");
        }
    }

    private record OptionSource(BaseMapper<?> mapper, String codeColumn, String labelColumn, boolean scoped, String ownerColumn) { }

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
        Object objectKey = source.get("objectNodeId");
        if (objectKey != null && full.nodes.containsKey(objectKey)) {
            node.put("dataObject", new LinkedHashMap<>(full.nodes.get(objectKey)));
            List<Map<String, Object>> ancestors = new ArrayList<>();
            Set<String> visited = new LinkedHashSet<>();
            visited.add(key);
            String parentKey = (String) source.get("parentNodeId");
            while (parentKey != null && visited.add(parentKey)) {
                Map<String, Object> parent = full.nodes.get(parentKey);
                if (parent == null || !"DATA_FIELD".equals(parent.get("type"))) break;
                Map<String, Object> ancestor = new LinkedHashMap<>(parent);
                ancestor.put("dataObject", node.get("dataObject"));
                ancestors.add(0, ancestor);
                parentKey = (String) parent.get("parentNodeId");
            }
            node.put("ancestorFields", ancestors);
        }
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
        for (RuleDataObject item : dataObjectMapper.selectList(new LambdaQueryWrapper<RuleDataObject>())) {
            addNode(graph, "DATA_OBJECT", item.getId(), item.getObjectCode(), item.getObjectLabel());
            addProjectEdge(graph, item.getProjectId(), nodeKey("DATA_OBJECT", item.getId()));
        }
        List<RuleDataObjectField> objectFields = dataObjectFieldMapper.selectList(new LambdaQueryWrapper<RuleDataObjectField>());
        for (RuleDataObjectField item : objectFields) {
            addNode(graph, "DATA_FIELD", item.getId(), item.getScriptName() != null ? item.getScriptName() : item.getVarCode(), item.getVarLabel());
            String objectKey = nodeKey("DATA_OBJECT", item.getObjectId());
            if (graph.nodes.containsKey(objectKey) && item.getId() != null) {
                graph.nodes.get(nodeKey("DATA_FIELD", item.getId())).put("objectNodeId", objectKey);
            }
        }
        for (RuleDataObjectField item : objectFields) {
            String fieldKey = nodeKey("DATA_FIELD", item.getId());
            String objectKey = nodeKey("DATA_OBJECT", item.getObjectId());
            if (graph.nodes.containsKey(objectKey) && graph.nodes.containsKey(fieldKey)) {
                String parentKey = nodeKey("DATA_FIELD", item.getParentFieldId());
                Map<String, Object> parent = graph.nodes.get(parentKey);
                if (parent == null || parentKey.equals(fieldKey) || !objectKey.equals(parent.get("objectNodeId"))) {
                    parentKey = objectKey;
                }
                graph.nodes.get(fieldKey).put("parentNodeId", parentKey);
                graph.nodes.get(parentKey).put("hasFieldChildren", true);
                addEdge(graph, parentKey, fieldKey, "包含字段");
            } else {
                addProjectEdge(graph, item.getProjectId(), fieldKey);
            }
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
