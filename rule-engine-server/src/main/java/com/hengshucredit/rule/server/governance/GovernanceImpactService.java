package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.service.RuleLineageService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Uses the effective lineage graph to block lifecycle actions that would leave
 * active downstream consumers pointing at a disabled or deleted resource.
 */
@Service
public class GovernanceImpactService {

    private final RuleLineageService lineageService;

    public GovernanceImpactService(RuleLineageService lineageService) {
        this.lineageService = lineageService;
    }

    public List<GovernanceIssue> analyze(
            String resourceType,
            Long resourceId,
            String action,
            ResourceSnapshot snapshot) {
        if (!destructive(action) || resourceId == null
                || resourceId <= 0) {
            return List.of();
        }
        List<GovernanceIssue> issues = new ArrayList<>();
        if (GovernanceResourceTypes.DATA_OBJECT.equals(
                normalize(resourceType))) {
            analyzeDataObject(resourceId, snapshot, issues);
            return issues;
        }
        String lineageType = lineageType(resourceType);
        if (lineageType != null) {
            analyzeNode(resourceType, resourceId, lineageType,
                    resourceId, "$", issues);
        }
        return issues;
    }

    private void analyzeDataObject(
            Long resourceId,
            ResourceSnapshot snapshot,
            List<GovernanceIssue> issues) {
        Object raw = CanonicalJson.readMap(snapshot.snapshotJson())
                .get("fields");
        if (!(raw instanceof List<?> fields)) {
            return;
        }
        for (int index = 0; index < fields.size(); index++) {
            if (!(fields.get(index) instanceof Map<?, ?> field)) {
                continue;
            }
            Long fieldId = longValue(field.get("id"));
            if (fieldId != null) {
                analyzeNode(GovernanceResourceTypes.DATA_OBJECT,
                        resourceId, "DATA_FIELD", fieldId,
                        "$.fields[" + index + "]", issues);
            }
        }
    }

    private void analyzeNode(
            String resourceType,
            Long resourceId,
            String lineageType,
            Long lineageId,
            String referencePath,
            List<GovernanceIssue> issues) {
        Map<String, Object> graph;
        try {
            graph = lineageService.graph(
                    lineageType, lineageId, "DOWNSTREAM", 1);
        } catch (IllegalArgumentException notInLegacyGraph) {
            return;
        }
        Object raw = graph.get("nodes");
        if (!(raw instanceof List<?> nodes) || nodes.size() <= 1) {
            return;
        }
        List<String> consumers = nodes.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .filter(node -> !sameNode(
                        lineageType, lineageId, node))
                // 整体下线/删除对象时，其内部字段随对象处理，不是外部消费者。
                .filter(node -> !(GovernanceResourceTypes.DATA_OBJECT.equals(resourceType)
                        && "DATA_FIELD".equals(node.get("type"))
                        && ("DATA_OBJECT:" + resourceId).equals(node.get("objectNodeId"))))
                .limit(5)
                .map(this::nodeLabel)
                .toList();
        if (!consumers.isEmpty()) {
            issues.add(GovernanceIssue.error(
                    "DOWNSTREAM_DEPENDENCY_ACTIVE",
                    "仍有生效的下游资源依赖当前内容："
                            + String.join("、", consumers),
                    resourceType, resourceId, referencePath));
        }
    }

    private boolean sameNode(String type,
                             Long id,
                             Map<?, ?> node) {
        return type.equalsIgnoreCase(
                String.valueOf(node.get("type")))
                && id.equals(longValue(node.containsKey("refId") ? node.get("refId") : node.get("id")));
    }

    private String nodeLabel(Map<?, ?> node) {
        Object label = node.get("label");
        Object code = node.get("code");
        Object type = node.get("type");
        return String.valueOf(label == null ? code : label)
                + "（" + type + "）";
    }

    private String lineageType(String resourceType) {
        return switch (normalize(resourceType)) {
            case GovernanceResourceTypes.PROJECT -> "PROJECT";
            case GovernanceResourceTypes.VARIABLE -> "VARIABLE";
            case GovernanceResourceTypes.MODEL -> "MODEL";
            case GovernanceResourceTypes.EXTERNAL_DATASOURCE ->
                    "DATASOURCE";
            case GovernanceResourceTypes.EXTERNAL_API -> "API";
            case GovernanceResourceTypes.DATABASE -> "DB";
            case GovernanceResourceTypes.RULE -> "RULE";
            case GovernanceResourceTypes.LIST_LIBRARY -> "LIST";
            default -> null;
        };
    }

    private boolean destructive(String action) {
        String normalized = normalize(action);
        return "DISABLE".equals(normalized)
                || "DELETE".equals(normalized);
    }

    private String normalize(String value) {
        return value == null ? ""
                : value.trim().toUpperCase(Locale.ROOT);
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
