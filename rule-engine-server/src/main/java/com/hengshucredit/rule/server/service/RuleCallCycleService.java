package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionContent;
import com.hengshucredit.rule.server.mapper.RuleDefinitionContentMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RuleCallCycleService {

    private static final Pattern SCRIPT_RULE_CALL_PATTERN =
            Pattern.compile("executeRule(?:Field)?\\s*\\(\\s*['\"]([^'\"]+)['\"]");

    @Resource
    private RuleDefinitionMapper definitionMapper;

    @Resource
    private RuleDefinitionContentMapper contentMapper;

    public String validateNoCycle(Long definitionId, String pendingModelJson) {
        if (definitionId == null) {
            return null;
        }
        RuleDefinition current = definitionMapper.selectById(definitionId);
        if (current == null) {
            return null;
        }
        List<RuleDefinition> definitions = definitionMapper.selectList(new LambdaQueryWrapper<RuleDefinition>());
        if (definitions == null || definitions.isEmpty()) {
            return null;
        }
        Map<Long, RuleDefinition> definitionById = new LinkedHashMap<>();
        Map<String, List<RuleDefinition>> definitionsByCode = new HashMap<>();
        for (RuleDefinition definition : definitions) {
            if (definition == null || definition.getId() == null) {
                continue;
            }
            definitionById.put(definition.getId(), definition);
            if (hasText(definition.getRuleCode())) {
                definitionsByCode.computeIfAbsent(definition.getRuleCode(), key -> new ArrayList<>()).add(definition);
            }
        }

        Map<Long, String> modelJsonById = loadModelJsonByDefinitionId();
        if (pendingModelJson != null) {
            modelJsonById.put(definitionId, pendingModelJson);
        }

        Map<Long, Set<Long>> graph = new LinkedHashMap<>();
        for (RuleDefinition definition : definitions) {
            if (definition == null || definition.getId() == null) {
                continue;
            }
            Set<Long> targets = new LinkedHashSet<>();
            List<RuleCallRef> refs = collectRuleCallRefs(modelJsonById.get(definition.getId()));
            for (RuleCallRef ref : refs) {
                Long targetId = ref.ruleId;
                if (targetId == null && hasText(ref.ruleCode)) {
                    targetId = resolveRuleIdByCode(ref.ruleCode, definition, definitionsByCode);
                }
                if (targetId == null || !definitionById.containsKey(targetId)) {
                    if (Objects.equals(definition.getId(), definitionId)) {
                        return "调用规则不存在: " + ref.displayName();
                    }
                    continue;
                }
                targets.add(targetId);
            }
            graph.put(definition.getId(), targets);
        }

        List<Long> cycle = findCycle(definitionId, graph);
        if (cycle == null || cycle.isEmpty()) {
            return null;
        }
        return "规则调用存在环路: " + formatCycle(cycle, definitionById);
    }

    static List<RuleCallRef> collectRuleCallRefs(String modelJson) {
        if (!hasText(modelJson)) {
            return Collections.emptyList();
        }
        try {
            Object parsed = JSON.parse(modelJson);
            LinkedHashSet<RuleCallRef> refs = new LinkedHashSet<>();
            collectRuleCallRefs(parsed, refs);
            return new ArrayList<>(refs);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private Map<Long, String> loadModelJsonByDefinitionId() {
        Map<Long, String> result = new HashMap<>();
        List<RuleDefinitionContent> contents = contentMapper.selectList(new LambdaQueryWrapper<RuleDefinitionContent>());
        if (contents == null) {
            return result;
        }
        for (RuleDefinitionContent content : contents) {
            if (content != null && content.getDefinitionId() != null) {
                result.put(content.getDefinitionId(), content.getModelJson());
            }
        }
        return result;
    }

    private static void collectRuleCallRefs(Object value, Set<RuleCallRef> refs) {
        if (value instanceof JSONObject) {
            JSONObject obj = (JSONObject) value;
            if ("rule-call".equals(obj.getString("type"))) {
                Long ruleId = obj.containsKey("ruleId") ? obj.getLong("ruleId") : null;
                String ruleCode = trimToNull(obj.getString("ruleCode"));
                if (ruleId != null || ruleCode != null) {
                    refs.add(new RuleCallRef(ruleId, ruleCode));
                }
            }
            for (Object child : obj.values()) {
                collectRuleCallRefs(child, refs);
            }
            return;
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.size(); i++) {
                collectRuleCallRefs(array.get(i), refs);
            }
            return;
        }
        if (value instanceof String) {
            Matcher matcher = SCRIPT_RULE_CALL_PATTERN.matcher((String) value);
            while (matcher.find()) {
                refs.add(new RuleCallRef(null, matcher.group(1)));
            }
        }
    }

    private static List<Long> findCycle(Long start, Map<Long, Set<Long>> graph) {
        Set<Long> visiting = new HashSet<>();
        Set<Long> visited = new HashSet<>();
        List<Long> stack = new ArrayList<>();
        return dfs(start, graph, visiting, visited, stack);
    }

    private static Long resolveRuleIdByCode(String ruleCode, RuleDefinition source,
                                            Map<String, List<RuleDefinition>> definitionsByCode) {
        List<RuleDefinition> candidates = definitionsByCode.get(ruleCode);
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        Long sourceProjectId = normalizeProjectId(source == null ? null : source.getProjectId());
        for (RuleDefinition candidate : candidates) {
            if (Objects.equals(normalizeProjectId(candidate.getProjectId()), sourceProjectId)) {
                return candidate.getId();
            }
        }
        if (sourceProjectId != null) {
            for (RuleDefinition candidate : candidates) {
                if (normalizeProjectId(candidate.getProjectId()) == null) {
                    return candidate.getId();
                }
            }
        }
        return candidates.size() == 1 ? candidates.get(0).getId() : null;
    }

    private static Long normalizeProjectId(Long projectId) {
        return projectId == null || projectId <= 0 ? null : projectId;
    }

    private static List<Long> dfs(Long node, Map<Long, Set<Long>> graph,
                                  Set<Long> visiting, Set<Long> visited, List<Long> stack) {
        if (visited.contains(node)) {
            return null;
        }
        if (visiting.contains(node)) {
            int index = stack.indexOf(node);
            if (index < 0) {
                index = 0;
            }
            List<Long> cycle = new ArrayList<>(stack.subList(index, stack.size()));
            cycle.add(node);
            return cycle;
        }
        visiting.add(node);
        stack.add(node);
        for (Long next : graph.getOrDefault(node, Collections.emptySet())) {
            List<Long> cycle = dfs(next, graph, visiting, visited, stack);
            if (cycle != null) {
                return cycle;
            }
        }
        stack.remove(stack.size() - 1);
        visiting.remove(node);
        visited.add(node);
        return null;
    }

    private static String formatCycle(List<Long> cycle, Map<Long, RuleDefinition> definitionById) {
        List<String> names = new ArrayList<>();
        for (Long id : cycle) {
            RuleDefinition definition = definitionById.get(id);
            if (definition == null) {
                names.add(String.valueOf(id));
                continue;
            }
            String name = hasText(definition.getRuleName()) ? definition.getRuleName() : definition.getRuleCode();
            String code = hasText(definition.getRuleCode()) ? definition.getRuleCode() : String.valueOf(id);
            names.add(name + "(" + code + ")");
        }
        return String.join(" -> ", names);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static class RuleCallRef {
        private final Long ruleId;
        private final String ruleCode;

        RuleCallRef(Long ruleId, String ruleCode) {
            this.ruleId = ruleId;
            this.ruleCode = trimToNull(ruleCode);
        }

        String displayName() {
            return ruleCode != null ? ruleCode : String.valueOf(ruleId);
        }

        Long getRuleId() {
            return ruleId;
        }

        String getRuleCode() {
            return ruleCode;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof RuleCallRef)) return false;
            RuleCallRef other = (RuleCallRef) obj;
            return Objects.equals(ruleId, other.ruleId) && Objects.equals(ruleCode, other.ruleCode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ruleId, ruleCode);
        }
    }
}
