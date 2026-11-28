package com.hengshucredit.rule.server.derived;

import java.util.Collections;
import java.util.List;

/** Identifies the execution project and the set of root definitions included in a history query. */
record HistoryScope(String type, Long projectId, List<Long> ruleIds, String excludedTrace, Long credentialId) {
    HistoryScope(String type, Long projectId, List<Long> ruleIds, String excludedTrace) {
        this(type, projectId, ruleIds, excludedTrace, null);
    }
    HistoryScope(String type, Long projectId, List<Long> ruleIds) { this(type, projectId, ruleIds, null); }
    HistoryScope {
        if (!"GLOBAL".equals(type)) {
            if (credentialId != null) projectId = 0L;
            DerivedVariableConfig.require(credentialId != null || projectId != null && projectId > 0, "当前项目范围需要明确执行项目");
        } else {
            projectId = null;
        }
        ruleIds = switch (type) {
            case "RULE", "SELECTED_RULES" -> {
                DerivedVariableConfig.require(!ruleIds.isEmpty(), "规则统计范围需要根规则标识");
                yield ruleIds.stream().distinct().sorted().toList();
            }
            case "PROJECT", "GLOBAL" -> List.of();
            default -> throw new IllegalArgumentException("历史统计范围无效");
        };
    }

    static HistoryScope current(String type, Long projectId, Long rootRuleId) {
        DerivedVariableConfig.require(!"SELECTED_RULES".equals(type), "请提供所选统计规则");
        return new HistoryScope(type, projectId, rootRuleId == null ? List.of() : List.of(rootRuleId));
    }

    boolean filtersRules() {
        return !ruleIds.isEmpty();
    }

    void excludeCurrent(StringBuilder sql, List<Object> parameters, String traceColumn) {
        if (excludedTrace != null) {
            sql.append(" AND (").append(traceColumn).append(" IS NULL OR ").append(traceColumn).append(" <> ?)");
            parameters.add(excludedTrace);
        }
    }

    void appendRuleFilter(StringBuilder sql, List<Object> parameters, String column) {
        if (!filtersRules()) return;
        sql.append(" AND ").append(column);
        if (ruleIds.size() == 1) sql.append(" = ?");
        else sql.append(" IN (").append(String.join(",", Collections.nCopies(ruleIds.size(), "?"))).append(')');
        parameters.addAll(ruleIds);
    }
}
