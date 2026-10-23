package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.service.RuleLineageService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Protects configuration references from undeleted consuming resources.
 * Disabled consumers retain their references; deleted consumers no longer block changes.
 */
@Service
public class GovernanceImpactService {

    private final RuleLineageService lineageService;
    @jakarta.annotation.Resource private DataObjectUpdateReferenceGuard dataObjectUpdateGuard;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.hengshucredit.rule.server.mapper.GovernanceDependencySnapshotMapper dependencySnapshotMapper;

    public GovernanceImpactService(RuleLineageService lineageService) {
        this.lineageService = lineageService;
    }

    public List<GovernanceIssue> analyze(
            String resourceType,
            Long resourceId,
            String action,
            ResourceSnapshot snapshot) {
        if ("UPDATE".equals(normalize(action)) && GovernanceResourceTypes.DATA_OBJECT.equals(normalize(resourceType))
                && dataObjectUpdateGuard != null) {
            return dataObjectUpdateGuard.validate(resourceId, CanonicalJson.readMap(snapshot.snapshotJson()));
        }
        if (!destructive(action) || resourceId == null
                || resourceId <= 0) {
            return List.of();
        }
        List<GovernanceIssue> issues = new ArrayList<>();
        if (dependencySnapshotMapper != null) {
            List<String> consumers = dependencySnapshotMapper.activeConsumers(normalize(resourceType), resourceId);
            if (!consumers.isEmpty()) issues.add(GovernanceIssue.error("DOWNSTREAM_DRAFT_DEPENDENCY",
                    "仍有未发布草稿或待审批内容引用当前资源，请先删除下游内容：" + String.join("、", consumers),
                    resourceType, resourceId, "$"));
        }
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
        List<Long> fieldIds = fields.stream().filter(Map.class::isInstance)
                .map(value -> longValue(((Map<?, ?>) value).get("id")))
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, List<Map<String, Object>>> consumers = lineageService.directReferenceConsumers("DATA_FIELD", fieldIds);
        for (int index = 0; index < fields.size(); index++) {
            if (!(fields.get(index) instanceof Map<?, ?> field)) {
                continue;
            }
            Long fieldId = longValue(field.get("id"));
            if (fieldId != null) {
                analyzeConsumers(GovernanceResourceTypes.DATA_OBJECT,
                        resourceId, "DATA_FIELD", fieldId,
                        "$.fields[" + index + "]", consumers.getOrDefault(fieldId, List.of()), issues);
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
        List<Map<String, Object>> nodes = lineageService.directReferenceConsumers(lineageType, lineageId);
        analyzeConsumers(resourceType, resourceId, lineageType, lineageId, referencePath, nodes, issues);
    }

    private void analyzeConsumers(String resourceType, Long resourceId, String lineageType, Long lineageId,
                                  String referencePath, List<Map<String, Object>> nodes, List<GovernanceIssue> issues) {
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
                    "仍有未删除的资源引用当前内容："
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
            case GovernanceResourceTypes.SFTP -> "SFTP";
            case GovernanceResourceTypes.RULE -> "RULE";
            case GovernanceResourceTypes.FUNCTION -> "FUNCTION";
            case GovernanceResourceTypes.EXPERIMENT -> "EXPERIMENT";
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
