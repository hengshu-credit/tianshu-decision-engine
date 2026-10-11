package com.hengshucredit.rule.server.artifact;

import com.hengshucredit.rule.model.dto.RuleRevisionFieldView;
import com.hengshucredit.rule.model.dto.RuleValidationIssue;
import com.hengshucredit.rule.model.entity.DecisionArtifact;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.server.mapper.DecisionArtifactMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionMapper;
import com.hengshucredit.rule.server.mapper.RuleRevisionMapper;
import com.hengshucredit.rule.server.service.RuleFieldAnalyzer;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;

@Service
public class RuleRevisionFieldViewService {
    @Resource private RuleDefinitionMapper definitionMapper;
    @Resource private RuleRevisionMapper revisionMapper;
    @Resource private DecisionArtifactMapper artifactMapper;
    @Resource private RuleFieldAnalyzer fieldAnalyzer;
    @Resource private PublishedRuleFieldSnapshotResolver snapshotResolver;
    @Resource private com.hengshucredit.rule.server.service.RuleReferenceNamespaceGuard namespaceGuard;

    public RuleRevisionFieldView getFields(Long definitionId, Long revisionId) {
        if (definitionId == null || revisionId == null || definitionId <= 0 || revisionId <= 0) {
            throw new IllegalArgumentException("规则 ID 和修订 ID 必须为正整数");
        }
        RuleDefinition definition = loadDefinition(definitionId);
        RuleRevision revision = loadRevision(revisionId);
        if (definition == null || revision == null || !definitionId.equals(revision.getDefinitionId())) {
            throw new IllegalArgumentException("规则修订不存在或不属于当前规则");
        }
        RuleRevisionFieldView view = new RuleRevisionFieldView();
        view.setDefinitionId(String.valueOf(definitionId));
        view.setRevisionId(String.valueOf(revisionId));
        view.setRevisionNo(revision.getRevisionNo());
        view.setRevisionState(revision.getState());
        view.setStatus("AVAILABLE");
        RuleFieldAnalyzer.ResolvedFields fields;
        if ("DRAFT".equals(revision.getState())) {
            view.setSource("DRAFT_ANALYSIS");
            view.setReferenceNamespace("CURRENT_ENVIRONMENT");
            view.setValidationSource("CURRENT_CONFIGURATION");
            view.setModelType(definition.getModelType());
            try {
                var proof = namespaceGuard.inspectNamespace(definition, "REVISION", revision.getId());
                if (proof.imported()) {
                    view.setReferenceNamespace("ARTIFACT_SOURCE");
                    return unavailable(view, "UNAVAILABLE", "DRAFT_REFERENCE_NAMESPACE_UNAVAILABLE",
                            "该草稿包含尚未迁移的来源环境引用，不能用当前同号字段解析。请先通过离线迁移完成明确资源绑定。");
                }
            } catch (com.hengshucredit.rule.server.common.RuleGovernanceException unavailableOrigin) {
                return unavailable(view, "UNAVAILABLE", "DRAFT_REFERENCE_NAMESPACE_UNAVAILABLE", unavailableOrigin.getMessage());
            }
            try {
                if (revision.getModelJson() == null || revision.getModelJson().isBlank()
                        || CanonicalJson.readMap(revision.getModelJson()) == null) {
                    throw new IllegalArgumentException("草稿尚未保存有效的规则 JSON 对象");
                }
                fields = analyzeDraft(definition, revision);
            } catch (IllegalArgumentException invalidDraft) {
                return unavailable(view, "INVALID", "DRAFT_FIELD_ANALYSIS_INVALID", "草稿字段解析失败：" + invalidDraft.getMessage());
            }
        } else {
            view.setSource("FROZEN_ARTIFACT");
            view.setReferenceNamespace("ARTIFACT_SOURCE");
            view.setValidationSource("FROZEN_REFERENCE_IDS");
            view.setArtifactId(revision.getArtifactId() == null ? null : String.valueOf(revision.getArtifactId()));
            if (revision.getArtifactId() == null) {
                return unavailable(view, "UNAVAILABLE", "FROZEN_REVISION_FIELD_SNAPSHOT_MISSING",
                        "该修订未保存字段快照制品，无法展示其冻结字段；当前工作区字段不能替代此修订。");
            }
            DecisionArtifact artifact = loadArtifact(revision.getArtifactId());
            fields = snapshotResolver.resolveRevision(revision, artifact);
            view.setModelType(fields.getSnapshotModelType());
            if (artifact != null) view.setArtifactDigest(artifact.getArtifactDigest());
        }
        view.setInputFields(rows(fields.getInputFields()));
        view.setOutputFields(rows(fields.getOutputFields()));
        view.setInputPropertySchemas(fields.getInputPropertySchemas());
        view.setOutputPropertySchemas(fields.getOutputPropertySchemas());
        view.setLocalOutputNames(fields.getLocalOutputNames());
        view.setDiagnostics(diagnostics(fields.getDiagnostics(), revisionId));
        if (fields.getDiagnostics().stream().anyMatch(issue -> "ERROR".equals(issue.getSeverity()))) {
            boolean missing = fields.getDiagnostics().stream().anyMatch(issue -> "FROZEN_REVISION_FIELD_SNAPSHOT_MISSING".equals(issue.getCode()));
            view.setStatus(missing ? "UNAVAILABLE" : "INVALID");
            if (missing) view.setSource("UNAVAILABLE");
        }
        return view;
    }

    private List<Map<String, Object>> rows(List<?> fields) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object field : fields) rows.add(exactIdentity(CanonicalJson.readMap(CanonicalJson.write(field))));
        return rows;
    }

    private List<Map<String, Object>> diagnostics(List<RuleValidationIssue> issues, Long revisionId) {
        List<Map<String, Object>> result = rows(issues);
        for (Map<String, Object> issue : result) {
            if (issue.get("revisionId") == null) issue.put("revisionId", String.valueOf(revisionId));
            stringifyDetailIds(issue.get("details"));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private void stringifyDetailIds(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> details = (Map<String, Object>) raw;
            details.replaceAll((key, item) -> {
                String name = key.toLowerCase(Locale.ROOT);
                if (name.endsWith("id") && item instanceof Number) return String.valueOf(item);
                if (name.endsWith("ids") && item instanceof List<?> ids) {
                    return ids.stream().map(id -> id instanceof Number ? String.valueOf(id) : id).toList();
                }
                stringifyDetailIds(item);
                return item;
            });
        } else if (value instanceof List<?> items) {
            items.forEach(this::stringifyDetailIds);
        }
    }

    private Map<String, Object> exactIdentity(Map<String, Object> values) {
        for (String key : Set.of("id", "definitionId", "varId", "resourceId", "revisionId")) {
            if (values.get(key) != null) values.put(key, String.valueOf(values.get(key)));
        }
        return values;
    }

    private RuleRevisionFieldView unavailable(RuleRevisionFieldView view, String status, String code, String message) {
        view.setStatus(status);
        if ("UNAVAILABLE".equals(status)) view.setSource("UNAVAILABLE");
        view.setDiagnostics(diagnostics(List.of(RuleValidationIssue.error(code, "$.ruleFields", "RULE",
                Long.valueOf(view.getDefinitionId()), message)), Long.valueOf(view.getRevisionId())));
        return view;
    }

    protected RuleDefinition loadDefinition(Long id) { return definitionMapper.selectById(id); }
    protected RuleRevision loadRevision(Long id) { return revisionMapper.selectById(id); }
    protected DecisionArtifact loadArtifact(Long id) { return artifactMapper.selectById(id); }
    protected RuleFieldAnalyzer.ResolvedFields analyzeDraft(RuleDefinition definition, RuleRevision revision) {
        return fieldAnalyzer.resolveFields(definition.getId(), revision.getModelJson(), definition.getModelType(), definition.getProjectId());
    }
}
