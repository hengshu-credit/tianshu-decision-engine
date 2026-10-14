package com.hengshucredit.rule.server.governance;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hengshucredit.rule.model.dto.GovernanceApprovalQuery;
import com.hengshucredit.rule.model.dto.GovernanceDraftRequest;
import com.hengshucredit.rule.model.dto.GovernanceReviewRequest;
import com.hengshucredit.rule.model.dto.GovernanceSubmitRequest;
import com.hengshucredit.rule.model.entity.GovernanceApprovalEvent;
import com.hengshucredit.rule.model.entity.GovernanceApprovalRequest;
import com.hengshucredit.rule.model.entity.GovernedResource;
import com.hengshucredit.rule.model.entity.GovernedResourceVersion;
import com.hengshucredit.rule.model.enums.GovernanceAction;
import com.hengshucredit.rule.model.enums.GovernanceRequestStatus;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.mapper.GovernanceApprovalEventMapper;
import com.hengshucredit.rule.server.mapper.GovernanceApprovalRequestMapper;
import com.hengshucredit.rule.server.mapper.GovernedResourceMapper;
import com.hengshucredit.rule.server.mapper.GovernedResourceVersionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class GovernanceApprovalService {
    @Autowired(required = false)
    private GovernanceDependencyDisplayService dependencyDisplay;

    @Autowired
    private com.hengshucredit.rule.server.security.ConsoleAccessGuard accessGuard;

    @Autowired(required = false)
    private GovernanceApprovalRequestMapper requestMapper;
    @Autowired(required = false)
    private GovernanceApprovalEventMapper eventMapper;
    @Autowired(required = false)
    private GovernedResourceMapper resourceMapper;
    @Autowired(required = false)
    private GovernedResourceVersionMapper versionMapper;
    @Autowired(required = false)
    private GovernedResourceAdapterRegistry adapterRegistry;
    @Autowired(required = false)
    private GovernanceDependencyService dependencyService;
    @Autowired(required = false)
    private GovernanceResourceBootstrapService bootstrapService;
    @Autowired(required = false)
    private GovernanceImpactService impactService;
    @Autowired(required = false)
    private RuleDraftDeletionService ruleDraftDeletionService;
    @Autowired(required = false)
    private com.hengshucredit.rule.server.mapper.RuleRevisionMapper ruleRevisionMapper;

    public List<GovernanceResourceState> resourceStates(String type, List<Long> ids, String actor) {
        String resourceType = normalizeType(type);
        requireResourcePermission(resourceType, "view");
        if (ids == null || ids.isEmpty()) return List.of();
        if (ids.size() > 100 || ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("单次最多查询100个有效资源ID");
        }
        List<Long> uniqueIds = ids.stream().distinct().toList();
        Map<Long, GovernedResource> resources = new LinkedHashMap<>();
        resourceMapper.selectList(new LambdaQueryWrapper<GovernedResource>()
                .eq(GovernedResource::getResourceType, resourceType).in(GovernedResource::getResourceId, uniqueIds))
                .forEach(resource -> resources.put(resource.getResourceId(), resource));
        Map<Long, GovernanceApprovalRequest> requests = new LinkedHashMap<>();
        requestMapper.selectLatestResourceRequests(resourceType, uniqueIds)
                .forEach(request -> requests.put(request.getResourceId(), request));
        Set<Long> ruleDrafts = new java.util.HashSet<>();
        Set<Long> approvedRules = new java.util.HashSet<>();
        if ("RULE".equals(resourceType)) {
            ruleRevisionMapper.selectList(new LambdaQueryWrapper<com.hengshucredit.rule.model.entity.RuleRevision>()
                    .select(com.hengshucredit.rule.model.entity.RuleRevision::getDefinitionId,
                            com.hengshucredit.rule.model.entity.RuleRevision::getState)
                    .in(com.hengshucredit.rule.model.entity.RuleRevision::getDefinitionId, uniqueIds)
                    .in(com.hengshucredit.rule.model.entity.RuleRevision::getState, "DRAFT", "APPROVED")
                    .groupBy(com.hengshucredit.rule.model.entity.RuleRevision::getDefinitionId,
                            com.hengshucredit.rule.model.entity.RuleRevision::getState))
                    .forEach(revision -> {
                        if ("DRAFT".equals(revision.getState())) ruleDrafts.add(revision.getDefinitionId());
                        if ("APPROVED".equals(revision.getState())) approvedRules.add(revision.getDefinitionId());
                    });
        }
        return uniqueIds.stream().map(id -> {
            GovernedResource resource = resources.get(id);
            GovernanceApprovalRequest request = requests.get(id);
            return new GovernanceResourceState(id, resource == null ? null : resource.getEffectiveStatus(),
                    resource != null && resource.getEffectiveVersionId() != null,
                    request == null ? null : request.getId(), request == null ? null : request.getStatus(),
                    request == null ? null : request.getAction(), request == null ? null : request.getApplicant(),
                    request != null && Objects.equals(actor, request.getApplicant()), ruleDrafts.contains(id), approvedRules.contains(id));
        }).toList();
    }

    public List<Long> draftResourceIds(String type, List<Long> ids) {
        String resourceType = normalizeType(type);
        requireResourcePermission(resourceType, "view");
        if (ids == null || ids.isEmpty()) return List.of();
        if (ids.size() > 100) throw new IllegalArgumentException("单次最多查询 100 个资源状态");
        return resourceMapper.selectList(new LambdaQueryWrapper<GovernedResource>()
                .eq(GovernedResource::getResourceType, resourceType)
                .eq(GovernedResource::getEffectiveStatus, "DRAFT")
                .isNull(GovernedResource::getEffectiveVersionId)
                .in(GovernedResource::getResourceId, ids)).stream().map(GovernedResource::getResourceId).toList();
    }

    public GovernanceRequestView editableWorkspace(String type, Long id, String actor) {
        String resourceType = normalizeType(type);
        requireResourcePermission(resourceType, "edit");
        GovernanceApprovalRequest request = findActiveRequest(resourceType + ":" + id);
        if (request == null || isTerminalRequest(request)) return null;
        if (!Objects.equals(actor, request.getApplicant()) || !GovernanceRequestStatus.EDITING.name().equals(request.getStatus())) return null;
        return GovernanceRequestView.from(request);
    }

    @Transactional
    public GovernanceDeletionResult deleteResource(String type, Long id, String actor) {
        String resourceType = normalizeType(type);
        if (GovernanceResourceTypes.RULE.equals(resourceType)) return deleteRule(id, actor);
        String operator = requireActor(actor);
        requireResourcePermission(resourceType, "edit");
        GovernedResource resource = findResource(resourceType, id);
        if (resource == null || "DELETED".equals(resource.getEffectiveStatus())) throw new IllegalArgumentException("资源不存在或已删除");
        var adapter = requireAdapter(resourceType);
        ResourceSnapshot snapshot = adapter.loadEffective(id);
        var issues = impactService.analyze(resourceType, id, "DELETE", snapshot);
        if (!issues.isEmpty()) throw new GovernanceStateException("GOVERNANCE_PREFLIGHT_FAILED", issues.get(0).message());
        if (isWorkspace(resource)) {
            GovernanceApprovalRequest active = findActiveRequest(resourceType + ":" + id);
            if (active != null && !isTerminalRequest(active)) cancelForDeletion(active, operator);
            adapter.apply(new ApprovalApplyContext(null, id, 0, "DRAFT_DELETE", snapshot, operator, null));
            resource.setEffectiveStatus("DELETED");
            persistResource(resource);
            return new GovernanceDeletionResult(true, null);
        }
        if (accessGuard != null) accessGuard.require("approval:submit");
        GovernanceDraftRequest draft = new GovernanceDraftRequest();
        draft.setResourceType(resourceType); draft.setResourceId(id); draft.setProjectId(resource.getProjectId());
        draft.setAction("DELETE"); draft.setChangeSummary("删除资源");
        return new GovernanceDeletionResult(false, createDraft(draft, operator).getId());
    }

    private void cancelForDeletion(GovernanceApprovalRequest active, String actor) {
        requireApplicant(active, actor);
        requireNotPendingNewContent(active);
        requireTransition(active, GovernanceRequestStatus.CANCELLED);
        String from = active.getStatus();
        active.setStatus(GovernanceRequestStatus.CANCELLED.name());
        active.setActiveResourceKey(null);
        active.setReviewComment("删除未发布草稿，撤回关联申请");
        active.setReviewTime(LocalDateTime.now());
        persistRequest(active);
        appendEvent(active, "CANCEL", from, actor, active.getReviewComment(), null);
    }

    @Transactional
    public GovernanceDeletionResult deleteRule(Long definitionId, String actor) {
        requireResourcePermission(GovernanceResourceTypes.RULE, "edit");
        String operator = requireActor(actor);
        var definition = ruleDraftDeletionService.lock(definitionId);
        if (!ruleDraftDeletionService.isUnapproved(definition)) {
            if (accessGuard != null) accessGuard.require("approval:submit");
            GovernanceDraftRequest draft = new GovernanceDraftRequest();
            draft.setResourceType(GovernanceResourceTypes.RULE);
            draft.setResourceId(definitionId);
            draft.setProjectId(definition.getProjectId());
            draft.setAction("DELETE");
            draft.setChangeSummary("删除规则");
            return new GovernanceDeletionResult(false, createDraft(draft, operator).getId());
        }
        ResourceSnapshot snapshot = requireAdapter(GovernanceResourceTypes.RULE).loadEffective(definitionId);
        // Discarding unfinished content does not require it to compile or have valid outbound references.
        List<GovernanceIssue> issues = impactService.analyze("RULE", definitionId, "DELETE", snapshot);
        if (!issues.isEmpty()) throw new GovernanceStateException("GOVERNANCE_PREFLIGHT_FAILED", issues.get(0).message());
        GovernanceApprovalRequest active = findActiveRequest("RULE:" + definitionId);
        if (active != null && !isTerminalRequest(active)) {
            requireApplicant(active, operator);
            requireNotPendingNewContent(active);
            requireTransition(active, GovernanceRequestStatus.CANCELLED);
            String from = active.getStatus();
            active.setStatus(GovernanceRequestStatus.CANCELLED.name());
            active.setActiveResourceKey(null);
            active.setReviewComment("新增规则未生效，删除时撤回申请");
            active.setReviewTime(LocalDateTime.now());
            persistRequest(active);
            appendEvent(active, "CANCEL", from, operator, active.getReviewComment(), null);
        }
        ruleDraftDeletionService.discard(definition, operator);
        GovernedResource resource = resourceMapper.selectOne(new LambdaQueryWrapper<GovernedResource>()
                .eq(GovernedResource::getResourceType, "RULE").eq(GovernedResource::getResourceId, definitionId));
        if (resource != null) {
            resource.setEffectiveStatus("DELETED");
            persistResource(resource);
        }
        return new GovernanceDeletionResult(true, null);
    }

    @Transactional
    public GovernanceApprovalRequest createDraft(GovernanceDraftRequest draft,
                                                 String actor) {
        if (draft == null) {
            throw new IllegalArgumentException("审批草稿不能为空");
        }
        String applicant = requireActor(actor);
        String resourceType = normalizeType(draft.getResourceType());
        requireResourcePermission(resourceType, "edit");
        lockRule(resourceType, draft.getResourceId());
        GovernanceAction action = normalizeAction(draft.getAction());
        String restoreMode = normalizeRestoreMode(draft.getRestoreMode());
        if ("REUSE".equals(restoreMode) && action != GovernanceAction.RESTORE) {
            throw new IllegalArgumentException("复用历史版本仅适用于恢复操作");
        }
        if ("REUSE".equals(restoreMode)) {
            GovernedResourceVersion source = requireRestoreVersion(resourceType, draft.getResourceId(), draft.getSourceVersionId());
            // 来源必须由服务端读取，不能接受调用者篡改的历史内容。
            draft.setSnapshotJson(source.getSnapshotJson());
            draft.setSecretPayloadCiphertext(source.getSecretPayloadCiphertext());
            draft.setSecretDigest(source.getSecretDigest());
            draft.setEffectiveStatus(source.getEffectiveStatus());
        }
        GovernedResourceAdapter adapter = requireAdapter(resourceType);
        GovernedResource resource = draft.getResourceId() == null
                ? null : findResource(resourceType, draft.getResourceId());
        if (isWorkspace(resource) && (action == GovernanceAction.UPDATE || action == GovernanceAction.ENABLE)) {
            action = GovernanceAction.CREATE;
        }
        validateDraftTarget(action, draft.getResourceId(), resource);
        String publishMode = draft.getPublishMode();
        if (publishMode != null && !Set.of("NEW", "OVERWRITE").contains(publishMode))
            throw new IllegalArgumentException("发布方式必须为 NEW 或 OVERWRITE");
        if (publishMode != null && (resource == null || resource.getEffectiveVersionId() == null))
            throw new IllegalArgumentException("请先完成资源首次发布，再创建并行版本草稿");
        if (publishMode != null && !Set.of(GovernanceAction.UPDATE, GovernanceAction.RESTORE).contains(action))
            throw new IllegalArgumentException("并行版本草稿只支持配置编辑");
        if ("OVERWRITE".equals(publishMode)) {
            GovernedResourceVersion target = versionHead(resourceType, draft.getResourceId(), draft.getTargetVersionId());
            if (!ResourceVersionLines.published(target)) throw new IllegalArgumentException("只能覆盖已发布的业务版本");
            if (!Objects.equals(target.getId(), draft.getExpectedTargetSnapshotId()))
                throw new GovernanceStateException("VERSION_TARGET_CHANGED", "覆盖目标已变化，请刷新后重试");
        }

        String activeResourceKey = resource == null
                ? null : resourceType + ":" + resource.getResourceId();
        if (publishMode != null) activeResourceKey += ":VERSION:" + UUID.randomUUID();
        GovernanceApprovalRequest active = activeResourceKey == null
                ? null : findActiveRequest(activeResourceKey);
        ResourceSnapshot effective = resource == null
                ? null : adapter.loadEffective(draft.getResourceId());
        String snapshotJson = draft.getSnapshotJson();
        if ((snapshotJson == null || snapshotJson.isBlank())
                && effective != null) {
            snapshotJson = effective.snapshotJson();
        }
        ResourceSnapshot normalized = adapter.normalizeDraft(
                new ResourceSnapshot(snapshotJson,
                        draft.getEffectiveStatus(),
                        firstNotBlank(
                                draft.getSecretPayloadCiphertext(),
                                firstNotBlank(active == null ? null
                                                : active
                                                .getSecretPayloadCiphertext(),
                                        effective == null ? null
                                                : effective
                                                .secretPayloadCiphertext())),
                        firstNotBlank(draft.getSecretDigest(),
                                firstNotBlank(active == null ? null
                                                : active.getSecretDigest(),
                                        effective == null ? null
                                                : effective
                                                .secretDigest()))));
        if (resource == null) {
            activeResourceKey = createActiveResourceKey(
                    resourceType, draft.getProjectId(), normalized);
            active = findActiveRequest(activeResourceKey);
            if (active != null) {
                normalized = new ResourceSnapshot(
                        normalized.snapshotJson(),
                        normalized.effectiveStatus(),
                        firstNotBlank(
                                normalized.secretPayloadCiphertext(),
                                active.getSecretPayloadCiphertext()),
                        firstNotBlank(normalized.secretDigest(),
                                active.getSecretDigest()));
            }
        }
        if (active != null && isTerminalRequest(active)) {
            active.setActiveResourceKey(null);
            persistRequest(active);
            active = null;
        }
        if (active != null
                && !GovernanceRequestStatus.EDITING.name()
                .equals(active.getStatus())) {
            throw new GovernanceStateException(
                    "GOVERNANCE_REQUEST_ALREADY_PENDING",
                    "当前资源已有待审批申请，请先完成现有审批");
        }
        if (active != null) {
            requireApplicant(active, applicant);
            // 模块编辑页继续保存历史恢复草稿，保留其来源和发布方式。
            if (action == GovernanceAction.UPDATE && "RESTORE".equals(active.getAction())) action = GovernanceAction.RESTORE;
            if (draft.getRestoreMode() != null && ("RESTORE".equals(active.getAction()) || action == GovernanceAction.RESTORE)) {
                throw new GovernanceStateException("RESTORE_DRAFT_EXISTS", "当前资源已有草稿，请继续编辑，或取消后重新恢复历史版本");
            }
        }
        if (active != null
                && !action.name().equals(active.getAction())) {
            throw new GovernanceStateException(
                    "GOVERNANCE_ACTION_CONFLICT",
                    "当前资源已有其他生命周期动作的编辑中申请，请先取消该申请");
        }
        if (active != null) {
            normalized = saveWorkspace(active, normalized, applicant);
            active.setDraftSnapshotJson(normalized.snapshotJson());
            active.setSnapshotDigest(
                    Sha256Digests.text(normalized.snapshotJson()));
            active.setSecretPayloadCiphertext(
                    normalized.secretPayloadCiphertext());
            active.setSecretDigest(normalized.secretDigest());
            active.setChangeSummary(draft.getChangeSummary());
            persistRequest(active);
            saveDraftDependencies(active, normalized);
            appendEvent(active, "SAVE_DRAFT", active.getStatus(),
                    actor, draft.getChangeSummary(), null);
            return active;
        }
        GovernanceApprovalRequest request = new GovernanceApprovalRequest();
        request.setRequestNo(newRequestNo());
        request.setResourceType(resourceType);
        request.setResourceId(draft.getResourceId() == null
                ? 0L : draft.getResourceId());
        request.setProjectId(draft.getProjectId());
        request.setAction(action.name());
        request.setStatus(GovernanceRequestStatus.EDITING.name());
        request.setActiveResourceKey(activeResourceKey);
        request.setBaseVersionId(resource == null
                ? null : resource.getEffectiveVersionId());
        request.setBaseVersionNo(resource == null
                ? null : resource.getEffectiveVersionNo());
        request.setSourceVersionId(draft.getSourceVersionId());
        request.setPublishMode(publishMode);
        request.setTargetVersionId(draft.getTargetVersionId());
        request.setExpectedTargetSnapshotId(draft.getExpectedTargetSnapshotId());
        if ("OVERWRITE".equals(publishMode)) {
            var target = versionHead(resourceType, draft.getResourceId(), draft.getTargetVersionId());
            request.setBaseVersionId(target.getId()); request.setBaseVersionNo(target.getDisplayVersion());
        }
        request.setRestoreMode(action == GovernanceAction.RESTORE ? restoreMode : null);
        request.setDraftSnapshotJson(normalized.snapshotJson());
        request.setSnapshotDigest(
                Sha256Digests.text(normalized.snapshotJson()));
        request.setSecretPayloadCiphertext(
                normalized.secretPayloadCiphertext());
        request.setSecretDigest(normalized.secretDigest());
        request.setChangeSummary(draft.getChangeSummary());
        request.setApplicant(applicant);
        normalized = saveWorkspace(request, normalized, applicant);
        request.setDraftSnapshotJson(normalized.snapshotJson());
        request.setSnapshotDigest(Sha256Digests.text(normalized.snapshotJson()));
        request.setSecretPayloadCiphertext(normalized.secretPayloadCiphertext());
        request.setSecretDigest(normalized.secretDigest());
        insertRequest(request);
        saveDraftDependencies(request, normalized);
        appendEvent(request, "CREATE_DRAFT", null, actor,
                draft.getChangeSummary(), null);
        return request;
    }

    @Transactional
    public GovernanceApprovalRequest saveDraft(Long requestId,
                                               GovernanceDraftRequest draft,
                                               String actor) {
        GovernanceApprovalRequest request = requireMutableRequest(requestId);
        requireStatus(request, GovernanceRequestStatus.EDITING);
        requireApplicant(request, actor);
        requireResourcePermission(request.getResourceType(), "edit");
        String snapshotJson = draft.getSnapshotJson();
        if (draft.getExpectedSnapshotDigest() != null && !Objects.equals(draft.getExpectedSnapshotDigest(), request.getSnapshotDigest()))
            throw new GovernanceStateException("GOVERNANCE_DRAFT_CHANGED", "草稿已被修改，请刷新后重新保存");
        if (snapshotJson == null || snapshotJson.isBlank()) {
            snapshotJson = request.getDraftSnapshotJson();
        }
        ResourceSnapshot normalized = requireAdapter(
                request.getResourceType()).normalizeDraft(
                new ResourceSnapshot(snapshotJson,
                        draft.getEffectiveStatus(),
                        firstNotBlank(
                                draft.getSecretPayloadCiphertext(),
                                request.getSecretPayloadCiphertext()),
                        firstNotBlank(draft.getSecretDigest(),
                                request.getSecretDigest())));
        normalized = saveWorkspace(request, normalized, actor);
        request.setDraftSnapshotJson(normalized.snapshotJson());
        request.setSnapshotDigest(
                Sha256Digests.text(normalized.snapshotJson()));
        request.setSecretPayloadCiphertext(
                normalized.secretPayloadCiphertext());
        request.setSecretDigest(normalized.secretDigest());
        request.setChangeSummary(draft.getChangeSummary());
        persistRequest(request);
        saveDraftDependencies(request, normalized);
        appendEvent(request, "SAVE_DRAFT", request.getStatus(), actor,
                draft.getChangeSummary(), null);
        return request;
    }

    @Transactional
    public GovernanceApprovalRequest saveDraftSnapshot(Long requestId,
                                                       String snapshotJson,
                                                       String actor) {
        GovernanceApprovalRequest request = requireMutableRequest(requestId);
        requireStatus(request, GovernanceRequestStatus.EDITING);
        requireApplicant(request, actor);
        requireResourcePermission(request.getResourceType(), "edit");
        ResourceSnapshot normalized = normalize(request, snapshotJson);
        normalized = saveWorkspace(request, normalized, actor);
        request.setDraftSnapshotJson(normalized.snapshotJson());
        request.setSecretPayloadCiphertext(
                normalized.secretPayloadCiphertext());
        request.setSecretDigest(normalized.secretDigest());
        request.setSnapshotDigest(
                Sha256Digests.text(normalized.snapshotJson()));
        persistRequest(request);
        saveDraftDependencies(request, normalized);
        appendEvent(request, "SAVE_DRAFT", request.getStatus(), actor,
                null, null);
        return request;
    }

    @Transactional
    public GovernancePreflightReport preflight(Long requestId,
                                               String actor) {
        GovernanceApprovalRequest request = requireMutableRequest(requestId);
        requireStatus(request, GovernanceRequestStatus.EDITING);
        requireApplicant(request, actor);
        requireResourcePermission(request.getResourceType(), "edit");
        ResourceSnapshot snapshot = requestSnapshot(request);
        GovernancePreflightReport report = evaluate(request, snapshot);
        request.setValidationReportJson(reportJson(report));
        request.setDependencyDigest(report.dependencyDigest());
        persistRequest(request);
        persistDependencies(request, report, null);
        return report;
    }

    @Transactional
    public GovernanceApprovalRequest submit(Long requestId,
                                            GovernanceSubmitRequest submit,
                                            String actor) {
        GovernanceApprovalRequest request = requireMutableRequest(requestId);
        requireResourcePermission(request.getResourceType(), "submit");
        requireStatus(request, GovernanceRequestStatus.EDITING);
        requireApplicant(request, actor);
        requireResourcePermission(request.getResourceType(), "edit");
        if (submit != null && submit.getExpectedSnapshotDigest() != null
                && !submit.getExpectedSnapshotDigest().equals(request.getSnapshotDigest())) {
            throw new GovernanceStateException("GOVERNANCE_DRAFT_CHANGED", "草稿已更新，请刷新并确认内容后重新提交发布");
        }
        ResourceSnapshot normalized = normalize(request,
                request.getDraftSnapshotJson());
        if ("CREATE".equals(request.getAction()) && (request.getResourceId() == null || request.getResourceId() <= 0)) {
            normalized = saveWorkspace(request, normalized, actor);
        }
        GovernancePreflightReport report = evaluate(request, normalized);
        if (!report.valid()) {
            throw new GovernanceStateException(
                    "GOVERNANCE_PREFLIGHT_FAILED",
                    firstErrorMessage(report));
        }

        String fromStatus = request.getStatus();
        request.setStatus(GovernanceRequestStatus.PENDING.name());
        request.setDraftSnapshotJson(normalized.snapshotJson());
        request.setSubmittedSnapshotJson(normalized.snapshotJson());
        request.setSnapshotDigest(
                Sha256Digests.text(normalized.snapshotJson()));
        request.setSecretPayloadCiphertext(
                normalized.secretPayloadCiphertext());
        request.setSecretDigest(normalized.secretDigest());
        request.setDependencyDigest(report.dependencyDigest());
        request.setValidationReportJson(reportJson(report));
        request.setSubmitComment(submit == null
                ? null : submit.getComment());
        request.setSubmitTime(LocalDateTime.now());
        persistRequest(request);
        persistDependencies(request, report, null);
        appendEvent(request, "SUBMIT", fromStatus, actor,
                request.getSubmitComment(), reportJson(report));
        return request;
    }

    @Transactional
    public GovernanceApprovalRequest approve(Long requestId,
                                             GovernanceReviewRequest review,
                                             String actor) {
        return approve(requestId, review, actor, applied -> { });
    }

    /** 供同一事务中的迁移流程接收落库时的准确 ID 映射，不通过编码反查。 */
    @Transactional
    public GovernanceApprovalRequest approve(Long requestId,
                                             GovernanceReviewRequest review,
                                             String actor,
                                             java.util.function.Consumer<AppliedResource> appliedConsumer) {
        GovernanceApprovalRequest request = requireMutableRequest(requestId);
        requireResourcePermission(request.getResourceType(), "view");
        if (accessGuard != null) accessGuard.require("approval:approve");
        requireTransition(request, GovernanceRequestStatus.APPROVED);
        if (!canReview(request, actor)) {
            throw new GovernanceStateException(
                    "APPROVAL_REVIEW_FORBIDDEN", "当前用户不能审批该申请");
        }
        GovernedResource resource = findResource(
                request.getResourceType(), request.getResourceId());
        if (hasBaseConflict(request, resource)) {
            return markConflict(request, actor, review,
                    "BASE_VERSION_CHANGED", "当前生效版本已变化，请重新发起审批");
        }

        boolean deleting = GovernanceAction.DELETE == normalizeAction(request.getAction());
        // 删除不再使用自身的出站引用；旧版待审批删除也不能被上游停用或改版锁住。
        List<GovernancePreflightReport.ResolvedDependency> submitted =
                deleting ? List.of() : loadDependencies(requestId);
        GovernancePreflightReport dependencyReport =
                revalidateDependencies(submitted);
        if (!dependencyReport.valid()) {
            return markConflict(request, actor, review,
                    "DEPENDENCY_CHANGED", firstErrorMessage(dependencyReport));
        }

        ResourceSnapshot snapshot = normalize(request,
                request.getSubmittedSnapshotJson());
        GovernancePreflightReport currentReport = evaluate(request, snapshot);
        if (!currentReport.valid()
                || (!deleting && !safeEquals(request.getDependencyDigest(),
                currentReport.dependencyDigest()))) {
            return markConflict(request, actor, review,
                    "PREFLIGHT_CHANGED",
                    currentReport.valid()
                            ? "依赖快照摘要已变化，请重新发起审批"
                            : firstErrorMessage(currentReport));
        }

        GovernedResourceVersion reusedVersion = "OVERWRITE".equals(request.getPublishMode())
                ? versionHead(request.getResourceType(), request.getResourceId(), request.getTargetVersionId()) : "REUSE".equals(request.getRestoreMode())
                ? requireRestoreVersion(request.getResourceType(), request.getResourceId(), request.getSourceVersionId()) : null;
        int nextVersionNo = nextVersionNumber(request.getResourceType(), request.getResourceId(), resource);
        int businessVersion = reusedVersion != null ? reusedVersion.getDisplayVersion()
                : nextBusinessVersion(request.getResourceType(), request.getResourceId(), resource);
        boolean keepLatest = reusedVersion != null && resource != null && businessVersion != defaultVersionNo(resource);
        ResourceSnapshot previousLatest = keepLatest ? requireAdapter(request.getResourceType()).loadEffective(request.getResourceId()) : null;
        AppliedResource applied;
        try {
            applied = requireAdapter(
                    request.getResourceType()).apply(
                    new ApprovalApplyContext(
                            request.getId(),
                            isCreateRequest(request)
                                    ? null : request.getResourceId(),
                            businessVersion,
                            reusedVersion != null ? "RESTORE" : request.getAction(),
                            snapshot,
                            actor,
                            reusedVersion != null ? reusedVersion.getId() : request.getSourceVersionId(),
                            reusedVersion != null ? "REUSE" : request.getRestoreMode(), keepLatest));
        } catch (DuplicateKeyException collision) {
            // A participating resource transaction may already be rollback-only.
            // Preserve the original failure and roll back the entire approval atomically.
            throw collision;
        }
        Long appliedResourceId = requireAppliedResourceId(applied);
        if (applied.versionNo() != null) businessVersion = applied.versionNo();

        if (resource == null) {
            resource = new GovernedResource();
            resource.setResourceType(request.getResourceType());
            resource.setResourceId(appliedResourceId);
            resource.setProjectId(
                    GovernanceResourceTypes.PROJECT.equals(
                            request.getResourceType())
                            ? appliedResourceId
                            : request.getProjectId());
            resource.setEffectiveVersionNo(0);
            resource.setEffectiveStatus("ACTIVE");
            insertResource(resource);
            request.setResourceId(appliedResourceId);
        } else if (!resource.getResourceId().equals(appliedResourceId)) {
            throw new GovernanceStateException(
                    "GOVERNANCE_APPLY_RESOURCE_MISMATCH",
                    "适配器返回的资源 ID 与审批目标不一致");
        }

        // 审批申请保留提交内容；生效版本必须使用目标环境实际落库的字段及父子 ID。
        ResourceSnapshot effectiveSnapshot = requireAdapter(request.getResourceType()).loadApplied(applied);
        GovernedResourceVersion version = new GovernedResourceVersion();
        version.setGovernedResourceId(resource.getId());
        version.setResourceType(request.getResourceType());
        version.setResourceId(appliedResourceId);
        version.setVersionNo(nextVersionNo);
        version.setBusinessVersion(businessVersion);
        version.setSourceVersionId(request.getSourceVersionId());
        version.setApprovalRequestId(request.getId());
        version.setSnapshotJson(effectiveSnapshot.snapshotJson());
        version.setSnapshotDigest(effectiveSnapshot == snapshot ? request.getSnapshotDigest()
                : Sha256Digests.text(effectiveSnapshot.snapshotJson()));
        version.setSecretPayloadCiphertext(
                effectiveSnapshot.secretPayloadCiphertext());
        version.setSecretDigest(effectiveSnapshot.secretDigest());
        version.setEffectiveStatus(applied.effectiveStatus() == null
                ? snapshot.effectiveStatus() : applied.effectiveStatus());
        version.setChangeSummary(request.getChangeSummary());
        version.setCreateBy(actor);
        persistVersion(version);

        if (keepLatest) {
            requireAdapter(request.getResourceType()).restoreLatest(appliedResourceId, previousLatest, actor);
        } else {
            resource.setEffectiveVersionId(version.getId());
            resource.setEffectiveVersionNo(businessVersion);
            resource.setEffectiveStatus(version.getEffectiveStatus());
            persistResource(resource);
        }
        bindDependencyVersion(requestId, version.getId());

        String fromStatus = request.getStatus();
        request.setStatus(GovernanceRequestStatus.APPROVED.name());
        request.setActiveResourceKey(null);
        request.setReviewer(requireActor(actor));
        request.setReviewComment(review == null
                ? null : review.getComment());
        request.setReviewTime(LocalDateTime.now());
        persistRequest(request);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("versionId", version.getId());
        details.put("versionNo", version.getDisplayVersion());
        details.put("revisionNo", version.getVersionNo());
        details.put("sourceVersionId", version.getSourceVersionId());
        details.put("restoreMode", request.getRestoreMode());
        details.put("restoredVersionId", request.getSourceVersionId());
        details.put("artifactId", applied.artifactId());
        appendEvent(request, "APPROVE", fromStatus, actor,
                request.getReviewComment(), JSON.toJSONString(details));
        appliedConsumer.accept(applied);
        return request;
    }

    @Transactional
    public GovernanceApprovalRequest reject(Long requestId,
                                            GovernanceReviewRequest review,
                                            String actor) {
        GovernanceApprovalRequest request = requireMutableRequest(requestId);
        requireResourcePermission(request.getResourceType(), "view");
        if (accessGuard != null) accessGuard.require("approval:approve");
        requireTransition(request, GovernanceRequestStatus.REJECTED);
        if (!canReview(request, actor)) {
            throw new GovernanceStateException(
                    "APPROVAL_REVIEW_FORBIDDEN", "当前用户不能审批该申请");
        }
        terminateAdapterWorkflow(request, actor,
                review == null ? null : review.getComment(),
                GovernanceRequestStatus.REJECTED.name());
        String fromStatus = request.getStatus();
        request.setStatus(GovernanceRequestStatus.REJECTED.name());
        request.setActiveResourceKey(null);
        request.setReviewer(requireActor(actor));
        request.setReviewComment(review == null
                ? null : review.getComment());
        request.setReviewTime(LocalDateTime.now());
        persistRequest(request);
        appendEvent(request, "REJECT", fromStatus, actor,
                request.getReviewComment(), null);
        return request;
    }

    @Transactional
    public GovernanceApprovalRequest cancel(Long requestId,
                                            GovernanceReviewRequest reason,
                                            String actor) {
        GovernanceApprovalRequest request = requireMutableRequest(requestId);
        requireTransition(request, GovernanceRequestStatus.CANCELLED);
        requireApplicant(request, actor);
        requireResourcePermission(request.getResourceType(), "edit");
        if (GovernanceRequestStatus.PENDING.name().equals(request.getStatus()) && isUnapprovedNewContent(request)) {
            requireNotPendingNewContent(request);
        }
        if (GovernanceRequestStatus.EDITING.name().equals(request.getStatus())
                && "CREATE".equals(request.getAction()) && request.getResourceId() != null && request.getResourceId() > 0
                && isWorkspace(findResource(request.getResourceType(), request.getResourceId()))) {
            deleteResource(request.getResourceType(), request.getResourceId(), actor);
            return requireRequest(requestId);
        }
        terminateAdapterWorkflow(request, actor,
                reason == null ? null : reason.getComment(),
                GovernanceRequestStatus.CANCELLED.name());
        String fromStatus = request.getStatus();
        request.setStatus(GovernanceRequestStatus.CANCELLED.name());
        request.setActiveResourceKey(null);
        request.setReviewComment(reason == null
                ? null : reason.getComment());
        request.setReviewTime(LocalDateTime.now());
        persistRequest(request);
        appendEvent(request, "CANCEL", fromStatus,
                requireActor(actor), request.getReviewComment(), null);
        return request;
    }

    private void requireNotPendingNewContent(GovernanceApprovalRequest request) {
        if (GovernanceRequestStatus.PENDING.name().equals(request.getStatus())) throw new GovernanceStateException(
                "NEW_CONTENT_REJECTION_REQUIRED", "新增内容正在待审批，请先由审批人驳回，再删除草稿");
    }

    private boolean isUnapprovedNewContent(GovernanceApprovalRequest request) {
        if (isCreateRequest(request)) return true;
        if ("RULE".equals(request.getResourceType()) && ruleDraftDeletionService != null)
            return ruleDraftDeletionService.isUnapproved(ruleDraftDeletionService.lock(request.getResourceId()));
        return isWorkspace(findResource(request.getResourceType(), request.getResourceId()));
    }

    @Transactional
    public GovernanceApprovalRequest createRestoreDraft(
            String resourceType,
            Long resourceId,
            Long sourceVersionId,
            String actor) {
        return createRestoreDraft(resourceType, resourceId, sourceVersionId, "NEW", actor);
    }

    @Transactional
    public GovernanceApprovalRequest createRestoreDraft(String resourceType, Long resourceId, Long sourceVersionId,
                                                         String restoreMode, String actor) {
        requireResourcePermission(resourceType, "edit");
        GovernedResource resource = findResource(
                normalizeType(resourceType), resourceId);
        if (resource == null || resource.getEffectiveVersionId() == null) {
            throw new IllegalArgumentException("待恢复资源不存在或尚无生效版本");
        }
        GovernedResourceVersion source = findVersion(sourceVersionId);
        if (source == null
                || !normalizeType(resourceType)
                .equals(source.getResourceType())
                || !resourceId.equals(source.getResourceId())) {
            throw new IllegalArgumentException("待恢复的历史版本不存在");
        }
        if (Boolean.TRUE.equals(source.getDeleted())) throw new IllegalArgumentException("已删除版本不能恢复，请选择有效版本");
        GovernanceDraftRequest draft = new GovernanceDraftRequest();
        draft.setResourceType(resourceType);
        draft.setResourceId(resourceId);
        draft.setProjectId(resource.getProjectId());
        draft.setAction(GovernanceAction.RESTORE.name());
        draft.setSnapshotJson(source.getSnapshotJson());
        draft.setEffectiveStatus(source.getEffectiveStatus());
        draft.setSecretPayloadCiphertext(
                source.getSecretPayloadCiphertext());
        draft.setSecretDigest(source.getSecretDigest());
        draft.setSourceVersionId(sourceVersionId);
        draft.setRestoreMode(normalizeRestoreMode(restoreMode));
        draft.setChangeSummary(("REUSE".equals(draft.getRestoreMode()) ? "恢复内容并复用版本号 V" : "从历史版本新增 V") + source.getDisplayVersion());
        return createDraft(draft, actor);
    }

    private String normalizeRestoreMode(String mode) {
        if (mode == null || mode.isBlank()) return "NEW";
        if (!"NEW".equals(mode) && !"REUSE".equals(mode)) throw new IllegalArgumentException("恢复方式必须为 NEW 或 REUSE");
        return mode;
    }

    /** 接入旧模块历史时保留明确的表类型与快照 ID，业务版本号和不可变记录序号分开。 */
    @Transactional
    public GovernanceApprovalRequest createLegacyRestoreDraft(String type, Long resourceId, String legacyType, Long legacyId,
                                                               Integer businessVersion, ResourceSnapshot snapshot, String mode, String actor) {
        requireResourcePermission(type, "edit");
        lockRule(type, resourceId);
        GovernedResource resource = findResource(type, resourceId);
        if (resource == null) throw new IllegalArgumentException("待恢复资源不存在");
        if (ResourceVersionLines.heads(versions(type, resourceId)).stream().anyMatch(version -> Objects.equals(businessVersion, version.getDisplayVersion()) && Boolean.TRUE.equals(version.getDeleted())))
            throw new IllegalArgumentException("所属业务版本已删除，不能从早期历史入口重新接入");
        GovernedResourceVersion source = versionMapper.selectOne(new LambdaQueryWrapper<GovernedResourceVersion>()
                .eq(GovernedResourceVersion::getLegacySourceType, legacyType).eq(GovernedResourceVersion::getLegacySourceId, legacyId));
        if (source == null) {
            ResourceSnapshot normalized = requireAdapter(type).normalizeDraft(snapshot);
            source = new GovernedResourceVersion();
            source.setGovernedResourceId(resource.getId()); source.setResourceType(type); source.setResourceId(resourceId);
            source.setVersionNo(nextVersionNumber(type, resourceId, resource)); source.setBusinessVersion(businessVersion);
            source.setLegacySourceType(legacyType); source.setLegacySourceId(legacyId);
            source.setSnapshotJson(normalized.snapshotJson()); source.setSnapshotDigest(Sha256Digests.text(normalized.snapshotJson()));
            source.setSecretPayloadCiphertext(normalized.secretPayloadCiphertext()); source.setSecretDigest(normalized.secretDigest());
            source.setEffectiveStatus(normalized.effectiveStatus()); source.setCreateBy(actor);
            source.setChangeSummary("接入历史版本 V" + businessVersion);
            persistVersion(source);
        }
        return createRestoreDraft(type, resourceId, source.getId(), mode, actor);
    }

    private GovernedResourceVersion requireRestoreVersion(String type, Long resourceId, Long versionId) {
        GovernedResourceVersion source = findVersion(versionId);
        if (source == null || !normalizeType(type).equals(source.getResourceType()) || !java.util.Objects.equals(resourceId, source.getResourceId()))
            throw new IllegalArgumentException("待恢复的历史版本不存在或不属于当前资源");
        if (!ResourceVersionLines.published(source))
            throw new IllegalArgumentException("仅已发布的历史版本可以复用原版本号");
        return source;
    }

    protected int nextVersionNumber(String type, Long resourceId, GovernedResource resource) {
        int maximum = resource == null ? 0 : defaultVersionNo(resource);
        if (versionMapper != null && resourceId != null && resourceId > 0) {
            GovernedResourceVersion latest = versionMapper.selectOne(new LambdaQueryWrapper<GovernedResourceVersion>()
                    .eq(GovernedResourceVersion::getResourceType, type).eq(GovernedResourceVersion::getResourceId, resourceId)
                    .orderByDesc(GovernedResourceVersion::getVersionNo).last("LIMIT 1"));
            if (latest != null) maximum = Math.max(maximum, latest.getVersionNo());
        }
        return Math.addExact(maximum, 1);
    }

    protected int nextBusinessVersion(String type, Long resourceId, GovernedResource resource) {
        Integer maximum = versionMapper == null ? null : versionMapper.maxBusinessVersion(type, resourceId);
        return Math.addExact(maximum == null ? (resource == null ? 0 : defaultVersionNo(resource)) : maximum, 1);
    }

    public boolean canReview(GovernanceApprovalRequest request, String actor) {
        return request != null
                && GovernanceRequestStatus.PENDING.name()
                .equals(request.getStatus())
                && actor != null
                && !actor.isBlank();
    }

    public List<GovernedResourceVersion> versions(String resourceType,
                                                  Long resourceId) {
        requireResourcePermission(resourceType, "view");
        if (versionMapper == null) {
            return List.of();
        }
        return versionMapper.selectList(new LambdaQueryWrapper<
                        GovernedResourceVersion>()
                        .select(GovernedResourceVersion.class, field -> !"secretPayloadCiphertext".equals(field.getProperty()))
                        .eq(GovernedResourceVersion::getResourceType,
                                normalizeType(resourceType))
                        .eq(GovernedResourceVersion::getResourceId,
                                resourceId)
                        .orderByDesc(
                                GovernedResourceVersion::getVersionNo));
    }

    public IPage<GovernanceRequestView> page(
            GovernanceApprovalQuery query) {
        return page(query, null);
    }

    public IPage<GovernanceRequestView> page(
            GovernanceApprovalQuery query, String actor) {
        GovernanceApprovalQuery safeQuery = query == null
                ? new GovernanceApprovalQuery() : query;
        int pageNum = Math.max(1, safeQuery.getPageNum());
        int pageSize = Math.min(100,
                Math.max(1, safeQuery.getPageSize()));
        LambdaQueryWrapper<GovernanceApprovalRequest> wrapper =
                new LambdaQueryWrapper<>();
        restrictVisibleResources(wrapper);
        String taskScope = hasText(safeQuery.getTaskScope())
                ? safeQuery.getTaskScope().trim()
                .toUpperCase(Locale.ROOT) : "ALL";
        boolean pendingScope = "PENDING".equals(taskScope);
        if (pendingScope) {
            wrapper.eq(GovernanceApprovalRequest::getStatus,
                    GovernanceRequestStatus.PENDING.name());
        } else if ("MINE".equals(taskScope)) {
            wrapper.eq(GovernanceApprovalRequest::getApplicant,
                    requireActor(actor));
        } else if ("COMPLETED".equals(taskScope)) {
            wrapper.in(GovernanceApprovalRequest::getStatus,
                    GovernanceRequestStatus.APPROVED.name(),
                    GovernanceRequestStatus.REJECTED.name(),
                    GovernanceRequestStatus.CONFLICT.name(),
                    GovernanceRequestStatus.CANCELLED.name());
        } else if (!"ALL".equals(taskScope)) {
            throw new IllegalArgumentException(
                    "不支持的审批任务视角: " + taskScope);
        }
        List<String> tabTypes = GovernanceResourceTypes.forTab(
                safeQuery.getTab());
        if (!tabTypes.isEmpty()) {
            wrapper.in(GovernanceApprovalRequest::getResourceType,
                    tabTypes);
        } else if (hasText(safeQuery.getResourceType())) {
            wrapper.eq(GovernanceApprovalRequest::getResourceType,
                    normalizeType(safeQuery.getResourceType()));
        }
        if (!pendingScope && hasText(safeQuery.getStatus())) {
            wrapper.eq(GovernanceApprovalRequest::getStatus,
                    safeQuery.getStatus().toUpperCase(Locale.ROOT));
        }
        if (hasText(safeQuery.getAction())) {
            wrapper.eq(GovernanceApprovalRequest::getAction,
                    safeQuery.getAction().toUpperCase(Locale.ROOT));
        }
        if (safeQuery.getProjectId() != null) {
            wrapper.eq(GovernanceApprovalRequest::getProjectId,
                    safeQuery.getProjectId());
        }
        if (hasText(safeQuery.getApplicant())) {
            wrapper.like(GovernanceApprovalRequest::getApplicant,
                    safeQuery.getApplicant().trim());
        }
        if (hasText(safeQuery.getKeyword())) {
            String keyword = safeQuery.getKeyword().trim();
            wrapper.and(value -> value
                    .like(GovernanceApprovalRequest::getRequestNo,
                            keyword)
                    .or()
                    .like(GovernanceApprovalRequest::getChangeSummary,
                            keyword));
        }
        wrapper.orderByDesc(GovernanceApprovalRequest::getCreateTime)
                .orderByDesc(GovernanceApprovalRequest::getId);
        IPage<GovernanceApprovalRequest> source =
                requestMapper.selectPage(
                        new Page<>(pageNum, pageSize), wrapper);
        Page<GovernanceRequestView> result =
                new Page<>(pageNum, pageSize, source.getTotal());
        result.setRecords(source.getRecords().stream()
                .map(GovernanceRequestView::from)
                .toList());
        return result;
    }

    public GovernanceApprovalSummary summary(
            Long projectId, String actor) {
        String applicant = requireActor(actor);
        LambdaQueryWrapper<GovernanceApprovalRequest> pending =
                projectScoped(projectId)
                        .eq(GovernanceApprovalRequest::getStatus,
                                GovernanceRequestStatus.PENDING.name());
        LambdaQueryWrapper<GovernanceApprovalRequest> myDrafts =
                projectScoped(projectId)
                        .eq(GovernanceApprovalRequest::getApplicant,
                                applicant)
                        .eq(GovernanceApprovalRequest::getStatus,
                                GovernanceRequestStatus.EDITING.name());
        LambdaQueryWrapper<GovernanceApprovalRequest> myRequests =
                projectScoped(projectId)
                        .eq(GovernanceApprovalRequest::getApplicant,
                                applicant);
        LambdaQueryWrapper<GovernanceApprovalRequest> completed =
                projectScoped(projectId)
                        .in(GovernanceApprovalRequest::getStatus,
                                GovernanceRequestStatus.APPROVED.name(),
                                GovernanceRequestStatus.REJECTED.name(),
                                GovernanceRequestStatus.CONFLICT.name(),
                                GovernanceRequestStatus.CANCELLED.name());
        return new GovernanceApprovalSummary(
                safeCount(pending),
                safeCount(myDrafts),
                safeCount(myRequests),
                safeCount(completed));
    }

    private LambdaQueryWrapper<GovernanceApprovalRequest> projectScoped(
            Long projectId) {
        LambdaQueryWrapper<GovernanceApprovalRequest> wrapper =
                new LambdaQueryWrapper<>();
        if (projectId != null) {
            if (projectId <= 0) {
                throw new IllegalArgumentException("项目 ID 必须为正整数");
            }
            wrapper.eq(GovernanceApprovalRequest::getProjectId, projectId);
        }
        restrictVisibleResources(wrapper);
        return wrapper;
    }

    private void requireResourcePermission(String type, String action) {
        if (accessGuard != null) {
            accessGuard.requireResource(type, "view");
            if (!"view".equals(action)) accessGuard.requireResource(type, action);
        }
    }

    private void restrictVisibleResources(LambdaQueryWrapper<GovernanceApprovalRequest> query) {
        if (accessGuard == null) return;
        List<String> types = List.of("VARIABLE", "DATA_OBJECT", "FIELD_VALIDATION", "MODEL", "EXTERNAL_DATASOURCE", "EXTERNAL_API", "DATABASE", "SFTP", "FUNCTION", "RULE", "RULE_PROJECT_BINDING", "LIST_LIBRARY", "LIST_RECORD_BATCH", "BILLING_CONFIG", "EXPERIMENT", "PROJECT");
        List<String> visible = types.stream().filter(type -> accessGuard.can(
                com.hengshucredit.rule.server.security.ConsoleAccessGuard.resourceModule(type) + ":view")).toList();
        if (visible.isEmpty()) query.eq(GovernanceApprovalRequest::getId, -1L);
        else query.in(GovernanceApprovalRequest::getResourceType, visible);
    }

    private long safeCount(
            LambdaQueryWrapper<GovernanceApprovalRequest> wrapper) {
        Long value = requestMapper.selectCount(wrapper);
        return value == null ? 0L : value;
    }

    public GovernanceRequestDetail detail(Long requestId) {
        GovernanceApprovalRequest request =
                requireRequest(requestId);
        requireResourcePermission(request.getResourceType(), "view");
        GovernedResourceVersion base =
                findVersion(request.getBaseVersionId());
        ResourceSnapshot left = base == null
                ? ResourceSnapshot.ofJson("{}")
                : new ResourceSnapshot(base.getSnapshotJson(),
                base.getEffectiveStatus(),
                base.getSecretPayloadCiphertext(),
                base.getSecretDigest());
        String rightJson =
                GovernanceRequestStatus.EDITING.name()
                        .equals(request.getStatus())
                        ? request.getDraftSnapshotJson()
                        : request.getSubmittedSnapshotJson();
        ResourceSnapshot right = new ResourceSnapshot(
                rightJson == null ? request.getDraftSnapshotJson()
                        : rightJson,
                requestedEffectiveStatus(request),
                request.getSecretPayloadCiphertext(),
                request.getSecretDigest());
        ResourceDiff diff = requireAdapter(
                request.getResourceType()).diff(left, right);
        return new GovernanceRequestDetail(
                GovernanceRequestView.from(request),
                diff,
                events(requestId),
                loadDependencies(requestId).stream().map(dependency -> dependencyDisplay == null
                        ? CanonicalJson.readMap(CanonicalJson.write(dependency)) : dependencyDisplay.describe(dependency)).toList(),
                versions(request.getResourceType(),
                        request.getResourceId()).stream()
                        .map(GovernanceVersionView::from)
                        .toList(),
                !safeEquals(base == null
                                ? null : base.getSecretDigest(),
                        request.getSecretDigest()));
    }

    public List<GovernanceApprovalEvent> events(Long requestId) {
        if (eventMapper == null) {
            return List.of();
        }
        return eventMapper.selectList(new LambdaQueryWrapper<
                        GovernanceApprovalEvent>()
                        .eq(GovernanceApprovalEvent::getRequestId,
                                requestId)
                        .orderByAsc(GovernanceApprovalEvent::getId));
    }

    protected GovernanceApprovalRequest requireRequest(Long requestId) {
        if (requestId == null) {
            throw new IllegalArgumentException("审批申请 ID 不能为空");
        }
        GovernanceApprovalRequest request = requestMapper == null
                ? null : requestMapper.selectById(requestId);
        if (request == null) {
            throw new IllegalArgumentException("审批申请不存在: " + requestId);
        }
        return request;
    }

    private void lockRule(String type, Long id) {
        if (ruleDraftDeletionService != null && GovernanceResourceTypes.RULE.equals(type) && id != null && id > 0) {
            ruleDraftDeletionService.lock(id);
        }
    }

    private GovernanceApprovalRequest requireMutableRequest(Long requestId) {
        GovernanceApprovalRequest request = requireRequest(requestId);
        lockRule(request.getResourceType(), request.getResourceId());
        if (request.getResourceId() != null && request.getResourceId() > 0) findResource(request.getResourceType(), request.getResourceId());
        // A current read after the rule lock observes a deletion/approval that committed while waiting.
        return requestMapper == null ? request : requestMapper.selectOne(new LambdaQueryWrapper<GovernanceApprovalRequest>()
                .eq(GovernanceApprovalRequest::getId, requestId).last("FOR UPDATE"));
    }

    protected GovernanceApprovalRequest insertRequest(
            GovernanceApprovalRequest request) {
        try {
            if (requestMapper == null
                    || requestMapper.insert(request) != 1) {
                throw new GovernanceStateException(
                        "GOVERNANCE_DRAFT_CREATE_FAILED",
                        "审批草稿创建失败");
            }
        } catch (DuplicateKeyException collision) {
            throw new GovernanceStateException(
                    "GOVERNANCE_REQUEST_ALREADY_ACTIVE",
                    "同一资源已有活动审批申请，请刷新后重试");
        }
        return request;
    }

    protected void persistRequest(GovernanceApprovalRequest request) {
        if (requestMapper == null || requestMapper.updateById(request) != 1) {
            throw new GovernanceStateException(
                    "APPROVAL_CONCURRENT_MODIFICATION",
                    "审批申请已发生变化，请刷新后重试");
        }
    }

    protected void appendEvent(GovernanceApprovalRequest request,
                               String action,
                               String fromStatus,
                               String actor,
                               String comment,
                               String detailsJson) {
        if (eventMapper == null) {
            return;
        }
        GovernanceApprovalEvent event = new GovernanceApprovalEvent();
        event.setRequestId(request.getId());
        event.setAction(action);
        event.setFromStatus(fromStatus);
        event.setToStatus(request.getStatus());
        event.setActor(requireActor(actor));
        event.setComment(comment);
        event.setDetailsJson(detailsJson);
        eventMapper.insert(event);
    }

    protected ResourceSnapshot normalize(GovernanceApprovalRequest request,
                                         String snapshotJson) {
        return requireAdapter(request.getResourceType()).normalizeDraft(
                new ResourceSnapshot(snapshotJson,
                        requestedEffectiveStatus(request),
                        request.getSecretPayloadCiphertext(),
                        request.getSecretDigest()));
    }

    protected GovernancePreflightReport evaluate(
            GovernanceApprovalRequest request,
            ResourceSnapshot snapshot) {
        GovernedResourceAdapter adapter =
                requireAdapter(request.getResourceType());
        List<GovernanceIssue> issues = new ArrayList<>(
                adapter.validate(snapshot, request.getAction()));
        if (impactService != null) {
            issues.addAll(impactService.analyze(
                    request.getResourceType(),
                    request.getResourceId(),
                    "RESTORE".equals(request.getAction()) ? "UPDATE" : request.getAction(),
                    snapshot));
        }
        return requireDependencyService().preflight(
                request.getResourceType(), request.getResourceId(),
                GovernanceAction.DELETE == normalizeAction(request.getAction())
                        ? List.of() : adapter.collectDependencies(snapshot),
                issues);
    }

    protected List<GovernancePreflightReport.ResolvedDependency>
    loadDependencies(Long requestId) {
        return requireDependencyService().load(requestId);
    }

    protected GovernedResource findResource(String resourceType,
                                            Long resourceId) {
        if (resourceMapper == null || resourceId == null) {
            return null;
        }
        GovernedResource resource = resourceMapper.selectOne(
                new LambdaQueryWrapper<
                        GovernedResource>()
                        .eq(GovernedResource::getResourceType,
                                normalizeType(resourceType))
                        .eq(GovernedResource::getResourceId, resourceId)
                        .last("LIMIT 1 FOR UPDATE"));
        if (resource == null && bootstrapService != null
                && resourceId > 0) {
            try {
                return bootstrapService.ensure(
                        resourceType, resourceId);
            } catch (IllegalArgumentException notFound) {
                return null;
            }
        }
        return resource;
    }

    protected GovernedResourceVersion findVersion(Long versionId) {
        return versionMapper == null || versionId == null
                ? null : versionMapper.selectById(versionId);
    }

    protected GovernanceApprovalRequest findActiveRequest(
            String activeResourceKey) {
        if (requestMapper == null || activeResourceKey == null) {
            return null;
        }
        return requestMapper.selectOne(new LambdaQueryWrapper<
                        GovernanceApprovalRequest>()
                .eq(GovernanceApprovalRequest::getActiveResourceKey,
                        activeResourceKey)
                .last("LIMIT 1 FOR UPDATE"));
    }

    protected GovernedResourceVersion persistVersion(
            GovernedResourceVersion version) {
        if (versionMapper == null || versionMapper.insert(version) != 1) {
            throw new GovernanceStateException(
                    "GOVERNANCE_VERSION_CREATE_FAILED",
                    "生效版本创建失败");
        }
        return version;
    }

    protected void insertResource(GovernedResource resource) {
        if (resourceMapper == null || resourceMapper.insert(resource) != 1) {
            throw new GovernanceStateException(
                    "GOVERNANCE_RESOURCE_CREATE_FAILED",
                    "治理资源创建失败");
        }
    }

    protected void persistResource(GovernedResource resource) {
        if (resourceMapper == null || resourceMapper.updateById(resource) != 1) {
            throw new GovernanceStateException(
                    "GOVERNANCE_RESOURCE_CONCURRENT_MODIFICATION",
                    "资源生效版本已发生变化，请刷新后重试");
        }
    }

    protected GovernedResourceAdapter requireAdapter(String resourceType) {
        if (adapterRegistry == null) {
            throw new IllegalStateException("治理资源适配器尚未初始化");
        }
        return adapterRegistry.require(resourceType);
    }

    protected void persistDependencies(GovernanceApprovalRequest request,
                                       GovernancePreflightReport report,
                                       Long versionId) {
        requireDependencyService().persist(request, report, versionId);
    }

    protected GovernancePreflightReport revalidateDependencies(
            List<GovernancePreflightReport.ResolvedDependency> submitted) {
        return requireDependencyService().revalidate(submitted);
    }

    protected void bindDependencyVersion(Long requestId, Long versionId) {
        requireDependencyService().bindVersion(requestId, versionId);
    }

    private GovernanceDependencyService requireDependencyService() {
        if (dependencyService == null) {
            throw new IllegalStateException("治理依赖服务尚未初始化");
        }
        return dependencyService;
    }

    private GovernanceApprovalRequest markConflict(
            GovernanceApprovalRequest request,
            String actor,
            GovernanceReviewRequest review,
            String conflictCode,
            String conflictMessage) {
        terminateAdapterWorkflow(request, actor, conflictMessage,
                GovernanceRequestStatus.CONFLICT.name());
        String fromStatus = request.getStatus();
        request.setStatus(GovernanceRequestStatus.CONFLICT.name());
        request.setActiveResourceKey(null);
        request.setReviewer(requireActor(actor));
        request.setReviewComment(conflictMessage);
        request.setReviewTime(LocalDateTime.now());
        persistRequest(request);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("conflictCode", conflictCode);
        details.put("requestedComment",
                review == null ? null : review.getComment());
        appendEvent(request, "CONFLICT", fromStatus, actor,
                conflictMessage, JSON.toJSONString(details));
        return request;
    }

    private void terminateAdapterWorkflow(
            GovernanceApprovalRequest request,
            String actor,
            String comment,
            String terminalStatus) {
        if (request == null || request.getPublishMode() != null && !"RULE".equals(request.getResourceType())) {
            return;
        }
        if (isCreateRequest(request)) {
            String snapshotJson = firstNotBlank(
                    request.getSubmittedSnapshotJson(),
                    request.getDraftSnapshotJson());
            if (snapshotJson == null) return;
            requireAdapter(request.getResourceType())
                    .onCreateApprovalTerminated(
                            new ResourceSnapshot(snapshotJson,
                                    requestedEffectiveStatus(request),
                                    request.getSecretPayloadCiphertext(),
                                    request.getSecretDigest()),
                            requireActor(actor), comment,
                            terminalStatus);
            return;
        }
        GovernedResource current = findResource(
                request.getResourceType(), request.getResourceId());
        Long effectiveVersionId = current == null
                || current.getEffectiveVersionId() == null
                ? request.getBaseVersionId()
                : current.getEffectiveVersionId();
        GovernedResourceVersion effective =
                findVersion(effectiveVersionId);
        ResourceSnapshot effectiveSnapshot = effective == null
                ? null : new ResourceSnapshot(
                effective.getSnapshotJson(),
                effective.getEffectiveStatus(),
                effective.getSecretPayloadCiphertext(),
                effective.getSecretDigest());
        requireAdapter(request.getResourceType())
                .onApprovalTerminated(request.getResourceId(),
                        effectiveSnapshot,
                        requireActor(actor), comment, terminalStatus, request.getId());
    }

    private boolean hasBaseConflict(GovernanceApprovalRequest request,
                                    GovernedResource resource) {
        if (request.getPublishMode() != null) {
            if (resource == null || "DELETED".equals(resource.getEffectiveStatus())) return true;
            if ("NEW".equals(request.getPublishMode())) return false;
            try {
                return !Objects.equals(request.getExpectedTargetSnapshotId(),
                        versionHead(request.getResourceType(), request.getResourceId(), request.getTargetVersionId()).getId());
            } catch (IllegalArgumentException deleted) { return true; }
        }
        if (isCreateRequest(request)) {
            return resource != null;
        }
        return resource == null
                || !safeEquals(request.getBaseVersionId(),
                resource.getEffectiveVersionId());
    }

    private boolean isCreateRequest(GovernanceApprovalRequest request) {
        return GovernanceAction.CREATE.name().equals(request.getAction())
                && Long.valueOf(0L).equals(request.getResourceId());
    }

    public GovernedResourceVersion versionHead(String type, Long resourceId, Long anchorId) {
        GovernedResourceVersion anchor = findVersion(anchorId);
        if (anchor == null || !normalizeType(type).equals(anchor.getResourceType()) || !Objects.equals(resourceId, anchor.getResourceId()))
            throw new IllegalArgumentException("指定版本不存在或不属于当前资源");
        List<GovernedResourceVersion> history = versionMapper == null ? versions(type, resourceId)
                : versionMapper.selectList(new LambdaQueryWrapper<GovernedResourceVersion>()
                        .select(GovernedResourceVersion.class, field -> !"secretPayloadCiphertext".equals(field.getProperty()))
                        .eq(GovernedResourceVersion::getResourceType, normalizeType(type))
                        .eq(GovernedResourceVersion::getResourceId, resourceId).last("FOR UPDATE"));
        return ResourceVersionLines.head(history, anchor);
    }

    private boolean isTerminalRequest(GovernanceApprovalRequest request) {
        try {
            return request != null
                    && GovernanceRequestStatus.valueOf(
                    request.getStatus()).isTerminal();
        } catch (RuntimeException invalidStatus) {
            return false;
        }
    }

    protected String requestedEffectiveStatus(
            GovernanceApprovalRequest request) {
        GovernanceAction action = normalizeAction(request.getAction());
        return switch (action) {
            case ENABLE, CREATE, RESTORE -> "ACTIVE";
            case DISABLE -> "DISABLED";
            case DELETE -> "DELETED";
            case UPDATE -> {
                String snapshotJson =
                        GovernanceRequestStatus.PENDING.name()
                                .equals(request.getStatus())
                                ? request.getSubmittedSnapshotJson()
                                : request.getDraftSnapshotJson();
                String snapshotStatus = effectiveStatusFromSnapshot(
                        snapshotJson);
                if (snapshotStatus != null) {
                    yield snapshotStatus;
                }
                GovernedResource current = findResource(
                        request.getResourceType(), request.getResourceId());
                yield current == null
                        || current.getEffectiveStatus() == null
                        ? "ACTIVE" : current.getEffectiveStatus();
            }
        };
    }

    private String effectiveStatusFromSnapshot(String snapshotJson) {
        if (snapshotJson == null || snapshotJson.isBlank()) {
            return null;
        }
        Object status = CanonicalJson.readMap(snapshotJson).get("status");
        if (status instanceof Number number) {
            return number.intValue() == 0 ? "DISABLED" : "ACTIVE";
        }
        if (status == null) {
            return null;
        }
        String text = String.valueOf(status).trim()
                .toUpperCase(Locale.ROOT);
        return switch (text) {
            case "0", "FALSE", "DISABLED", "OFFLINE" -> "DISABLED";
            case "1", "TRUE", "ACTIVE", "ENABLED", "PUBLISHED" ->
                    "ACTIVE";
            default -> null;
        };
    }

    private ResourceSnapshot requestSnapshot(
            GovernanceApprovalRequest request) {
        String snapshotJson =
                GovernanceRequestStatus.PENDING.name()
                        .equals(request.getStatus())
                        ? request.getSubmittedSnapshotJson()
                        : request.getDraftSnapshotJson();
        return normalize(request, snapshotJson);
    }

    private void validateDraftTarget(GovernanceAction action,
                                     Long resourceId,
                                     GovernedResource resource) {
        if (action == GovernanceAction.CREATE) {
            if (resource != null && !isWorkspace(resource)) {
                throw new IllegalArgumentException("待创建资源已存在");
            }
            return;
        }
        if (resourceId == null || resource == null
                || resource.getEffectiveVersionId() == null) {
            throw new IllegalArgumentException(
                    "生命周期操作要求资源存在且已有生效版本");
        }
    }

    private boolean isWorkspace(GovernedResource resource) {
        return resource != null && resource.getEffectiveVersionId() == null && "DRAFT".equals(resource.getEffectiveStatus());
    }

    private ResourceSnapshot saveWorkspace(GovernanceApprovalRequest request, ResourceSnapshot snapshot, String actor) {
        GovernedResourceAdapter adapter = requireAdapter(request.getResourceType());
        if (!"CREATE".equals(request.getAction()) || !adapter.supportsDraftWorkspace()) return snapshot;
        Long id = request.getResourceId();
        boolean create = id == null || id <= 0;
        GovernedResource resource = create ? null : findResource(request.getResourceType(), id);
        if (!create && !isWorkspace(resource)) return snapshot;
        checkDraftDependencies(request, snapshot);
        AppliedResource applied = adapter.apply(new ApprovalApplyContext(request.getId(), create ? null : id, 0,
                create ? "DRAFT_CREATE" : "DRAFT_UPDATE", snapshot, actor, null));
        if (create) {
            resource = new GovernedResource();
            resource.setResourceType(request.getResourceType());
            resource.setResourceId(requireAppliedResourceId(applied));
            resource.setProjectId("PROJECT".equals(request.getResourceType()) ? applied.resourceId() : request.getProjectId());
            resource.setEffectiveStatus("DRAFT");
            resource.setEffectiveVersionNo(0);
            insertResource(resource);
            request.setResourceId(applied.resourceId());
            request.setActiveResourceKey(request.getResourceType() + ":" + applied.resourceId());
        }
        ResourceSnapshot stored = adapter.loadEffective(applied.resourceId());
        Map<String, Object> saved = CanonicalJson.readMap(stored.snapshotJson());
        Map<String, Object> submitted = CanonicalJson.readMap(snapshot.snapshotJson());
        // status=0 isolates the workspace projection; the approval must still show the requested configuration.
        if (submitted.containsKey("status")) saved.put("status", submitted.get("status"));
        return new ResourceSnapshot(CanonicalJson.write(saved), snapshot.effectiveStatus(),
                stored.secretPayloadCiphertext(), stored.secretDigest());
    }

    private GovernancePreflightReport checkDraftDependencies(GovernanceApprovalRequest request, ResourceSnapshot snapshot) {
        if (dependencyService == null || Set.of("DELETE", "DISABLE").contains(request.getAction())) return null;
        GovernancePreflightReport report = dependencyService.preflight(request.getResourceType(), request.getResourceId(),
                requireAdapter(request.getResourceType()).collectDependencies(snapshot), List.of());
        if (!report.valid()) throw new GovernanceStateException("GOVERNANCE_DEPENDENCY_NOT_PUBLISHED", firstErrorMessage(report));
        return report;
    }

    private void saveDraftDependencies(GovernanceApprovalRequest request, ResourceSnapshot snapshot) {
        GovernancePreflightReport report = checkDraftDependencies(request, snapshot);
        if (report != null) persistDependencies(request, report, null);
    }

    private int defaultVersionNo(GovernedResource resource) {
        return resource.getEffectiveVersionNo() == null
                ? 0 : resource.getEffectiveVersionNo();
    }

    private Long requireAppliedResourceId(AppliedResource applied) {
        if (applied == null || applied.resourceId() == null) {
            throw new GovernanceStateException(
                    "GOVERNANCE_APPLY_FAILED",
                    "适配器没有返回业务资源 ID");
        }
        return applied.resourceId();
    }

    private String reportJson(GovernancePreflightReport report) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("valid", report.valid());
        value.put("errors", report.errors());
        value.put("warnings", report.warnings());
        value.put("dependencies", report.dependencies());
        value.put("dependencyDigest", report.dependencyDigest());
        return JSON.toJSONString(value);
    }

    private String firstErrorMessage(GovernancePreflightReport report) {
        return report.errors().isEmpty()
                ? "依赖预检未通过"
                : report.errors().get(0).message();
    }

    private void requireStatus(GovernanceApprovalRequest request,
                               GovernanceRequestStatus expected) {
        if (!expected.name().equals(request.getStatus())) {
            throw stateConflict(request, "执行此操作");
        }
    }

    private void requireTransition(GovernanceApprovalRequest request,
                                   GovernanceRequestStatus target) {
        GovernanceRequestStatus current;
        try {
            current = GovernanceRequestStatus.valueOf(request.getStatus());
        } catch (RuntimeException e) {
            throw new GovernanceStateException(
                    "APPROVAL_STATE_CONFLICT", "审批申请状态无效");
        }
        if (!current.canTransitionTo(target)) {
            throw stateConflict(request, "变更为 " + target.name());
        }
    }

    private GovernanceStateException stateConflict(
            GovernanceApprovalRequest request, String action) {
        return new GovernanceStateException(
                "APPROVAL_STATE_CONFLICT",
                "审批申请当前状态为 " + request.getStatus()
                        + "，不能" + action);
    }

    private String requireActor(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new GovernanceStateException(
                    "APPROVAL_OPERATOR_REQUIRED", "操作人不能为空");
        }
        return actor;
    }

    private void requireApplicant(GovernanceApprovalRequest request,
                                  String actor) {
        String operator = requireActor(actor);
        if (!operator.equals(request.getApplicant())) {
            String requestReference = request.getRequestNo() == null
                    || request.getRequestNo().isBlank()
                    ? "#" + request.getId()
                    : request.getRequestNo();
            throw new GovernanceStateException(
                    "GOVERNANCE_DRAFT_OWNED_BY_ANOTHER",
                    "审批申请 " + requestReference + " 由用户 "
                            + request.getApplicant()
                            + " 创建，请由申请人本人修改、提交或撤回");
        }
    }

    private String createActiveResourceKey(
            String resourceType,
            Long projectId,
            ResourceSnapshot snapshot) {
        Map<String, Object> value =
                CanonicalJson.readMap(snapshot.snapshotJson());
        if (GovernanceResourceTypes.RULE_PROJECT_BINDING.equals(
                resourceType)) {
            Long definitionId = requirePositiveId(
                    value.get("definitionId"), "全局规则 ID");
            Long bindingProjectId = requirePositiveId(
                    value.get("projectId"), "项目 ID");
            return resourceType + ":CREATE:"
                    + definitionId + ":" + bindingProjectId;
        }
        Map<String, Object> identity = new LinkedHashMap<>();
        switch (resourceType) {
            case GovernanceResourceTypes.VARIABLE ->
                    addScopedIdentity(identity, value, projectId,
                            "varCode");
            case GovernanceResourceTypes.DATA_OBJECT ->
                    addScopedIdentity(identity, value, projectId,
                            "objectCode");
            case GovernanceResourceTypes.EXTERNAL_DATASOURCE,
                 GovernanceResourceTypes.DATABASE,
                 GovernanceResourceTypes.SFTP ->
                    addScopedIdentity(identity, value, projectId,
                            "datasourceCode");
            case GovernanceResourceTypes.FUNCTION ->
                    addScopedIdentity(identity, value, projectId,
                            "funcCode");
            case GovernanceResourceTypes.LIST_LIBRARY ->
                    addScopedIdentity(identity, value, projectId,
                            "listCode");
            case GovernanceResourceTypes.FIELD_VALIDATION ->
                    addScopedIdentity(identity, value, projectId,
                            "validationCode");
            case GovernanceResourceTypes.BILLING_CONFIG ->
                    addScopedIdentity(identity, value, projectId,
                            "billingCode");
            case GovernanceResourceTypes.EXTERNAL_API -> {
                addIdentity(identity, "datasourceId",
                        value.get("datasourceId"));
                addIdentity(identity, "apiCode", value.get("apiCode"));
            }
            case GovernanceResourceTypes.MODEL ->
                    addIdentity(identity, "modelCode",
                            value.get("modelCode"));
            case GovernanceResourceTypes.RULE ->
                    addIdentity(identity, "ruleCode",
                            value.get("ruleCode"));
            case GovernanceResourceTypes.EXPERIMENT ->
                    addIdentity(identity, "experimentCode",
                            value.get("experimentCode"));
            case GovernanceResourceTypes.PROJECT ->
                    addIdentity(identity, "projectCode",
                            value.get("projectCode"));
            default -> identity.put(
                    "snapshotDigest",
                    Sha256Digests.text(snapshot.snapshotJson()));
        }
        return resourceType + ":CREATE:"
                + Sha256Digests.text(CanonicalJson.write(identity));
    }

    private void addScopedIdentity(Map<String, Object> identity,
                                   Map<String, Object> snapshot,
                                   Long projectId,
                                   String codeField) {
        Object snapshotProjectId = snapshot.get("projectId");
        addIdentity(identity, "scope",
                firstNotBlank(stringValue(snapshot.get("scope")),
                        "PROJECT"));
        addIdentity(identity, "projectId",
                snapshotProjectId == null ? projectId : snapshotProjectId);
        addIdentity(identity, codeField, snapshot.get(codeField));
    }

    private void addIdentity(Map<String, Object> identity,
                             String key,
                             Object value) {
        identity.put(key, stringValue(value).trim()
                .toLowerCase(Locale.ROOT));
    }

    private Long requirePositiveId(Object value, String label) {
        try {
            Long id = value instanceof Number number
                    ? number.longValue()
                    : Long.valueOf(String.valueOf(value));
            if (id > 0) {
                return id;
            }
        } catch (RuntimeException invalid) {
            // Fall through to the stable validation error below.
        }
        throw new IllegalArgumentException(label + "必须是大于零的数值 ID");
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String normalizeType(String resourceType) {
        if (resourceType == null || resourceType.isBlank()) {
            throw new IllegalArgumentException("资源类型不能为空");
        }
        return resourceType.trim().toUpperCase(Locale.ROOT);
    }

    private GovernanceAction normalizeAction(String action) {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("生命周期动作不能为空");
        }
        try {
            return GovernanceAction.valueOf(
                    action.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "不支持的生命周期动作: " + action);
        }
    }

    private String newRequestNo() {
        return "GOV-"
                + LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + UUID.randomUUID().toString()
                .substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private boolean safeEquals(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private String firstNotBlank(String preferred,
                                 String fallback) {
        return preferred == null || preferred.isBlank()
                ? fallback : preferred;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public static class GovernanceStateException
            extends IllegalStateException {
        private final String code;

        public GovernanceStateException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }
}
