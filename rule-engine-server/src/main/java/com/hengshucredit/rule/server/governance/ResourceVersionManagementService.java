package com.hengshucredit.rule.server.governance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hengshucredit.rule.model.dto.GovernanceDraftRequest;
import com.hengshucredit.rule.model.entity.*;
import com.hengshucredit.rule.server.mapper.*;
import com.hengshucredit.rule.server.security.ConsoleAccessGuard;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Shared version workspace; module-specific validation and publication stay in their adapters. */
@Service
public class ResourceVersionManagementService {
    @Resource private GovernanceApprovalService approvals;
    @Resource private GovernanceResourceBootstrapService bootstrap;
    @Resource private GovernedResourceMapper resources;
    @Resource private GovernedResourceVersionMapper versions;
    @Resource private GovernanceApprovalRequestMapper requests;
    @Resource private GovernanceApprovalEventMapper events;
    @Resource private RuleVersionBindingMapper ruleBindings;
    @Resource private ResourceVersionImpactService versionImpact;
    @Resource private ConsoleAccessGuard access;

    public Map<String, Object> workspace(String type, Long id, String actor) {
        access.requireResource(type, "view");
        GovernedResource resource = bootstrap.ensure(type, id);
        List<GovernedResourceVersion> history = approvals.versions(type, id);
        List<Map<String, Object>> heads = new ArrayList<>();
        for (var head : ResourceVersionLines.heads(history)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("versionId", head.getId()); row.put("version", head.getDisplayVersion());
            row.put("revision", head.getVersionNo()); row.put("deleted", Boolean.TRUE.equals(head.getDeleted()));
            row.put("latest", ResourceVersionLines.published(head) && Objects.equals(head.getId(), resource.getEffectiveVersionId()));
            row.put("status", head.getEffectiveStatus()); row.put("changeSummary", head.getChangeSummary());
            row.put("createBy", head.getCreateBy()); row.put("createTime", head.getCreateTime()); heads.add(row);
        }
        List<GovernanceApprovalRequest> drafts = requests.selectList(new LambdaQueryWrapper<GovernanceApprovalRequest>()
                .select(GovernanceApprovalRequest.class, field -> !"secretPayloadCiphertext".equals(field.getProperty()))
                .eq(GovernanceApprovalRequest::getResourceType, type).eq(GovernanceApprovalRequest::getResourceId, id)
                .in(GovernanceApprovalRequest::getStatus, "EDITING", "PENDING", "CONFLICT", "REJECTED")
                .orderByDesc(GovernanceApprovalRequest::getId));
        // Viewing a resource does not grant access to another applicant's unpublished content.
        Set<String> actorNames = approvals.actorNames(actor);
        if (!access.can("approval:view")) drafts = drafts.stream().filter(draft -> draft.getApplicant() != null && actorNames.contains(draft.getApplicant())).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        Long latestId = history.stream().filter(ResourceVersionLines::published)
                .filter(version -> Objects.equals(version.getId(), resource.getEffectiveVersionId())).map(GovernedResourceVersion::getId).findFirst().orElse(null);
        result.put("latestVersionId", latestId); result.put("versions", heads);
        result.put("history", history.stream().map(GovernanceVersionView::from).toList());
        result.put("drafts", drafts.stream().map(GovernanceRequestView::from).toList());
        result.put("actor", actor);
        result.put("actorNames", actorNames);
        result.put("projectId", resource.getProjectId());
        result.put("scope", resource.getProjectId() != null && resource.getProjectId() > 0 ? "PROJECT" : "GLOBAL");
        return result;
    }

    public List<GovernanceApprovalEvent> history(String type, Long id) {
        access.requireResource(type, "view");
        List<Long> ids = requests.selectList(new LambdaQueryWrapper<GovernanceApprovalRequest>()
                .select(GovernanceApprovalRequest::getId)
                .eq(GovernanceApprovalRequest::getResourceType, type).eq(GovernanceApprovalRequest::getResourceId, id))
                .stream().map(GovernanceApprovalRequest::getId).toList();
        return ids.isEmpty() ? List.of() : events.selectList(new LambdaQueryWrapper<GovernanceApprovalEvent>()
                .in(GovernanceApprovalEvent::getRequestId, ids).orderByDesc(GovernanceApprovalEvent::getId));
    }

    @Transactional
    public GovernanceRequestView create(String type, Long id, GovernanceDraftRequest draft, String actor) {
        access.requireResource(type, "edit");
        var resource = bootstrap.ensure(type, id);
        Long sourceId = draft.getSourceVersionId() == null ? resource.getEffectiveVersionId() : draft.getSourceVersionId();
        var source = versions.selectById(sourceId);
        if (source == null || !type.equals(source.getResourceType()) || !id.equals(source.getResourceId()))
            throw new IllegalArgumentException("请选择当前资源的有效来源版本");
        if (Boolean.TRUE.equals(source.getDeleted())) throw new IllegalArgumentException("已删除版本不能创建新草稿");
        draft.setResourceType(type); draft.setResourceId(id); draft.setProjectId(resource.getProjectId());
        draft.setAction("RESTORE"); draft.setRestoreMode("NEW"); draft.setSourceVersionId(sourceId);
        if (draft.getPublishMode() == null) draft.setPublishMode("NEW");
        // Source credentials are restored server-side; clients only edit the sanitized snapshot.
        draft.setSecretPayloadCiphertext(source.getSecretPayloadCiphertext()); draft.setSecretDigest(source.getSecretDigest());
        if (draft.getSnapshotJson() == null) draft.setSnapshotJson(source.getSnapshotJson());
        return GovernanceRequestView.from(approvals.createDraft(draft, actor));
    }

    @Transactional
    public void deleteVersion(String type, Long id, Long versionId, Long expectedSnapshotId, String actor) {
        access.requireResource(type, "edit");
        var resource = resources.selectOne(new LambdaQueryWrapper<GovernedResource>()
                .eq(GovernedResource::getResourceType, type).eq(GovernedResource::getResourceId, id).last("FOR UPDATE"));
        var head = approvals.versionHead(type, id, versionId);
        if (!Objects.equals(head.getId(), expectedSnapshotId)) throw new IllegalArgumentException("版本已更新，请刷新后重新删除");
        var history = approvals.versions(type, id);
        var blockers = deletionIssues(resource, head, history);
        if (blockers.stream().anyMatch(GovernanceIssue::isError)) throw new IllegalArgumentException(blockers.stream().filter(GovernanceIssue::isError).findFirst().orElseThrow().message());
        List<Long> revisionIds = history.stream()
                .filter(version -> Objects.equals(head.getDisplayVersion(), version.getDisplayVersion())).map(GovernedResourceVersion::getId).toList();
        versions.update(null, new LambdaUpdateWrapper<GovernedResourceVersion>().in(GovernedResourceVersion::getId, revisionIds)
                .set(GovernedResourceVersion::getDeleted, true));
        if ("RULE".equals(type)) ruleBindings.update(null, new LambdaUpdateWrapper<RuleVersionBinding>()
                .eq(RuleVersionBinding::getDefinitionId, id).eq(RuleVersionBinding::getVersionNo, head.getDisplayVersion()).set(RuleVersionBinding::getStatus, 0));
        if (head.getApprovalRequestId() != null) {
            var event = new GovernanceApprovalEvent(); event.setRequestId(head.getApprovalRequestId());
            event.setAction("DELETE_VERSION"); event.setActor(actor); event.setComment("删除业务版本 v" + head.getDisplayVersion());
            event.setFromStatus("ACTIVE"); event.setToStatus("DELETED");
            event.setDetailsJson("{\"versionId\":" + head.getId() + "}"); events.insert(event);
        }
    }

    public Map<String, Object> deletionImpact(String type, Long id, Long versionId) {
        access.requireResource(type, "edit");
        var resource = resources.selectOne(new LambdaQueryWrapper<GovernedResource>()
                .eq(GovernedResource::getResourceType, type).eq(GovernedResource::getResourceId, id));
        var head = approvals.versionHead(type, id, versionId);
        var blockers = deletionIssues(resource, head, approvals.versions(type, id));
        return Map.of("allowed", blockers.stream().noneMatch(GovernanceIssue::isError), "issues", blockers, "expectedSnapshotId", head.getId());
    }

    private List<GovernanceIssue> deletionIssues(GovernedResource resource, GovernedResourceVersion head, List<GovernedResourceVersion> history) {
        if (resource == null || Objects.equals(head.getId(), resource.getEffectiveVersionId()))
            return List.of(GovernanceIssue.error("CURRENT_PRODUCTION_VERSION", "当前生产最新版不能删除，请先发布替代版本", head.getResourceType(), head.getResourceId(), "$"));
        return versionImpact.analyze(head, history);
    }
}
