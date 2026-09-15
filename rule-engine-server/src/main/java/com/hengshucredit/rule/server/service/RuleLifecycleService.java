package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hengshucredit.rule.model.dto.RuleLifecycleActionRequest;
import com.hengshucredit.rule.model.dto.RulePreflightReport;
import com.hengshucredit.rule.model.dto.RulePushMessage;
import com.hengshucredit.rule.model.dto.RuleValidationIssue;
import com.hengshucredit.rule.model.dto.RuleDraftSaveRequest;
import com.hengshucredit.rule.model.dto.RuleDraftSaveResponse;
import com.hengshucredit.rule.model.dto.RuleDraftSourceRequest;
import com.hengshucredit.rule.model.entity.DecisionArtifact;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionContent;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionVersion;
import com.hengshucredit.rule.model.entity.RuleLifecycleEvent;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.model.entity.RulePublishOutbox;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.model.enums.RuleRevisionState;
import com.hengshucredit.rule.model.enums.RuleDraftSourceType;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.artifact.DecisionArtifactService;
import com.hengshucredit.rule.server.artifact.RulePreflightValidationService;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import com.hengshucredit.rule.server.common.RuleGovernanceException;
import com.hengshucredit.rule.server.governance.GovernanceResourceBootstrapService;
import com.hengshucredit.rule.server.governance.GovernanceResourceTypes;
import com.hengshucredit.rule.server.mapper.RuleDefinitionVersionMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionMapper;
import com.hengshucredit.rule.server.mapper.RuleLifecycleEventMapper;
import com.hengshucredit.rule.server.mapper.RulePublishedMapper;
import com.hengshucredit.rule.server.mapper.RulePublishOutboxMapper;
import com.hengshucredit.rule.server.mapper.RuleRevisionMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RuleLifecycleService {
    @Resource
    private RuleRevisionMapper revisionMapper;
    @Resource
    private RuleDefinitionMapper definitionMapper;
    @Resource
    private RuleLifecycleEventMapper eventMapper;
    @Resource
    private RulePublishedMapper publishedMapper;
    @Resource
    private RulePublishOutboxMapper outboxMapper;
    @Resource
    private RuleDefinitionVersionMapper versionMapper;
    @Resource
    private RuleVersionBindingService versionBindingService;
    @Resource private RulePublicationValidator publicationValidator;
    @Resource
    private RuleDefinitionService definitionService;
    @Resource
    private RuleProjectService projectService;
    @Resource
    private RulePreflightValidationService preflightService;
    @Resource
    private DecisionArtifactService artifactService;
    @Resource
    private ConsoleOperatorResolver operatorResolver;
    @Resource
    @Lazy
    private RuleDraftService ruleDraftService;
    @Resource
    @Lazy
    private GovernanceResourceBootstrapService
            governanceBootstrapService;

    @Transactional
    public RuleRevision ensureDraft(Long definitionId) {
        return createDraft(definitionId, null);
    }

    /** 只读取来源并复制内存快照，供纯编译和显式新增保存使用。 */
    public RuleRevision prepareDesignerDraft(Long definitionId, RuleDraftSourceType sourceType, Long sourceId) {
        if (loadDefinition(definitionId) == null) {
            throw governance(400, "DEFINITION_NOT_FOUND", "规则定义不存在");
        }
        if ((sourceType == null) != (sourceId == null)) {
            throw governance(400, "SOURCE_NOT_FOUND", "来源类型和来源 ID 必须同时提供");
        }
        RuleRevision draft = new RuleRevision();
        draft.setDefinitionId(definitionId);
        draft.setState(RuleRevisionState.DRAFT.name());
        draft.setLockVersion(0);
        draft.setSourceType(sourceType == null ? null : sourceType.name());
        draft.setSourceId(sourceId);
        if (sourceType == RuleDraftSourceType.REVISION) {
            copyRevisionSource(definitionId, sourceId, draft);
        } else if (sourceType == RuleDraftSourceType.VERSION) {
            copyVersionSource(definitionId, sourceId, draft);
        } else {
            RuleDefinitionContent content = loadContent(definitionId);
            if (content != null) {
                draft.setModelJson(content.getModelJson());
                draft.setOpenApiConfigJson(content.getOpenApiConfigJson());
            }
        }
        return draft;
    }

    void insertDesignerDraft(RuleRevision draft) {
        draft.setRevisionNo(nextRevisionNo(draft.getDefinitionId()));
        draft.setCreateBy(actor());
        draft.setCreateTime(LocalDateTime.now());
        draft.setUpdateBy(actor());
        draft.setUpdateTime(LocalDateTime.now());
        insertRevision(draft);
    }

    void recordDesignerSave(RuleRevision revision, String mode) {
        RuleLifecycleEvent saved = event(revision,
                "NEW".equals(mode) ? "CREATE_DRAFT" : "OVERWRITE_DRAFT",
                "NEW".equals(mode) ? null : "DRAFT", "DRAFT", null);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("sourceType", revision.getSourceType());
        details.put("sourceId", revision.getSourceId());
        details.put("baseRevisionId", revision.getBaseRevisionId());
        details.put("lockVersion", revision.getLockVersion());
        saved.setDetailsJson(JSON.toJSONString(details));
        insertEvent(saved);
    }

    @Transactional
    public void deleteDraft(Long definitionId, Long revisionId, Integer lockVersion) {
        if (lockVersion == null || lockVersion < 0) {
            throw governance(400, "DRAFT_SAVE_CONTRACT_INVALID", "删除草稿必须提供锁版本");
        }
        if (lockDefinition(definitionId) == null) {
            throw governance(400, "DEFINITION_NOT_FOUND", "规则定义不存在");
        }
        RuleRevision draft = requireEditableDraft(definitionId, revisionId);
        if (!lockVersion.equals(draft.getLockVersion())
                || !compareAndSetState(draft, RuleRevisionState.DRAFT.name(), RuleRevisionState.DELETED.name())) {
            throw governance(409, "DRAFT_LOCK_CONFLICT", "草稿已修改或提交，请刷新后重试");
        }
        draft.setState(RuleRevisionState.DELETED.name());
        insertEvent(event(draft, "DELETE_DRAFT", "DRAFT", "DELETED", null));
        clearDesignerProjectionIfUnused(definitionId);
    }

    @Transactional
    public RuleRevision createDraft(Long definitionId, Long baseRevisionId) {
        RuleDefinition definition = lockDefinition(definitionId);
        if (definition == null) {
            throw new IllegalArgumentException("规则定义不存在");
        }
        ensureGovernanceBaseline(definitionId);
        RuleDefinitionContent content = loadContent(definitionId);
        RuleRevision base = baseRevisionId == null
                ? findLatestRevision(definitionId, RuleRevisionState.APPROVED)
                : loadRevision(baseRevisionId);
        if (baseRevisionId == null && base == null) {
            base = findPublishedRevision(definitionId);
        }
        if (baseRevisionId != null) {
            validateDraftBase(definitionId, baseRevisionId, base);
        }
        RuleRevision draft = new RuleRevision();
        draft.setDefinitionId(definitionId);
        draft.setRevisionNo(nextRevisionNo(definitionId));
        draft.setState(RuleRevisionState.DRAFT.name());
        draft.setBaseRevisionId(base == null ? null : base.getId());
        draft.setBaseArtifactId(base == null ? null : base.getArtifactId());
        draft.setModelJson(base != null ? base.getModelJson() : content == null ? "{}" : content.getModelJson());
        draft.setCompiledScript(base != null ? base.getCompiledScript()
                : content == null ? null : content.getCompiledScript());
        draft.setCompiledType(base != null ? base.getCompiledType()
                : content == null ? null : content.getCompiledType());
        draft.setOpenApiConfigJson(base != null ? base.getOpenApiConfigJson()
                : content == null ? null : content.getOpenApiConfigJson());
        draft.setInputSchemaJson(base == null ? null : base.getInputSchemaJson());
        draft.setOutputSchemaJson(base == null ? null : base.getOutputSchemaJson());
        draft.setLockVersion(0);
        draft.setCreateBy(actor());
        draft.setCreateTime(LocalDateTime.now());
        draft.setUpdateBy(actor());
        draft.setUpdateTime(LocalDateTime.now());
        try {
            insertRevision(draft);
        } catch (DuplicateKeyException conflict) {
            throw governance(409, "DRAFT_LOCK_CONFLICT", "修订编号冲突，请重试创建");
        }
        RuleLifecycleEvent created =
                event(draft, "CREATE_DRAFT", null, "DRAFT", null);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("baseRevisionId", draft.getBaseRevisionId());
        created.setDetailsJson(JSON.toJSONString(details));
        insertEvent(created);
        return draft;
    }

    @Transactional
    public RuleDraftSaveResponse saveTemporaryDraft(Long definitionId, RuleDraftSourceRequest request) {
        if (request == null || request.getModelJson() == null || request.getModelJson().isBlank()) {
            throw governance(400, "DRAFT_SAVE_CONTRACT_INVALID", "暂存必须提供当前设计内容");
        }
        if (request.getSourceType() != null) return createDraftFromSource(definitionId, request);
        if (request.getSourceId() != null) throw governance(400, "SOURCE_NOT_FOUND", "暂存来源类型缺失");
        if (lockDefinition(definitionId) == null) throw governance(400, "DEFINITION_NOT_FOUND", "规则定义不存在");
        RuleRevision draft = createDraft(definitionId, null);
        RuleDraftSaveRequest saveRequest = new RuleDraftSaveRequest();
        saveRequest.setDefinitionId(definitionId);
        saveRequest.setRevisionId(draft.getId());
        saveRequest.setLockVersion(draft.getLockVersion());
        saveRequest.setModelJson(request.getModelJson());
        saveRequest.setOpenApiConfigJson(request.getOpenApiConfigJson());
        saveRequest.setUpdateOpenApiConfig(request.getUpdateOpenApiConfig());
        return saveDraft(saveRequest);
    }

    protected void clearDesignerProjectionIfUnused(Long definitionId) {
        if (revisionMapper.selectCount(new LambdaQueryWrapper<RuleRevision>()
                .eq(RuleRevision::getDefinitionId, definitionId).ne(RuleRevision::getState, "DELETED")) == 0
                && versionMapper.selectCount(new LambdaQueryWrapper<RuleDefinitionVersion>()
                .eq(RuleDefinitionVersion::getDefinitionId, definitionId)) == 0) {
            ruleDraftService.clearDesignerProjection(definitionId);
        }
    }

    @Transactional
    public RuleDraftSaveResponse createDraftFromSource(
            Long definitionId, RuleDraftSourceRequest request) {
        RuleDefinition definition = lockDefinition(definitionId);
        if (definition == null) {
            throw governance(400, "DEFINITION_NOT_FOUND", "规则定义不存在");
        }
        ensureGovernanceBaseline(definitionId);
        if (request == null || request.getSourceType() == null
                || request.getSourceId() == null) {
            throw governance(400, "SOURCE_NOT_FOUND", "来源不存在或不属于当前规则");
        }
        validateSourceTransientState(definitionId, request);

        RuleRevision draft = new RuleRevision();
        draft.setDefinitionId(definitionId);
        draft.setRevisionNo(nextRevisionNo(definitionId));
        draft.setState(RuleRevisionState.DRAFT.name());
        draft.setLockVersion(0);
        draft.setSourceType(request.getSourceType().name());
        draft.setSourceId(request.getSourceId());
        draft.setCreateBy(actor());
        draft.setCreateTime(LocalDateTime.now());
        draft.setUpdateBy(actor());
        draft.setUpdateTime(LocalDateTime.now());

        if (RuleDraftSourceType.REVISION == request.getSourceType()) {
            copyRevisionSource(definitionId, request.getSourceId(), draft);
        } else if (RuleDraftSourceType.VERSION == request.getSourceType()) {
            copyVersionSource(definitionId, request.getSourceId(), draft);
        } else {
            throw governance(400, "SOURCE_NOT_FOUND", "来源不存在或不属于当前规则");
        }

        if (request.getModelJson() != null) draft.setModelJson(request.getModelJson());

        try {
            insertRevision(draft);
        } catch (DuplicateKeyException conflict) {
            throw governance(409, "DRAFT_LOCK_CONFLICT", "修订编号冲突，请重试创建");
        }

        RuleDraftSaveRequest saveRequest = new RuleDraftSaveRequest();
        saveRequest.setDefinitionId(definitionId);
        saveRequest.setRevisionId(draft.getId());
        saveRequest.setLockVersion(0);
        saveRequest.setModelJson(request.getModelJson() == null ? draft.getModelJson() : request.getModelJson());
        saveRequest.setOpenApiConfigJson(Boolean.TRUE.equals(request.getUpdateOpenApiConfig())
                ? request.getOpenApiConfigJson() : draft.getOpenApiConfigJson());
        saveRequest.setUpdateOpenApiConfig(true);
        RuleDraftSaveResponse response = saveDraft(saveRequest);

        RuleLifecycleEvent created = event(draft, "CREATE_DRAFT", null,
                RuleRevisionState.DRAFT.name(), null);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("sourceType", request.getSourceType().name());
        details.put("sourceId", request.getSourceId());
        details.put("baseRevisionId", draft.getBaseRevisionId());
        created.setDetailsJson(JSON.toJSONString(details));
        insertEvent(created);
        return response;
    }

    @Transactional
    public RuleRevision submit(Long revisionId, RuleLifecycleActionRequest request) {
        RuleRevision revision = requireState(revisionId, RuleRevisionState.DRAFT);
        lockDefinition(revision.getDefinitionId());
        revision = requireState(revisionId, RuleRevisionState.DRAFT);
        RuleRevision pending = findPendingRevision(revision.getDefinitionId());
        if (pending != null) {
            throw governance(409, "REVIEW_ALREADY_EXISTS", "规则已有待审批修订，请等待审批结束");
        }
        RulePreflightReport report = preflight(revisionId);
        assertPreflightValid(report);
        applyPreflight(revision, report);
        revision.setSubmitBy(actor());
        revision.setSubmitTime(LocalDateTime.now());
        return transition(revision, RuleRevisionState.DRAFT, RuleRevisionState.REVIEW,
                "SUBMIT", comment(request));
    }

    @Transactional
    public RuleRevision returnToDraft(Long revisionId, RuleLifecycleActionRequest request) {
        String comment = comment(request);
        if (comment == null) {
            throw new IllegalArgumentException("驳回申请必须填写原因");
        }
        RuleRevision initial = loadRevision(revisionId);
        if (initial == null) throw new IllegalArgumentException("规则修订不存在");
        RuleDefinition definition = lockDefinition(initial.getDefinitionId());
        if (definition == null) throw new IllegalArgumentException("规则定义不存在");
        RuleRevision revision = requireState(revisionId, RuleRevisionState.REVIEW);
        RuleRevision rejected = transition(revision, RuleRevisionState.REVIEW,
                RuleRevisionState.REJECTED, "REJECT", comment);
        restoreEffectiveProjection(rejected);
        return rejected;
    }

    @Transactional
    public RuleRevision approve(Long revisionId, RuleLifecycleActionRequest request) {
        RuleRevision revision = requireState(revisionId, RuleRevisionState.REVIEW);
        RulePreflightReport report = preflight(revisionId);
        assertPreflightValid(report);
        if (revision.getContentDigest() == null
                || !revision.getContentDigest().equals(report.getContentDigest())) {
            throw new IllegalStateException("REVIEW 内容或依赖已变化，请退回 DRAFT 后重新提交");
        }
        String forceReason = request == null ? null : trim(request.getForcePublishReason());
        if (report.isBreakingSchemaChange() && forceReason == null) {
            throw new IllegalArgumentException("破坏性 Schema 变更必须填写批准原因");
        }
        if (forceReason != null) revision.setForcePublishReason(forceReason);
        applyPreflight(revision, report);
        persistRevisionSnapshot(revision);
        DecisionArtifact artifact = buildArtifact(revisionId, actor());
        revision.setArtifactId(artifact.getId());
        revision.setApproveBy(actor());
        revision.setApproveTime(LocalDateTime.now());
        return transition(revision, RuleRevisionState.REVIEW, RuleRevisionState.APPROVED,
                "APPROVE", comment(request), artifact.getArtifactDigest());
    }

    @Transactional
    public RuleRevision publish(Long revisionId, RuleLifecycleActionRequest request) {
        RuleRevision revision = requireState(revisionId, RuleRevisionState.APPROVED);
        lockDefinition(revision.getDefinitionId());
        revision = requireState(revisionId, RuleRevisionState.APPROVED);
        if (revision.getArtifactId() == null) {
            throw new IllegalStateException("APPROVED 修订缺少不可变制品");
        }
        if (publicationValidator != null) publicationValidator.validate(revision);
        RuleRevision prior = findPublishedRevision(revision.getDefinitionId());
        if (prior != null && !prior.getId().equals(revision.getId()) && !"OVERWRITE".equals(revision.getPublishMode())) {
            transition(prior, RuleRevisionState.PUBLISHED, RuleRevisionState.OFFLINE,
                    "AUTO_OFFLINE", "发布新修订自动下线旧修订");
        }
        RuleRevision published = transition(revision, RuleRevisionState.APPROVED,
                RuleRevisionState.PUBLISHED, "PUBLISH", comment(request));
        published.setPublishBy(actor());
        published.setPublishTime(LocalDateTime.now());
        persistRevisionSnapshot(published);
        activateArtifact(published, actor());
        return published;
    }

    @Transactional
    public RuleRevision offline(Long revisionId, RuleLifecycleActionRequest request) {
        RuleRevision revision = requireState(revisionId, RuleRevisionState.PUBLISHED);
        lockDefinition(revision.getDefinitionId());
        revision = requireState(revisionId, RuleRevisionState.PUBLISHED);
        RuleRevision current = findPublishedRevision(revision.getDefinitionId());
        if (current == null || !revisionId.equals(current.getId())) {
            throw new IllegalStateException("当前修订不是生产最新版，请刷新后从当前生产版本执行下线");
        }
        RuleRevision offline = transition(revision, RuleRevisionState.PUBLISHED,
                RuleRevisionState.OFFLINE, "OFFLINE", comment(request));
        offline.setOfflineBy(actor());
        offline.setOfflineTime(LocalDateTime.now());
        persistRevisionSnapshot(offline);
        deactivateArtifact(offline, actor());
        return offline;
    }

    public List<RuleLifecycleEvent> timeline(Long definitionId) {
        return eventMapper.selectList(new LambdaQueryWrapper<RuleLifecycleEvent>()
                .eq(RuleLifecycleEvent::getDefinitionId, definitionId)
                .orderByAsc(RuleLifecycleEvent::getCreateTime)
                .orderByAsc(RuleLifecycleEvent::getId));
    }

    public List<RuleRevision> listRevisions(Long definitionId) {
        return revisionMapper.selectList(new LambdaQueryWrapper<RuleRevision>()
                .eq(RuleRevision::getDefinitionId, definitionId)
                .ne(RuleRevision::getState, RuleRevisionState.DELETED.name())
                .orderByDesc(RuleRevision::getRevisionNo));
    }

    public RuleRevision getRevision(Long definitionId, Long revisionId) {
        RuleRevision revision = loadRevision(revisionId);
        if (revision == null || !definitionId.equals(revision.getDefinitionId())) {
            throw new IllegalArgumentException("规则修订不存在");
        }
        return revision;
    }

    public RuleRevision currentDraft(Long definitionId) {
        return findDraft(definitionId);
    }

    public RuleRevision requireEditableDraft(Long definitionId) {
        if (revisionMapper != null && revisionMapper.selectCount(
                new LambdaQueryWrapper<RuleRevision>()
                        .eq(RuleRevision::getDefinitionId, definitionId)
                        .eq(RuleRevision::getState, RuleRevisionState.DRAFT.name())) > 1) {
            throw governance(409, "DRAFT_ID_REQUIRED", "存在多份草稿，请指定要修改的草稿 ID");
        }
        RuleRevision draft = findDraft(definitionId);
        if (draft == null) {
            throw governance(409, "FROZEN_REVISION_WRITE_REJECTED",
                    "规则没有可编辑的 DRAFT 修订");
        }
        return requireEditableDraft(definitionId, draft.getId());
    }

    public RuleRevision requireEditableDraft(Long definitionId, Long revisionId) {
        RuleRevision revision = loadRevision(revisionId);
        if (revision == null) {
            throw governance(409, "FROZEN_REVISION_WRITE_REJECTED",
                    "指定修订不存在或已冻结");
        }
        if (!definitionId.equals(revision.getDefinitionId())) {
            throw governance(400, "REVISION_DEFINITION_MISMATCH",
                    "修订不属于指定规则");
        }
        if (!RuleRevisionState.DRAFT.name().equals(revision.getState())) {
            throw governance(409, "FROZEN_REVISION_WRITE_REJECTED",
                    "只能修改 DRAFT 状态的规则修订");
        }
        return revision;
    }

    public RulePreflightReport preflightReport(Long definitionId, Long revisionId) {
        getRevision(definitionId, revisionId);
        return preflight(revisionId);
    }

    @Transactional
    public RuleRevision publishApproved(Long definitionId, RuleLifecycleActionRequest request) {
        RuleRevision approved = findLatestRevision(definitionId, RuleRevisionState.APPROVED);
        if (approved == null) {
            throw governance(409, "FROZEN_REVISION_WRITE_REJECTED",
                    "规则没有可发布的 APPROVED 修订");
        }
        return publish(approved.getId(), request);
    }

    @Transactional
    public RuleRevision offlinePublished(Long definitionId, RuleLifecycleActionRequest request) {
        RuleRevision published = findPublishedRevision(definitionId);
        if (published == null) {
            throw governance(409, "FROZEN_REVISION_WRITE_REJECTED",
                    "规则没有 PUBLISHED 修订");
        }
        return offline(published.getId(), request);
    }

    @Transactional
    public RuleRevision activateImportedArtifact(Long artifactId, Long definitionId, String importActor) {
        DecisionArtifact artifact = artifactService.getById(artifactId);
        if (artifact == null) throw new IllegalArgumentException("导入制品不存在");
        DecisionArtifactService.RuntimeProjection projection =
                artifactService.loadRuntimeProjection(artifactId);
        RuleRevision draft = ensureDraft(definitionId);
        draft.setModelJson(projection.getModelJson());
        draft.setCompiledScript(projection.getCompiledScript());
        draft.setCompiledType("QLEXPRESS");
        draft.setOpenApiConfigJson(projection.getOpenApiConfigJson());
        draft.setInputSchemaJson(projection.getInputSchemaJson());
        draft.setOutputSchemaJson(projection.getOutputSchemaJson());
        draft.setContentDigest(artifact.getArtifactDigest());
        draft.setArtifactId(artifactId);
        draft.setApproveBy(importActor);
        draft.setApproveTime(LocalDateTime.now());
        draft.setForcePublishReason("跨环境导入已批准决策制品");
        if (!compareAndSetState(draft, RuleRevisionState.DRAFT.name(),
                RuleRevisionState.APPROVED.name())) {
            throw new IllegalStateException("目标规则草稿已被其他会话修改");
        }
        draft.setState(RuleRevisionState.APPROVED.name());
        persistRevisionSnapshot(draft);
        RuleLifecycleEvent imported = event(draft, "IMPORT_APPROVED_ARTIFACT",
                RuleRevisionState.DRAFT.name(), RuleRevisionState.APPROVED.name(),
                "导入不可变制品 " + artifact.getArtifactDigest());
        imported.setArtifactDigest(artifact.getArtifactDigest());
        insertEvent(imported);
        RuleLifecycleActionRequest request = new RuleLifecycleActionRequest();
        request.setComment("跨环境部署");
        return publish(draft.getId(), request);
    }

    private RuleRevision transition(RuleRevision revision, RuleRevisionState expected,
                                    RuleRevisionState target, String action, String comment) {
        return transition(revision, expected, target, action, comment, null);
    }

    private RuleRevision transition(RuleRevision revision, RuleRevisionState expected,
                                    RuleRevisionState target, String action, String comment,
                                    String artifactDigest) {
        if (!expected.canTransitionTo(target)) {
            throw new IllegalStateException("不允许的生命周期变更: " + expected + " -> " + target);
        }
        if (!compareAndSetState(revision, expected.name(), target.name())) {
            throw new IllegalStateException("规则修订已被其他会话修改，请刷新后重试");
        }
        revision.setState(target.name());
        revision.setUpdateBy(actor());
        revision.setUpdateTime(LocalDateTime.now());
        if (target == RuleRevisionState.OFFLINE) {
            revision.setOfflineBy(actor());
            revision.setOfflineTime(LocalDateTime.now());
        }
        persistRevisionSnapshot(revision);
        RuleLifecycleEvent event = event(revision, action, expected.name(), target.name(), comment);
        event.setArtifactDigest(artifactDigest);
        insertEvent(event);
        return revision;
    }

    private void applyPreflight(RuleRevision revision, RulePreflightReport report) {
        revision.setCompiledScript(report.getCompiledScript());
        revision.setCompiledType(report.getCompiledType());
        revision.setInputSchemaJson(report.getInputSchemaJson());
        revision.setOutputSchemaJson(report.getOutputSchemaJson());
        revision.setContentDigest(report.getContentDigest());
        revision.setValidationReportDigest(Sha256Digests.text(CanonicalJson.write(report)));
    }

    private void assertPreflightValid(RulePreflightReport report) {
        if (!report.isValid()) {
            throw new IllegalStateException("发布前验证未通过: "
                    + (report.getErrors().isEmpty() ? "未知错误" : report.getErrors().get(0).getMessage()));
        }
    }

    private RuleRevision requireState(Long revisionId, RuleRevisionState state) {
        RuleRevision revision = loadRevision(revisionId);
        if (revision == null) throw new IllegalArgumentException("规则修订不存在");
        if (!state.name().equals(revision.getState())) {
            throw new IllegalStateException("当前操作要求修订状态为 " + state
                    + "，实际为 " + revision.getState());
        }
        return revision;
    }

    private RuleLifecycleEvent event(RuleRevision revision, String action, String from,
                                     String to, String comment) {
        RuleLifecycleEvent event = new RuleLifecycleEvent();
        event.setDefinitionId(revision.getDefinitionId());
        event.setRevisionId(revision.getId());
        event.setAction(action);
        event.setFromState(from);
        event.setToState(to);
        event.setActor(actor());
        event.setComment(comment);
        event.setContentDigest(revision.getContentDigest());
        event.setValidationReportDigest(revision.getValidationReportDigest());
        event.setRequestSource("CONSOLE");
        event.setCreateTime(LocalDateTime.now());
        return event;
    }

    private String comment(RuleLifecycleActionRequest request) {
        return request == null ? null : trim(request.getComment());
    }

    private void copyRevisionSource(Long definitionId, Long sourceId,
                                    RuleRevision draft) {
        RuleRevision source = loadRevision(sourceId);
        if (source == null || !definitionId.equals(source.getDefinitionId())) {
            throw governance(400, "SOURCE_NOT_FOUND", "来源不存在或不属于当前规则");
        }
        if (RuleRevisionState.REVIEW.name().equals(source.getState())) {
            throw governance(409, "SOURCE_REVIEW_REQUIRES_RETURN",
                    "REVIEW 修订需先退回 DRAFT");
        }
        boolean rejected = RuleRevisionState.REJECTED.name()
                .equals(source.getState());
        if (!rejected
                && !RuleRevisionState.DRAFT.name().equals(source.getState())
                && !RuleRevisionState.APPROVED.name().equals(source.getState())
                && !RuleRevisionState.PUBLISHED.name().equals(source.getState())
                && !RuleRevisionState.OFFLINE.name().equals(source.getState())) {
            throw governance(400, "SOURCE_NOT_FOUND", "来源不存在或不属于当前规则");
        }
        boolean draftSource = RuleRevisionState.DRAFT.name().equals(source.getState());
        draft.setBaseRevisionId(rejected || draftSource
                ? source.getBaseRevisionId() : source.getId());
        draft.setBaseArtifactId(rejected || draftSource
                ? source.getBaseArtifactId() : source.getArtifactId());
        draft.setModelJson(source.getModelJson());
        draft.setCompiledScript(source.getCompiledScript());
        draft.setCompiledType(source.getCompiledType());
        draft.setOpenApiConfigJson(source.getOpenApiConfigJson());
        draft.setInputSchemaJson(source.getInputSchemaJson());
        draft.setOutputSchemaJson(source.getOutputSchemaJson());
    }

    private void validateSourceTransientState(Long definitionId,
                                              RuleDraftSourceRequest request) {
        if (RuleDraftSourceType.REVISION != request.getSourceType()) {
            return;
        }
        RuleRevision source = loadRevision(request.getSourceId());
        if (source == null || !definitionId.equals(source.getDefinitionId())) {
            return;
        }
        if (RuleRevisionState.REVIEW.name().equals(source.getState())) {
            throw governance(409, "SOURCE_REVIEW_REQUIRES_RETURN",
                    "REVIEW 修订需先退回 DRAFT");
        }
    }

    private void copyVersionSource(Long definitionId, Long sourceId,
                                   RuleRevision draft) {
        RuleDefinitionVersion source = loadVersion(definitionId, sourceId);
        if (source == null) {
            throw governance(400, "SOURCE_NOT_FOUND", "来源不存在或不属于当前规则");
        }
        draft.setBaseRevisionId(null);
        draft.setBaseArtifactId(null);
        draft.setModelJson(source.getModelJson());
        draft.setCompiledScript(source.getCompiledScript());
        draft.setCompiledType(source.getCompiledType());
        draft.setOpenApiConfigJson(source.getOpenApiConfigJson());
    }

    private String trim(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private void validateDraftBase(Long definitionId, Long baseRevisionId,
                                   RuleRevision base) {
        if (base == null || !definitionId.equals(base.getDefinitionId())) {
            throw governance(400, "BASE_REVISION_INVALID",
                    "基线修订不存在或不属于当前规则");
        }
        if (!RuleRevisionState.APPROVED.name().equals(base.getState())
                && !RuleRevisionState.PUBLISHED.name().equals(base.getState())
                && !RuleRevisionState.OFFLINE.name().equals(base.getState())) {
            throw governance(400, "BASE_REVISION_INVALID",
                    "基线修订必须为 APPROVED、PUBLISHED 或 OFFLINE");
        }
        if (!baseRevisionId.equals(base.getId())) {
            throw governance(400, "BASE_REVISION_INVALID",
                    "基线修订身份不匹配");
        }
    }

    private RuleGovernanceException governance(int httpStatus, String code,
                                               String message) {
        return new RuleGovernanceException(httpStatus, code, message,
                Collections.singletonList(
                        RuleValidationIssue.error(code, "$", message)));
    }

    protected String actor() {
        return operatorResolver.resolve();
    }

    protected RuleDefinition loadDefinition(Long definitionId) {
        return definitionService.getById(definitionId);
    }

    protected RuleDefinition lockDefinition(Long definitionId) {
        return definitionMapper.selectOne(
                new LambdaQueryWrapper<RuleDefinition>()
                        .eq(RuleDefinition::getId, definitionId)
                        .last("FOR UPDATE"));
    }

    protected RuleDefinitionContent loadContent(Long definitionId) {
        return definitionService.getContent(definitionId);
    }

    protected RuleRevision loadRevision(Long revisionId) {
        return revisionMapper.selectById(revisionId);
    }

    protected RuleDefinitionVersion loadVersion(
            Long definitionId, Long versionId) {
        return definitionService.getVersionById(definitionId, versionId);
    }

    protected RuleDraftSaveResponse saveDraft(
            RuleDraftSaveRequest request) {
        return ruleDraftService.save(request);
    }

    protected RuleRevision findDraft(Long definitionId) {
        return revisionMapper.selectOne(new LambdaQueryWrapper<RuleRevision>()
                .eq(RuleRevision::getDefinitionId, definitionId)
                .eq(RuleRevision::getState, RuleRevisionState.DRAFT.name())
                .orderByDesc(RuleRevision::getRevisionNo).last("LIMIT 1"));
    }

    protected RuleRevision findPublishedRevision(Long definitionId) {
        if (publishedMapper != null) {
            RulePublished active = publishedMapper.selectOne(new LambdaQueryWrapper<RulePublished>()
                    .eq(RulePublished::getDefinitionId, definitionId).eq(RulePublished::getStatus, 1).last("LIMIT 1"));
            if (active != null && active.getRevisionId() != null) return loadRevision(active.getRevisionId());
        }
        return revisionMapper.selectOne(new LambdaQueryWrapper<RuleRevision>()
                .eq(RuleRevision::getDefinitionId, definitionId)
                .eq(RuleRevision::getState, RuleRevisionState.PUBLISHED.name())
                .orderByDesc(RuleRevision::getRevisionNo).last("LIMIT 1"));
    }

    protected RuleRevision findPendingRevision(Long definitionId) {
        if (revisionMapper == null) return null;
        return revisionMapper.selectOne(new LambdaQueryWrapper<RuleRevision>()
                .eq(RuleRevision::getDefinitionId, definitionId)
                .eq(RuleRevision::getState, RuleRevisionState.REVIEW.name())
                .orderByDesc(RuleRevision::getRevisionNo).last("LIMIT 1 FOR UPDATE"));
    }

    protected RuleRevision findLatestRevision(Long definitionId, RuleRevisionState state) {
        return revisionMapper.selectOne(new LambdaQueryWrapper<RuleRevision>()
                .eq(RuleRevision::getDefinitionId, definitionId)
                .eq(RuleRevision::getState, state.name())
                .orderByDesc(RuleRevision::getRevisionNo).last("LIMIT 1"));
    }

    protected RuleRevision findLatestAnyRevision(Long definitionId) {
        if (revisionMapper == null) return null;
        return revisionMapper.selectOne(new LambdaQueryWrapper<RuleRevision>()
                .eq(RuleRevision::getDefinitionId, definitionId)
                .orderByDesc(RuleRevision::getRevisionNo).last("LIMIT 1"));
    }

    protected int nextRevisionNo(Long definitionId) {
        RuleRevision latest = revisionMapper.selectOne(new LambdaQueryWrapper<RuleRevision>()
                .eq(RuleRevision::getDefinitionId, definitionId)
                .orderByDesc(RuleRevision::getRevisionNo).last("LIMIT 1"));
        return latest == null || latest.getRevisionNo() == null ? 1 : latest.getRevisionNo() + 1;
    }

    protected void insertRevision(RuleRevision revision) {
        if (revisionMapper.insert(revision) != 1) {
            throw new IllegalStateException("创建规则草稿失败");
        }
    }

    protected void persistRevisionSnapshot(RuleRevision revision) {
        revisionMapper.updateById(revision);
    }

    protected boolean compareAndSetState(RuleRevision revision, String expected, String target) {
        int lockVersion = revision.getLockVersion() == null ? 0 : revision.getLockVersion();
        int updated = revisionMapper.update(null, new LambdaUpdateWrapper<RuleRevision>()
                .eq(RuleRevision::getId, revision.getId())
                .eq(RuleRevision::getState, expected)
                .eq(RuleRevision::getLockVersion, lockVersion)
                .set(RuleRevision::getState, target)
                .set(RuleRevision::getLockVersion, lockVersion + 1)
                .set(RuleRevision::getUpdateBy, actor())
                .set(RuleRevision::getUpdateTime, LocalDateTime.now()));
        if (updated == 1) revision.setLockVersion(lockVersion + 1);
        return updated == 1;
    }

    protected RulePreflightReport preflight(Long revisionId) {
        return preflightService.validate(revisionId);
    }

    protected DecisionArtifact buildArtifact(Long revisionId, String actor) {
        return artifactService.buildApprovedArtifact(revisionId, actor);
    }

    protected void activateArtifact(RuleRevision revision, String actor) {
        RuleDefinition definition = loadDefinition(revision.getDefinitionId());
        DecisionArtifact artifact = artifactService.getById(revision.getArtifactId());
        if (definition == null || artifact == null) {
            throw new IllegalStateException("发布激活所需的规则或制品不存在");
        }
        DecisionArtifactService.RuntimeProjection projection =
                artifactService.loadRuntimeProjection(artifact.getId());
        RuleDefinitionVersion version = new RuleDefinitionVersion();
        version.setModelJson(projection.getModelJson());
        version.setCompiledScript(projection.getCompiledScript());
        version.setCompiledType(revision.getCompiledType());
        version.setArtifactDigest(artifact.getArtifactDigest());
        version.setOpenApiConfigJson(projection.getOpenApiConfigJson());
        version.setChangeLog(revision.getForcePublishReason());
        version.setPublishBy(actor);
        version.setPublishTime(LocalDateTime.now());
        com.hengshucredit.rule.model.entity.RuleVersionBinding binding = versionBindingService.activate(revision, version);
        boolean latest = binding.getVersionNo() == versionBindingService.latestBusinessVersion(definition.getId());
        String projectCode = definition.getProjectCode();
        if ((projectCode == null || projectCode.isBlank()) && definition.getProjectId() != null) {
            RuleProject project = projectService.getById(definition.getProjectId());
            projectCode = project == null ? null : project.getProjectCode();
        }
        RulePublished published = publishedMapper.selectOne(new LambdaQueryWrapper<RulePublished>()
                .eq(RulePublished::getDefinitionId, definition.getId()).last("LIMIT 1"));
        boolean insert = published == null;
        if (insert) published = new RulePublished();
        if (latest) {
            published.setRuleCode(definition.getRuleCode());
            published.setDefinitionId(definition.getId());
            published.setRevisionId(revision.getId());
            published.setArtifactId(artifact.getId());
            published.setArtifactDigest(artifact.getArtifactDigest());
            published.setProjectCode(projectCode);
            published.setVersion(binding.getVersionNo());
            published.setModelType(definition.getModelType());
            published.setCompiledScript(projection.getCompiledScript());
            published.setCompiledType(revision.getCompiledType());
            published.setModelJson(projection.getModelJson());
            published.setStatus(1);
            published.setPublishBy(actor);
            published.setPublishTime(LocalDateTime.now());
            published.setOfflineTime(null);
            if (insert) publishedMapper.insert(published); else publishedMapper.updateById(published);
            publishedMapper.updateOpenApiConfigByDefinitionId(definition.getId(),
                    projection.getOpenApiConfigJson());

            definition.setPublishedVersion(binding.getVersionNo());
            definition.setStatus(1);
            definitionService.updateById(definition);
        }

        RulePushMessage message = pushMessage(definition, revision, artifact, projection,
                projectCode, latest ? "PUBLISH" : "VERSION_UPDATE");
        message.setVersion(binding.getVersionNo());
        message.setDefinitionId(definition.getId());
        message.setVersionBindingId(binding.getId());
        message.setBindingGeneration(binding.getGeneration());
        insertOutbox(definition, revision, artifact, message);
    }

    protected void deactivateArtifact(RuleRevision revision, String actor) {
        RuleDefinition definition = loadDefinition(revision.getDefinitionId());
        RulePublished published = publishedMapper.selectOne(new LambdaQueryWrapper<RulePublished>()
                .eq(RulePublished::getDefinitionId, revision.getDefinitionId()).last("LIMIT 1"));
        if (published != null && revision.getId().equals(published.getRevisionId())) {
            published.setStatus(0);
            published.setOfflineTime(LocalDateTime.now());
            publishedMapper.updateById(published);
        }
        if (definition != null) {
            definition.setStatus(2);
            definitionService.updateById(definition);
            RulePushMessage message = new RulePushMessage();
            message.setRuleCode(definition.getRuleCode());
            message.setDefinitionId(definition.getId());
            message.setRevisionId(revision.getId());
            message.setArtifactDigest(published == null ? null : published.getArtifactDigest());
            message.setProjectCode(published == null ? definition.getProjectCode() : published.getProjectCode());
            message.setAction("UNPUBLISH");
            message.setPublishTime(System.currentTimeMillis());
            insertOutbox(definition, revision, null, message);
        }
    }

    protected void restoreEffectiveProjection(RuleRevision rejected) {
        RuleRevision effective =
                findPublishedRevision(rejected.getDefinitionId());
        if (effective == null && rejected.getBaseRevisionId() != null) {
            effective = loadRevision(rejected.getBaseRevisionId());
        }
        if (effective != null) {
            ruleDraftService.restoreEffectiveProjection(effective);
        }
    }

    protected void ensureGovernanceBaseline(Long definitionId) {
        if (governanceBootstrapService != null) {
            governanceBootstrapService.ensure(
                    GovernanceResourceTypes.RULE, definitionId);
        }
    }

    private RulePushMessage pushMessage(RuleDefinition definition, RuleRevision revision,
                                        DecisionArtifact artifact,
                                        DecisionArtifactService.RuntimeProjection projection,
                                        String projectCode, String action) {
        RulePushMessage message = new RulePushMessage();
        message.setRuleCode(definition.getRuleCode());
        message.setVersion(revision.getRevisionNo());
        message.setRevisionId(revision.getId());
        message.setArtifactDigest(artifact.getArtifactDigest());
        message.setModelType(definition.getModelType());
        message.setCompiledScript(projection.getCompiledScript());
        message.setCompiledType(revision.getCompiledType());
        message.setModelJson(projection.getModelJson());
        List<RuleDefinitionOutputField> fields = definitionService.listOutputFields(definition.getId());
        if (fields != null) {
            message.setOutputScriptNames(fields.stream().map(RuleDefinitionOutputField::getScriptName)
                    .filter(name -> name != null && !name.isBlank()).collect(Collectors.toList()));
        }
        message.setProjectCode(projectCode);
        message.setPublishTime(System.currentTimeMillis());
        message.setAction(action);
        return message;
    }

    private void insertOutbox(RuleDefinition definition, RuleRevision revision,
                              DecisionArtifact artifact, RulePushMessage message) {
        RulePublishOutbox outbox = new RulePublishOutbox();
        outbox.setOperationId(UUID.randomUUID().toString());
        outbox.setDefinitionId(definition.getId());
        outbox.setRevisionId(revision.getId());
        outbox.setArtifactId(artifact == null ? revision.getArtifactId() : artifact.getId());
        outbox.setMessageJson(JSON.toJSONString(message));
        outbox.setDeliveryStatus("PENDING");
        outbox.setRetryCount(0);
        outbox.setCreateTime(LocalDateTime.now());
        outbox.setUpdateTime(LocalDateTime.now());
        outboxMapper.insert(outbox);
    }

    protected void insertEvent(RuleLifecycleEvent event) {
        eventMapper.insert(event);
    }
}
