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
import java.util.UUID;

@Service
public class GovernanceApprovalService {

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

    @Transactional
    public GovernanceApprovalRequest createDraft(GovernanceDraftRequest draft,
                                                 String actor) {
        if (draft == null) {
            throw new IllegalArgumentException("审批草稿不能为空");
        }
        String applicant = requireActor(actor);
        String resourceType = normalizeType(draft.getResourceType());
        GovernanceAction action = normalizeAction(draft.getAction());
        GovernedResourceAdapter adapter = requireAdapter(resourceType);
        GovernedResource resource = draft.getResourceId() == null
                ? null : findResource(resourceType, draft.getResourceId());
        validateDraftTarget(action, draft.getResourceId(), resource);

        String activeResourceKey = resource == null
                ? null : resourceType + ":" + resource.getResourceId();
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
        }
        if (active != null
                && !action.name().equals(active.getAction())) {
            throw new GovernanceStateException(
                    "GOVERNANCE_ACTION_CONFLICT",
                    "当前资源已有其他生命周期动作的编辑中申请，请先取消该申请");
        }
        if (active != null) {
            active.setDraftSnapshotJson(normalized.snapshotJson());
            active.setSnapshotDigest(
                    Sha256Digests.text(normalized.snapshotJson()));
            active.setSecretPayloadCiphertext(
                    normalized.secretPayloadCiphertext());
            active.setSecretDigest(normalized.secretDigest());
            active.setChangeSummary(draft.getChangeSummary());
            persistRequest(active);
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
        request.setDraftSnapshotJson(normalized.snapshotJson());
        request.setSnapshotDigest(
                Sha256Digests.text(normalized.snapshotJson()));
        request.setSecretPayloadCiphertext(
                normalized.secretPayloadCiphertext());
        request.setSecretDigest(normalized.secretDigest());
        request.setChangeSummary(draft.getChangeSummary());
        request.setApplicant(applicant);
        insertRequest(request);
        appendEvent(request, "CREATE_DRAFT", null, actor,
                draft.getChangeSummary(), null);
        return request;
    }

    @Transactional
    public GovernanceApprovalRequest saveDraft(Long requestId,
                                               GovernanceDraftRequest draft,
                                               String actor) {
        GovernanceApprovalRequest request = requireRequest(requestId);
        requireStatus(request, GovernanceRequestStatus.EDITING);
        requireApplicant(request, actor);
        String snapshotJson = draft.getSnapshotJson();
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
        request.setDraftSnapshotJson(normalized.snapshotJson());
        request.setSnapshotDigest(
                Sha256Digests.text(normalized.snapshotJson()));
        request.setSecretPayloadCiphertext(
                normalized.secretPayloadCiphertext());
        request.setSecretDigest(normalized.secretDigest());
        request.setChangeSummary(draft.getChangeSummary());
        persistRequest(request);
        appendEvent(request, "SAVE_DRAFT", request.getStatus(), actor,
                draft.getChangeSummary(), null);
        return request;
    }

    @Transactional
    public GovernanceApprovalRequest saveDraftSnapshot(Long requestId,
                                                       String snapshotJson,
                                                       String actor) {
        GovernanceApprovalRequest request = requireRequest(requestId);
        requireStatus(request, GovernanceRequestStatus.EDITING);
        requireApplicant(request, actor);
        ResourceSnapshot normalized = normalize(request, snapshotJson);
        request.setDraftSnapshotJson(normalized.snapshotJson());
        request.setSecretPayloadCiphertext(
                normalized.secretPayloadCiphertext());
        request.setSecretDigest(normalized.secretDigest());
        request.setSnapshotDigest(
                Sha256Digests.text(normalized.snapshotJson()));
        persistRequest(request);
        appendEvent(request, "SAVE_DRAFT", request.getStatus(), actor,
                null, null);
        return request;
    }

    @Transactional
    public GovernancePreflightReport preflight(Long requestId,
                                               String actor) {
        GovernanceApprovalRequest request = requireRequest(requestId);
        requireStatus(request, GovernanceRequestStatus.EDITING);
        requireApplicant(request, actor);
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
        GovernanceApprovalRequest request = requireRequest(requestId);
        requireStatus(request, GovernanceRequestStatus.EDITING);
        requireApplicant(request, actor);
        ResourceSnapshot normalized = normalize(request,
                request.getDraftSnapshotJson());
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
        GovernanceApprovalRequest request = requireRequest(requestId);
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

        List<GovernancePreflightReport.ResolvedDependency> submitted =
                loadDependencies(requestId);
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
                || !safeEquals(request.getDependencyDigest(),
                currentReport.dependencyDigest())) {
            return markConflict(request, actor, review,
                    "PREFLIGHT_CHANGED",
                    currentReport.valid()
                            ? "依赖快照摘要已变化，请重新发起审批"
                            : firstErrorMessage(currentReport));
        }

        int nextVersionNo = resource == null
                ? 1 : defaultVersionNo(resource) + 1;
        AppliedResource applied;
        try {
            applied = requireAdapter(
                    request.getResourceType()).apply(
                    new ApprovalApplyContext(
                            request.getId(),
                            isCreateRequest(request)
                                    ? null : request.getResourceId(),
                            nextVersionNo,
                            request.getAction(),
                            snapshot,
                            actor,
                            request.getSourceVersionId()));
        } catch (DuplicateKeyException collision) {
            // A participating resource transaction may already be rollback-only.
            // Preserve the original failure and roll back the entire approval atomically.
            throw collision;
        }
        Long appliedResourceId = requireAppliedResourceId(applied);

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

        GovernedResourceVersion version = new GovernedResourceVersion();
        version.setGovernedResourceId(resource.getId());
        version.setResourceType(request.getResourceType());
        version.setResourceId(appliedResourceId);
        version.setVersionNo(nextVersionNo);
        version.setSourceVersionId(request.getSourceVersionId());
        version.setApprovalRequestId(request.getId());
        version.setSnapshotJson(snapshot.snapshotJson());
        version.setSnapshotDigest(request.getSnapshotDigest());
        version.setSecretPayloadCiphertext(
                snapshot.secretPayloadCiphertext());
        version.setSecretDigest(snapshot.secretDigest());
        version.setEffectiveStatus(applied.effectiveStatus() == null
                ? snapshot.effectiveStatus() : applied.effectiveStatus());
        version.setChangeSummary(request.getChangeSummary());
        version.setCreateBy(actor);
        persistVersion(version);

        resource.setEffectiveVersionId(version.getId());
        resource.setEffectiveVersionNo(nextVersionNo);
        resource.setEffectiveStatus(version.getEffectiveStatus());
        persistResource(resource);
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
        details.put("versionNo", version.getVersionNo());
        details.put("sourceVersionId", version.getSourceVersionId());
        details.put("artifactId", applied.artifactId());
        appendEvent(request, "APPROVE", fromStatus, actor,
                request.getReviewComment(), JSON.toJSONString(details));
        return request;
    }

    @Transactional
    public GovernanceApprovalRequest reject(Long requestId,
                                            GovernanceReviewRequest review,
                                            String actor) {
        GovernanceApprovalRequest request = requireRequest(requestId);
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
        GovernanceApprovalRequest request = requireRequest(requestId);
        requireTransition(request, GovernanceRequestStatus.CANCELLED);
        requireApplicant(request, actor);
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

    @Transactional
    public GovernanceApprovalRequest createRestoreDraft(
            String resourceType,
            Long resourceId,
            Long sourceVersionId,
            String actor) {
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
        draft.setChangeSummary("恢复至历史版本 V" + source.getVersionNo());
        return createDraft(draft, actor);
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
        if (versionMapper == null) {
            return List.of();
        }
        return versionMapper.selectList(new LambdaQueryWrapper<
                        GovernedResourceVersion>()
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
        return wrapper;
    }

    private long safeCount(
            LambdaQueryWrapper<GovernanceApprovalRequest> wrapper) {
        Long value = requestMapper.selectCount(wrapper);
        return value == null ? 0L : value;
    }

    public GovernanceRequestDetail detail(Long requestId) {
        GovernanceApprovalRequest request =
                requireRequest(requestId);
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
                loadDependencies(requestId),
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
                    request.getAction(),
                    snapshot));
        }
        return requireDependencyService().preflight(
                request.getResourceType(), request.getResourceId(),
                adapter.collectDependencies(snapshot),
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
                        .last("LIMIT 1"));
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
                .last("LIMIT 1"));
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
        if (request == null) {
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
                        requireActor(actor), comment, terminalStatus);
    }

    private boolean hasBaseConflict(GovernanceApprovalRequest request,
                                    GovernedResource resource) {
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
            if (resource != null) {
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
                 GovernanceResourceTypes.DATABASE ->
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
