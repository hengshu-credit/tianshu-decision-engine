package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.dto.GovernanceDraftRequest;
import com.hengshucredit.rule.model.dto.GovernanceSubmitRequest;
import com.hengshucredit.rule.model.dto.RuleLifecycleActionRequest;
import com.hengshucredit.rule.model.entity.GovernanceApprovalRequest;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.governance.GovernanceApprovalService;
import com.hengshucredit.rule.server.governance.GovernanceResourceTypes;
import com.hengshucredit.rule.server.governance.GovernedResourceAdapterRegistry;
import com.hengshucredit.rule.server.governance.ResourceSnapshot;
import com.hengshucredit.rule.server.mapper.RuleRevisionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import com.hengshucredit.rule.model.dto.RuleDesignerPublishRequest;
import com.hengshucredit.rule.model.entity.RuleVersionBinding;
import com.hengshucredit.rule.model.entity.RuleDefinitionContent;
import jakarta.annotation.Resource;
import java.util.Objects;

/**
 * Bridges the rule designer revision workflow into the unified approval
 * workflow. The legacy revision submit and the unified request creation are
 * committed atomically.
 */
@Service
public class RuleGovernanceSubmissionService {

    private final RuleLifecycleService lifecycleService;
    private final GovernanceApprovalService approvalService;
    private final GovernedResourceAdapterRegistry adapterRegistry;
    private final ConsoleOperatorResolver operatorResolver;
    private final RuleRevisionMapper revisionMapper;
    @Resource private RuleVersionBindingService versionService;
    @Resource private RuleFieldAnalyzer fieldAnalyzer;
    @Resource private RuleDefinitionService definitionService;
    @Resource private RulePublicationValidator publicationValidator;

    @Transactional
    public RuleRevision submitDesigner(Long definitionId, RuleDesignerPublishRequest request) {
        if (request == null || request.getRevisionId() == null || request.getLockVersion() == null
                || !("NEW".equals(request.getPublishMode()) || "OVERWRITE".equals(request.getPublishMode())))
            throw new IllegalArgumentException("请选择有效草稿和发布方式");
        lifecycleService.lockDefinition(definitionId);
        RuleRevision revision = lifecycleService.requireEditableDraft(definitionId, request.getRevisionId());
        if (!Objects.equals(revision.getLockVersion(), request.getLockVersion()))
            throw new IllegalStateException("草稿已变化，请重新保存并提交");
        if ("OVERWRITE".equals(request.getPublishMode())) {
            RuleVersionBinding target = versionService.requireBinding(definitionId, request.getTargetVersionId());
            if (!Objects.equals(target.getGeneration(), request.getTargetGeneration()))
                throw new IllegalStateException("覆盖目标已变化，请重新选择版本");
        } else if (request.getTargetVersionId() != null || request.getTargetGeneration() != null) {
            throw new IllegalArgumentException("新增版本不能指定覆盖目标");
        }
        revision.setPublishMode(request.getPublishMode());
        revision.setTargetVersionId(request.getTargetVersionId()); revision.setTargetGeneration(request.getTargetGeneration());
        publicationValidator.validate(revision);
        lifecycleService.persistRevisionSnapshot(revision);
        RuleLifecycleActionRequest action = new RuleLifecycleActionRequest(); action.setComment(request.getComment());
        return submit(revision.getId(), action);
    }

    public RuleGovernanceSubmissionService(
            RuleLifecycleService lifecycleService,
            GovernanceApprovalService approvalService,
            GovernedResourceAdapterRegistry adapterRegistry,
            ConsoleOperatorResolver operatorResolver,
            RuleRevisionMapper revisionMapper) {
        this.lifecycleService = lifecycleService;
        this.approvalService = approvalService;
        this.adapterRegistry = adapterRegistry;
        this.operatorResolver = operatorResolver;
        this.revisionMapper = revisionMapper;
    }

    @Transactional
    public RuleRevision submit(
            Long revisionId,
            RuleLifecycleActionRequest action) {
        RuleRevision revision =
                lifecycleService.submit(revisionId, action);
        String actor = operatorResolver.resolve();
        ResourceSnapshot snapshot = adapterRegistry
                .require(GovernanceResourceTypes.RULE)
                .loadEffective(revision.getDefinitionId());
        Map<String, Object> value =
                CanonicalJson.readMap(snapshot.snapshotJson());
        // A different draft may own the compatibility projection; freeze the selected revision instead.
        if (fieldAnalyzer != null && definitionService != null) {
            var definition = definitionService.getById(revision.getDefinitionId());
            var fields = fieldAnalyzer.resolveFields(definition.getId(), revision.getModelJson(), definition.getModelType(), definition.getProjectId());
            RuleDefinitionContent content = new RuleDefinitionContent();
            content.setDefinitionId(revision.getDefinitionId()); content.setModelJson(revision.getModelJson());
            content.setCompiledScript(revision.getCompiledScript()); content.setCompiledType(revision.getCompiledType());
            content.setOpenApiConfigJson(revision.getOpenApiConfigJson()); content.setCompileStatus(1);
            value.put("content", content); value.put("inputFieldsJson", fields.getInputFields()); value.put("outputFieldsJson", fields.getOutputFields());
            snapshot = new ResourceSnapshot(CanonicalJson.write(value), snapshot.effectiveStatus(), snapshot.secretPayloadCiphertext(), snapshot.secretDigest());
        }

        GovernanceDraftRequest draft =
                new GovernanceDraftRequest();
        draft.setResourceType(GovernanceResourceTypes.RULE);
        draft.setResourceId(revision.getDefinitionId());
        draft.setProjectId(longValue(value.get("projectId")));
        draft.setAction("UPDATE");
        draft.setSnapshotJson(snapshot.snapshotJson());
        draft.setEffectiveStatus(snapshot.effectiveStatus());
        draft.setSecretPayloadCiphertext(
                snapshot.secretPayloadCiphertext());
        draft.setSecretDigest(snapshot.secretDigest());
        draft.setChangeSummary(comment(action,
                "提交规则修订 v" + revision.getRevisionNo()));

        GovernanceApprovalRequest request =
                approvalService.createDraft(draft, actor);
        GovernanceSubmitRequest submit =
                new GovernanceSubmitRequest();
        submit.setComment(comment(action, "提交规则审批"));
        request = approvalService.submit(
                request.getId(), submit, actor);
        revision.setGovernanceRequestId(request.getId());
        if (revisionMapper.updateById(revision) != 1) {
            throw new IllegalStateException(
                    "规则修订绑定统一审批申请失败");
        }
        return revision;
    }

    private String comment(RuleLifecycleActionRequest action,
                           String fallback) {
        return action == null || action.getComment() == null
                || action.getComment().isBlank()
                ? fallback : action.getComment().trim();
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
