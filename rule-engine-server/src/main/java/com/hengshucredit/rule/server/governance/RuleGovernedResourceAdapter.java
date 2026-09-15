package com.hengshucredit.rule.server.governance;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.dto.RuleDraftSourceRequest;
import com.hengshucredit.rule.model.dto.RuleLifecycleActionRequest;
import com.hengshucredit.rule.model.dto.RuleDraftSaveRequest;
import com.hengshucredit.rule.model.dto.RulePreflightReport;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionContent;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.model.enums.RuleRevisionState;
import com.hengshucredit.rule.model.enums.RuleDraftSourceType;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.mapper.RuleDefinitionContentMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionInputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionOutputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import com.hengshucredit.rule.server.service.RuleLifecycleService;
import com.hengshucredit.rule.server.service.RuleDraftService;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;

public class RuleGovernedResourceAdapter
        extends AggregateEntityGovernedResourceAdapter<RuleDefinition> {

    private final RuleLifecycleService lifecycleService;
    private final RuleDefinitionContentMapper contentMapper;
    private final RuleDefinitionInputFieldMapper inputMapper;
    private final RuleDefinitionOutputFieldMapper outputMapper;
    private final RuleDraftService draftService;
    private final RuleDataObjectFieldMapper dataObjectFieldMapper;

    public RuleGovernedResourceAdapter(
            SimpleEntityGovernedResourceAdapter.EntityStore<RuleDefinition>
                    store,
            GovernanceSecretCodec secretCodec,
            RuleLifecycleService lifecycleService,
            RuleDraftService draftService,
            RuleDefinitionContentMapper contentMapper,
            RuleDefinitionInputFieldMapper inputMapper,
            RuleDefinitionOutputFieldMapper outputMapper) {
        this(store, secretCodec, lifecycleService, draftService,
                contentMapper, inputMapper, outputMapper, null);
    }

    public RuleGovernedResourceAdapter(
            SimpleEntityGovernedResourceAdapter.EntityStore<RuleDefinition>
                    store,
            GovernanceSecretCodec secretCodec,
            RuleLifecycleService lifecycleService,
            RuleDraftService draftService,
            RuleDefinitionContentMapper contentMapper,
            RuleDefinitionInputFieldMapper inputMapper,
            RuleDefinitionOutputFieldMapper outputMapper,
            RuleDataObjectFieldMapper dataObjectFieldMapper) {
        super(new SimpleEntityGovernedResourceAdapter<>(
                GovernanceResourceTypes.RULE,
                RuleDefinition.class,
                store,
                RuleDefinition::getId,
                RuleDefinition::setId,
                RuleDefinition::getStatus,
                RuleDefinition::setStatus,
                Set.of("ruleCode", "ruleName", "modelType"),
                Set.of(),
                secretCodec));
        this.lifecycleService = lifecycleService;
        this.draftService = draftService;
        this.contentMapper = contentMapper;
        this.inputMapper = inputMapper;
        this.outputMapper = outputMapper;
        this.dataObjectFieldMapper = dataObjectFieldMapper;
    }

    @Override
    public List<ResourceDependencyRef> collectDependencies(
            ResourceSnapshot draft) {
        List<ResourceDependencyRef> resolved = new ArrayList<>();
        for (ResourceDependencyRef dependency
                : super.collectDependencies(draft)) {
            resolved.add(resolveGovernedRoot(dependency));
        }
        return resolved;
    }

    @Override
    public List<GovernanceIssue> validate(ResourceSnapshot draft,
                                          String action) {
        List<GovernanceIssue> issues =
                new ArrayList<>(super.validate(draft));
        if ("DELETE".equals(action) || "DISABLE".equals(action)) {
            return issues;
        }
        Map<String, Object> snapshot = CanonicalJson.readMap(
                draft.snapshotJson());
        Long definitionId = longValue(snapshot.get("id"));
        if (definitionId == null) {
            return issues;
        }
        RuleRevision review = lifecycleService.listRevisions(definitionId)
                .stream()
                .filter(revision -> RuleRevisionState.REVIEW.name()
                        .equals(revision.getState()))
                .findFirst()
                .orElse(null);
        if (review == null) {
            return issues;
        }
        RulePreflightReport report = lifecycleService.preflightReport(
                definitionId, review.getId());
        if (report == null || !report.isValid()
                || !Objects.equals(review.getContentDigest(),
                report.getContentDigest())) {
            issues.add(GovernanceIssue.error(
                    "RULE_REVIEW_PREFLIGHT_CHANGED",
                    "REVIEW 内容或依赖已变化，审批将转为冲突并生成新的 DRAFT",
                    GovernanceResourceTypes.RULE, definitionId,
                    "$.revision"));
        }
        return issues;
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

    private ResourceDependencyRef resolveGovernedRoot(
            ResourceDependencyRef dependency) {
        if (dataObjectFieldMapper == null
                || !GovernanceResourceTypes.DATA_OBJECT.equals(
                dependency.targetResourceType())
                || !isFieldReferencePath(dependency.referencePath())) {
            return dependency;
        }
        RuleDataObjectField field = dataObjectFieldMapper.selectById(
                dependency.targetResourceId());
        if (field == null || field.getObjectId() == null) {
            return dependency;
        }
        return new ResourceDependencyRef(
                GovernanceResourceTypes.DATA_OBJECT,
                field.getObjectId(), GovernanceResourceTypes.DATA_OBJECT,
                dependency.referencePath(), dependency.relationType(),
                dependency.required());
    }

    private boolean isFieldReferencePath(String path) {
        return path != null
                && (path.endsWith(".refId") || path.endsWith(".varId"));
    }

    @Override
    protected void enrichSnapshot(Long resourceId,
                                  Map<String, Object> snapshot) {
        RuleDefinitionContent content = contentMapper.selectOne(
                new LambdaQueryWrapper<RuleDefinitionContent>()
                        .eq(RuleDefinitionContent::getDefinitionId,
                                resourceId)
                        .last("LIMIT 1"));
        snapshot.put("content", content);
        snapshot.put("inputFieldsJson", inputMapper.selectList(
                new LambdaQueryWrapper<RuleDefinitionInputField>()
                        .eq(RuleDefinitionInputField::getDefinitionId,
                                resourceId)
                        .orderByAsc(
                                RuleDefinitionInputField::getSortOrder)
                        .orderByAsc(RuleDefinitionInputField::getId)));
        snapshot.put("outputFieldsJson", outputMapper.selectList(
                new LambdaQueryWrapper<RuleDefinitionOutputField>()
                        .eq(RuleDefinitionOutputField::getDefinitionId,
                                resourceId)
                        .orderByAsc(
                                RuleDefinitionOutputField::getSortOrder)
                        .orderByAsc(RuleDefinitionOutputField::getId)));
    }

    @Override
    protected boolean isOwnershipReference(
            ResourceDependencyRef dependency) {
        String path = dependency.referencePath();
        return "$.content.definitionId".equals(path)
                || isCollectionOwnershipReference(
                dependency, "inputFieldsJson", "definitionId")
                || isCollectionOwnershipReference(
                dependency, "outputFieldsJson", "definitionId");
    }

    @Override
    protected void applyAggregate(Long resourceId,
                                  Map<String, Object> snapshot) {
        RuleDefinitionContent content = JSON.parseObject(
                JSON.toJSONString(snapshot.get("content")),
                RuleDefinitionContent.class);
        if (content != null) {
            RuleDefinitionContent current = contentMapper.selectOne(
                    new LambdaQueryWrapper<RuleDefinitionContent>()
                            .eq(RuleDefinitionContent::getDefinitionId,
                                    resourceId)
                            .last("LIMIT 1"));
            content.setDefinitionId(resourceId);
            if (current == null) {
                content.setId(null);
                contentMapper.insert(content);
            } else {
                content.setId(current.getId());
                contentMapper.updateById(content);
            }
        }
        inputMapper.delete(new LambdaQueryWrapper<
                        RuleDefinitionInputField>()
                .eq(RuleDefinitionInputField::getDefinitionId,
                        resourceId));
        outputMapper.delete(new LambdaQueryWrapper<
                        RuleDefinitionOutputField>()
                .eq(RuleDefinitionOutputField::getDefinitionId,
                        resourceId));
        List<RuleDefinitionInputField> inputs = JSON.parseArray(
                JSON.toJSONString(snapshot.get("inputFieldsJson")),
                RuleDefinitionInputField.class);
        if (inputs != null) {
            for (int index = 0; index < inputs.size(); index++) {
                RuleDefinitionInputField input = inputs.get(index);
                input.setId(null);
                input.setDefinitionId(resourceId);
                input.setSortOrder(index);
                inputMapper.insert(input);
            }
        }
        List<RuleDefinitionOutputField> outputs = JSON.parseArray(
                JSON.toJSONString(snapshot.get("outputFieldsJson")),
                RuleDefinitionOutputField.class);
        if (outputs != null) {
            for (int index = 0; index < outputs.size(); index++) {
                RuleDefinitionOutputField output = outputs.get(index);
                output.setId(null);
                output.setDefinitionId(resourceId);
                output.setSortOrder(index);
                outputMapper.insert(output);
            }
        }
    }

    @Override
    protected AppliedResource afterAggregateApplied(
            ApprovalApplyContext context, AppliedResource applied) {
        if ("DELETE".equals(context.action())
                || "DISABLE".equals(context.action())) {
            Long artifactId = offlinePublished(applied.resourceId());
            return new AppliedResource(applied.resourceId(),
                    applied.versionNo(), applied.effectiveStatus(),
                    artifactId);
        }
        RuleRevision published = approveAndPublish(
                applied.resourceId(), context.snapshot(),
                context.actor(), context.requestId());
        String effectiveStatus = published != null
                && RuleRevisionState.PUBLISHED.name()
                .equals(published.getState())
                ? "ACTIVE" : applied.effectiveStatus();
        return new AppliedResource(applied.resourceId(),
                applied.versionNo(), effectiveStatus,
                published == null ? null : published.getArtifactId());
    }

    @Override
    public void onApprovalTerminated(Long resourceId,
                                     ResourceSnapshot effectiveSnapshot,
                                     String actor,
                                     String comment,
                                     String terminalStatus) {
        RuleRevision review = lifecycleService.listRevisions(resourceId)
                .stream()
                .filter(revision -> RuleRevisionState.REVIEW.name()
                        .equals(revision.getState()))
                .findFirst()
                .orElse(null);
        if (review != null) {
            RuleLifecycleActionRequest action =
                    new RuleLifecycleActionRequest();
            action.setComment(comment == null || comment.isBlank()
                    ? "统一审批已终止：" + terminalStatus
                    : comment);
            RuleRevision rejected = lifecycleService.returnToDraft(
                    review.getId(), action);
            RuleDraftSourceRequest source = new RuleDraftSourceRequest();
            source.setSourceType(RuleDraftSourceType.REVISION);
            source.setSourceId(rejected.getId());
            lifecycleService.createDraftFromSource(resourceId, source);
            return;
        }
        restoreProjection(resourceId, effectiveSnapshot);
    }

    private RuleRevision approveAndPublish(
                                           Long definitionId,
                                           ResourceSnapshot snapshot,
                                           String actor, Long requestId) {
        RuleLifecycleActionRequest action =
                lifecycleAction(actor);
        List<RuleRevision> revisions =
                lifecycleService.listRevisions(definitionId);
        RuleRevision revision = requestId == null ? null : revisions.stream()
                .filter(item -> requestId.equals(item.getGovernanceRequestId()))
                .findFirst().orElse(null);
        if (revision == null) revision = latestTransient(revisions);
        if (revision == null) {
            RuleRevision base = latestStable(revisions);
            revision = lifecycleService.createDraft(
                    definitionId, base == null ? null : base.getId());
        }
        if (RuleRevisionState.DRAFT.name()
                .equals(revision.getState())) {
            revision = synchronizeDraft(
                    definitionId, revision, snapshot);
        }
        if (RuleRevisionState.DRAFT.name()
                .equals(revision.getState())) {
            revision = lifecycleService.submit(
                    revision.getId(), action);
        }
        if (RuleRevisionState.REVIEW.name()
                .equals(revision.getState())) {
            revision = lifecycleService.approve(
                    revision.getId(), action);
        }
        if (RuleRevisionState.APPROVED.name()
                .equals(revision.getState())) {
            revision = lifecycleService.publish(
                    revision.getId(), action);
        }
        return revision;
    }

    private RuleRevision synchronizeDraft(
            Long definitionId,
            RuleRevision revision,
            ResourceSnapshot snapshot) {
        if (draftService == null || snapshot == null) {
            return revision;
        }
        Map<String, Object> aggregate =
                com.hengshucredit.rule.server.artifact.CanonicalJson
                        .readMap(snapshot.snapshotJson());
        RuleDefinitionContent content = JSON.parseObject(
                JSON.toJSONString(aggregate.get("content")),
                RuleDefinitionContent.class);
        if (content == null || content.getModelJson() == null) {
            return revision;
        }
        RuleDraftSaveRequest save =
                new RuleDraftSaveRequest();
        save.setDefinitionId(definitionId);
        save.setRevisionId(revision.getId());
        save.setLockVersion(revision.getLockVersion() == null
                ? 0 : revision.getLockVersion());
        save.setModelJson(content.getModelJson());
        save.setOpenApiConfigJson(
                content.getOpenApiConfigJson());
        save.setUpdateOpenApiConfig(true);
        return draftService.save(save).getRevision();
    }

    private Long offlinePublished(Long definitionId) {
        RuleRevision offline = lifecycleService.offlinePublished(definitionId,
                lifecycleAction("统一生命周期审批"));
        return offline == null ? null : offline.getArtifactId();
    }

    private RuleRevision latestTransient(
            List<RuleRevision> revisions) {
        List<RuleRevision> candidates = revisions.stream()
                .filter(revision ->
                        RuleRevisionState.DRAFT.name()
                                .equals(revision.getState())
                                || RuleRevisionState.REVIEW.name()
                                .equals(revision.getState())
                                || RuleRevisionState.APPROVED.name()
                                .equals(revision.getState()))
                .toList();
        // Legacy approvals may predate governanceRequestId. Never pick a newer unrelated draft.
        List<RuleRevision> frozen = candidates.stream()
                .filter(item -> !RuleRevisionState.DRAFT.name().equals(item.getState())).toList();
        if (frozen.size() == 1) return frozen.get(0);
        if (candidates.size() > 1) throw new IllegalStateException("存在多个候选草稿，审批必须绑定明确的规则修订");
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private RuleRevision latestStable(
            List<RuleRevision> revisions) {
        return revisions.stream()
                .filter(revision ->
                        RuleRevisionState.APPROVED.name()
                                .equals(revision.getState())
                                || RuleRevisionState.PUBLISHED.name()
                                .equals(revision.getState())
                                || RuleRevisionState.OFFLINE.name()
                                .equals(revision.getState()))
                .findFirst()
                .orElse(null);
    }

    private RuleLifecycleActionRequest lifecycleAction(String actor) {
        RuleLifecycleActionRequest request =
                new RuleLifecycleActionRequest();
        request.setComment("统一生命周期审批通过：" + actor);
        request.setForcePublishReason(
                "统一审批已确认本次 Schema 与依赖影响");
        return request;
    }
}
