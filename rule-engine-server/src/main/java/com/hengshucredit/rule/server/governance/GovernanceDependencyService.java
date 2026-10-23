package com.hengshucredit.rule.server.governance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hengshucredit.rule.model.entity.GovernanceApprovalRequest;
import com.hengshucredit.rule.model.entity.GovernanceDependencySnapshot;
import com.hengshucredit.rule.model.entity.GovernedResource;
import com.hengshucredit.rule.model.entity.GovernedResourceVersion;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import com.hengshucredit.rule.server.mapper.GovernanceDependencySnapshotMapper;
import com.hengshucredit.rule.server.mapper.GovernedResourceMapper;
import com.hengshucredit.rule.server.mapper.GovernedResourceVersionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class GovernanceDependencyService {
    @jakarta.annotation.Resource private com.hengshucredit.rule.server.security.ConsoleProjectAccess consoleProjects;

    @Autowired @org.springframework.context.annotation.Lazy
    private com.hengshucredit.rule.server.service.ResourceVersionService resourceVersions;
    @Autowired private com.hengshucredit.rule.server.service.RuleVersionBindingService ruleVersions;

    private static final Set<String> EFFECTIVE_STATUSES = Set.of(
            "ACTIVE", "ENABLED", "PUBLISHED", "1");

    @Autowired(required = false)
    private GovernedResourceMapper resourceMapper;
    @Autowired(required = false)
    private GovernedResourceVersionMapper versionMapper;
    @Autowired(required = false)
    private GovernanceDependencySnapshotMapper dependencySnapshotMapper;
    @Autowired(required = false)
    private GovernanceResourceBootstrapService bootstrapService;
    @Autowired(required = false)
    private com.hengshucredit.rule.server.mapper.RuleDefinitionMapper definitionMapper;
    @Autowired(required = false)
    private com.hengshucredit.rule.server.mapper.RuleModelMapper modelMapper;
    @Autowired(required = false)
    private com.hengshucredit.rule.server.mapper.RuleModelOutputFieldMapper modelOutputFieldMapper;

    public GovernancePreflightReport preflight(
            String sourceResourceType,
            Long sourceResourceId,
            List<ResourceDependencyRef> dependencies,
            List<GovernanceIssue> validationIssues) {
        List<GovernanceIssue> errors = new ArrayList<>();
        List<GovernanceIssue> warnings = new ArrayList<>();
        splitIssues(validationIssues, errors, warnings);

        List<GovernancePreflightReport.ResolvedDependency> resolved =
                new ArrayList<>();
        for (ResourceDependencyRef dependency : safe(dependencies)) {
            resolved.add(resolve(sourceResourceType, sourceResourceId,
                    dependency, errors, warnings));
        }
        resolved.sort(Comparator
                .comparing(GovernancePreflightReport.ResolvedDependency
                        ::targetResourceType)
                .thenComparing(
                        GovernancePreflightReport.ResolvedDependency
                                ::targetResourceId)
                .thenComparing(value -> value.referencePath() == null
                        ? "" : value.referencePath()));
        return report(errors, warnings, resolved);
    }

    public GovernancePreflightReport revalidate(
            List<GovernancePreflightReport.ResolvedDependency> submitted) {
        List<GovernanceIssue> errors = new ArrayList<>();
        List<GovernanceIssue> warnings = new ArrayList<>();
        List<GovernancePreflightReport.ResolvedDependency> resolved =
                new ArrayList<>();
        for (GovernancePreflightReport.ResolvedDependency previous
                : safeResolved(submitted)) {
            if (previous.versionSelection() != null) {
                List<GovernanceIssue> selectedErrors = new ArrayList<>();
                var selected = resolveFixed(new ResourceDependencyRef(previous.targetResourceType(), previous.targetResourceId(),
                        previous.targetResourceType(), previous.referencePath(), previous.relationType(), previous.required(),
                        previous.versionSelection()), selectedErrors);
                errors.addAll(selectedErrors);
                if (selectedErrors.isEmpty()) {
                    if (!safeEquals(previous.targetVersionId(), selected.targetVersionId())
                            || !safeEquals(previous.targetResourceType(), selected.targetResourceType())
                            || !safeEquals(previous.targetResourceId(), selected.targetResourceId())) {
                        errors.add(issue("DEPENDENCY_VERSION_CHANGED", "指定版本在提交后已被覆盖，请重新提交审批", previous));
                        selected = selectedIssue(selected, "VERSION_CHANGED", "DEPENDENCY_VERSION_CHANGED", "指定版本在提交后已被覆盖，请重新提交审批");
                    } else if (!safeEquals(previous.targetDigest(), selected.targetDigest())) {
                        errors.add(issue("DEPENDENCY_DIGEST_CHANGED", "指定版本摘要已变化，请重新提交审批", previous));
                        selected = selectedIssue(selected, "DIGEST_CHANGED", "DEPENDENCY_DIGEST_CHANGED", "指定版本摘要已变化，请重新提交审批");
                    }
                }
                resolved.add(selected);
                continue;
            }
            GovernedResource target = findTarget(
                    previous.targetResourceType(),
                    previous.targetResourceId());
            if (target == null) {
                errors.add(issue("DEPENDENCY_MISSING",
                        "依赖资源不存在", previous));
                resolved.add(withIssue(previous, "MISSING",
                        "DEPENDENCY_MISSING", "依赖资源不存在"));
                continue;
            }
            if (!isEffective(target)) {
                errors.add(issue("DEPENDENCY_INACTIVE",
                        "依赖资源当前未生效", previous));
                resolved.add(current(previous, target, "INACTIVE",
                        null, "DEPENDENCY_INACTIVE", "依赖资源当前未生效"));
                continue;
            }
            if (!target.getEffectiveVersionId()
                    .equals(previous.targetVersionId())) {
                errors.add(issue("DEPENDENCY_VERSION_CHANGED",
                        "依赖资源在提交后已变更版本，请重新提交审批", previous));
                GovernedResourceVersion current =
                        findVersion(target.getEffectiveVersionId());
                resolved.add(current(previous, target,
                        "VERSION_CHANGED", current,
                        "DEPENDENCY_VERSION_CHANGED",
                        "依赖资源在提交后已变更版本，请重新提交审批"));
                continue;
            }
            GovernedResourceVersion version =
                    findVersion(target.getEffectiveVersionId());
            if (version == null) {
                errors.add(issue("DEPENDENCY_VERSION_MISSING",
                        "依赖资源的生效版本不存在", previous));
                resolved.add(current(previous, target,
                        "VERSION_MISSING", null,
                        "DEPENDENCY_VERSION_MISSING",
                        "依赖资源的生效版本不存在"));
                continue;
            }
            if (!safeEquals(previous.targetDigest(),
                    version.getSnapshotDigest())) {
                errors.add(issue("DEPENDENCY_DIGEST_CHANGED",
                        "依赖资源版本摘要已变化，请重新提交审批", previous));
                resolved.add(current(previous, target,
                        "DIGEST_CHANGED", version,
                        "DEPENDENCY_DIGEST_CHANGED",
                        "依赖资源版本摘要已变化，请重新提交审批"));
                continue;
            }
            resolved.add(current(previous, target, "RESOLVED",
                    version, null, null));
        }
        resolved.sort(Comparator
                .comparing(GovernancePreflightReport.ResolvedDependency
                        ::targetResourceType)
                .thenComparing(
                        GovernancePreflightReport.ResolvedDependency
                                ::targetResourceId));
        return report(errors, warnings, resolved);
    }

    public void persist(GovernanceApprovalRequest request,
                        GovernancePreflightReport report,
                        Long versionId) {
        if (dependencySnapshotMapper == null) {
            return;
        }
        dependencySnapshotMapper.delete(new LambdaUpdateWrapper<
                GovernanceDependencySnapshot>()
                .eq(GovernanceDependencySnapshot::getRequestId,
                        request.getId()));
        for (GovernancePreflightReport.ResolvedDependency dependency
                : report.dependencies()) {
            GovernanceDependencySnapshot row =
                    new GovernanceDependencySnapshot();
            row.setRequestId(request.getId());
            row.setVersionId(versionId);
            row.setSourceResourceType(request.getResourceType());
            row.setSourceResourceId(request.getResourceId());
            row.setTargetResourceType(
                    dependency.targetResourceType());
            row.setTargetResourceId(dependency.targetResourceId());
            row.setTargetVersionId(dependency.targetVersionId());
            row.setTargetVersionNo(dependency.targetVersionNo());
            row.setVersionSelectionJson(dependency.versionSelection() == null ? null
                    : com.hengshucredit.rule.server.artifact.CanonicalJson.write(dependency.versionSelection()));
            row.setReferencePath(dependency.referencePath());
            row.setRelationType(dependency.relationType());
            row.setRequired(dependency.required() ? 1 : 0);
            row.setResolutionStatus(dependency.resolutionStatus());
            row.setTargetDigest(dependency.targetDigest());
            row.setIssueCode(dependency.issueCode());
            row.setIssueMessage(dependency.issueMessage());
            dependencySnapshotMapper.insert(row);
        }
    }

    public List<GovernancePreflightReport.ResolvedDependency> load(
            Long requestId) {
        if (dependencySnapshotMapper == null) {
            return List.of();
        }
        return dependencySnapshotMapper.selectList(new LambdaQueryWrapper<
                        GovernanceDependencySnapshot>()
                        .eq(GovernanceDependencySnapshot::getRequestId,
                                requestId)
                        .orderByAsc(GovernanceDependencySnapshot::getId))
                .stream()
                .map(row -> new GovernancePreflightReport
                        .ResolvedDependency(
                        row.getTargetResourceType(),
                        row.getTargetResourceId(),
                        row.getTargetVersionId(),
                        row.getTargetVersionNo(),
                        row.getReferencePath(),
                        row.getRelationType(),
                        Integer.valueOf(1).equals(row.getRequired()),
                        row.getResolutionStatus(),
                        row.getTargetDigest(),
                        row.getIssueCode(),
                        row.getIssueMessage(), DependencyVersionSelection.read(row.getVersionSelectionJson())))
                .toList();
    }

    public void bindVersion(Long requestId, Long versionId) {
        if (dependencySnapshotMapper == null) {
            return;
        }
        GovernanceDependencySnapshot update =
                new GovernanceDependencySnapshot();
        update.setVersionId(versionId);
        dependencySnapshotMapper.update(update, new LambdaUpdateWrapper<
                GovernanceDependencySnapshot>()
                .eq(GovernanceDependencySnapshot::getRequestId,
                        requestId));
    }

    protected GovernedResource findTarget(String resourceType,
                                          Long resourceId) {
        if (resourceMapper == null) {
            return null;
        }
        if (projectReferenceOnly(resourceType, resourceId)) return resourceMapper.selectProjectReferenceMetadata(resourceId);
        GovernedResource resource = resourceMapper.selectOne(
                new LambdaQueryWrapper<
                GovernedResource>()
                .eq(GovernedResource::getResourceType,
                        normalizeType(resourceType))
                .eq(GovernedResource::getResourceId, resourceId)
                .last("LIMIT 1"));
        if (resource == null && bootstrapService != null
                && resourceId != null && resourceId > 0) {
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

    private GovernancePreflightReport.ResolvedDependency resolve(
            String sourceResourceType,
            Long sourceResourceId,
            ResourceDependencyRef dependency,
            List<GovernanceIssue> errors,
            List<GovernanceIssue> warnings) {
        if (dependency.versionSelection() != null) {
            var selected = resolveFixed(dependency, errors);
            if (selected.targetResourceType().equals(normalizeType(sourceResourceType))
                    && selected.targetResourceId().equals(sourceResourceId)) {
                var issue = issue("DEPENDENCY_SELF_REFERENCE", "资源不能依赖自身", selected);
                errors.add(issue);
                return selectedIssue(selected, "SELF_REFERENCE", issue.code(), issue.message());
            }
            return selected;
        }
        if ("MODEL_OUTPUT".equals(normalizeType(dependency.targetResourceType()))
                && (dependency.refType() == null || "MODEL_OUTPUT".equalsIgnoreCase(dependency.refType()))
                && modelOutputFieldMapper != null) {
            var field = modelOutputFieldMapper.selectById(dependency.targetResourceId());
            if (field != null && field.getModelId() != null) {
                dependency = new ResourceDependencyRef("MODEL", field.getModelId(), "MODEL", dependency.referencePath(),
                        dependency.relationType(), dependency.required());
            }
        }
        String targetType = normalizeType(
                dependency.targetResourceType());
        if (dependency.refType() != null
                && !dependency.refType().isBlank()
                && !targetType.equals(
                normalizeType(dependency.refType()))) {
            GovernanceIssue issue = GovernanceIssue.error(
                    "DEPENDENCY_TYPE_MISMATCH",
                    "依赖引用类型与目标资源类型不一致",
                    targetType, dependency.targetResourceId(),
                    dependency.referencePath());
            errors.add(issue);
            return unresolved(dependency, "TYPE_MISMATCH", issue);
        }
        if (targetType.equals(normalizeType(sourceResourceType))
                && dependency.targetResourceId().equals(sourceResourceId)) {
            GovernanceIssue issue = GovernanceIssue.error(
                    "DEPENDENCY_SELF_REFERENCE",
                    "资源不能依赖自身", targetType,
                    dependency.targetResourceId(),
                    dependency.referencePath());
            errors.add(issue);
            return unresolved(dependency, "SELF_REFERENCE", issue);
        }
        GovernedResource target = findTarget(
                targetType, dependency.targetResourceId());
        if (target == null) {
            GovernanceIssue issue = GovernanceIssue.error("DEPENDENCY_MISSING",
                    "依赖资源不存在", targetType,
                    dependency.targetResourceId(),
                    dependency.referencePath());
            add(issue, errors, warnings);
            return unresolved(dependency, "MISSING", issue);
        }
        if (!isEffective(target)) {
            GovernanceIssue issue = GovernanceIssue.error("DEPENDENCY_INACTIVE",
                    "上游资源尚未发布或已停用，请先发布上游资源", targetType,
                    dependency.targetResourceId(),
                    dependency.referencePath());
            add(issue, errors, warnings);
            return current(dependency, target, "INACTIVE",
                    null, issue.code(), issue.message());
        }
        GovernedResourceVersion version = projectReferenceOnly(targetType, dependency.targetResourceId())
                ? versionMapper.selectProjectReferenceMetadata(target.getEffectiveVersionId()) : findVersion(target.getEffectiveVersionId());
        if (version == null) {
            GovernanceIssue issue = GovernanceIssue.error(
                    "DEPENDENCY_VERSION_MISSING",
                    "依赖资源的生效版本不存在", targetType,
                    dependency.targetResourceId(),
                    dependency.referencePath());
            errors.add(issue);
            return current(dependency, target, "VERSION_MISSING",
                    null, issue.code(), issue.message());
        }
        return current(dependency, target, "RESOLVED",
                version, null, null);
    }

    private boolean isEffective(GovernedResource resource) {
        if ("RULE".equals(resource.getResourceType()) && definitionMapper != null) {
            var rule = definitionMapper.selectById(resource.getResourceId());
            if (rule == null || !Integer.valueOf(1).equals(rule.getStatus())
                    || rule.getPublishedVersion() == null || rule.getPublishedVersion() <= 0) return false;
        }
        if ("MODEL".equals(resource.getResourceType()) && modelMapper != null) {
            var model = modelMapper.selectOne(new LambdaQueryWrapper<com.hengshucredit.rule.model.entity.RuleModel>()
                    .select(com.hengshucredit.rule.model.entity.RuleModel::getId,
                            com.hengshucredit.rule.model.entity.RuleModel::getStatus,
                            com.hengshucredit.rule.model.entity.RuleModel::getPublishedVersion)
                    .eq(com.hengshucredit.rule.model.entity.RuleModel::getId, resource.getResourceId()));
            if (model == null || !Integer.valueOf(1).equals(model.getStatus())
                    || model.getPublishedVersion() == null || model.getPublishedVersion() <= 0) return false;
        }
        return resource.getEffectiveVersionId() != null
                && resource.getEffectiveStatus() != null
                && EFFECTIVE_STATUSES.contains(
                resource.getEffectiveStatus().toUpperCase(Locale.ROOT));
    }

    private boolean projectReferenceOnly(String type, Long id) {
        var scope = consoleProjects == null ? null : consoleProjects.current();
        return "PROJECT".equals(normalizeType(type)) && scope != null && !scope.can("project:view", id);
    }

    private GovernancePreflightReport.ResolvedDependency resolveFixed(ResourceDependencyRef dependency, List<GovernanceIssue> errors) {
        DependencyVersionSelection selection = dependency.versionSelection();
        try {
            String type;
            Long ownerId, versionId;
            Integer number;
            String digest;
            if ("RULE_BINDING".equals(selection.versionSource())) {
                if (!"RULE".equals(selection.refType())) throw new IllegalArgumentException("规则版本引用类型不匹配");
                var version = ruleVersions.resolveSnapshot(selection.refId(), selection.versionId());
                if (version.getArtifactId() == null || version.getArtifactDigest() == null)
                    throw new IllegalArgumentException("指定规则版本缺少可验证制品");
                type = "RULE"; ownerId = selection.refId(); versionId = version.getId();
                number = com.hengshucredit.rule.server.service.RuleVersionBindingService.businessVersion(version);
                digest = version.getArtifactDigest();
            } else {
                var stored = resourceVersions.snapshot(new com.hengshucredit.rule.core.engine.ResourceVersionCall(selection.refType(),
                        selection.refId(), selection.versionId(), "", List.of(), null, null,
                        "SNAPSHOT".equals(selection.versionSource()) ? null : selection.versionSource()), null);
                type = switch (selection.refType()) { case "CONSTANT" -> "VARIABLE"; case "MODEL_OUTPUT" -> "MODEL"; default -> selection.refType(); };
                ownerId = stored.getLong("ownerId");
                versionId = stored.getLong("resolvedVersionId") == null ? selection.versionId() : stored.getLong("resolvedVersionId");
                number = stored.getInteger("displayVersion");
                digest = stored.getString("digest");
                if (digest == null) digest = Sha256Digests.text(com.hengshucredit.rule.server.artifact.CanonicalJson.write(stored.get("payload")));
                if (stored.getString("secretDigest") != null) digest = Sha256Digests.text(digest + ":" + stored.getString("secretDigest"));
            }
            GovernedResource owner = findTarget(type, ownerId);
            if (owner == null || "DELETED".equals(owner.getEffectiveStatus()) || "RULE".equals(type) && !isEffective(owner))
                throw new IllegalArgumentException("指定版本所属资源不存在或不可用");
            return new GovernancePreflightReport.ResolvedDependency(type, ownerId, versionId, number,
                    dependency.referencePath(), dependency.relationType(), dependency.required(), "RESOLVED", digest, null, null, selection);
        } catch (IllegalArgumentException | IllegalStateException unavailable) {
            var issue = GovernanceIssue.error("DEPENDENCY_VERSION_UNAVAILABLE", unavailable.getMessage(),
                    dependency.targetResourceType(), dependency.targetResourceId(), dependency.referencePath());
            errors.add(issue);
            return unresolved(dependency, "VERSION_UNAVAILABLE", issue);
        }
    }

    private GovernancePreflightReport.ResolvedDependency selectedIssue(GovernancePreflightReport.ResolvedDependency dependency,
                                                                       String status, String code, String message) {
        return new GovernancePreflightReport.ResolvedDependency(dependency.targetResourceType(), dependency.targetResourceId(),
                dependency.targetVersionId(), dependency.targetVersionNo(), dependency.referencePath(), dependency.relationType(),
                dependency.required(), status, dependency.targetDigest(), code, message, dependency.versionSelection());
    }

    private GovernancePreflightReport report(
            List<GovernanceIssue> errors,
            List<GovernanceIssue> warnings,
            List<GovernancePreflightReport.ResolvedDependency> resolved) {
        StringBuilder digestInput = new StringBuilder();
        for (GovernancePreflightReport.ResolvedDependency dependency
                : resolved) {
            digestInput.append(dependency.targetResourceType()).append(':')
                    .append(dependency.targetResourceId()).append(':')
                    .append(dependency.targetVersionId()).append(':')
                    .append(dependency.targetDigest()).append(':')
                    .append(dependency.versionSelection() == null ? "" : com.hengshucredit.rule.server.artifact.CanonicalJson.write(dependency.versionSelection()) + ':')
                    .append(dependency.referencePath()).append('|');
        }
        return new GovernancePreflightReport(errors.isEmpty(),
                errors, warnings, resolved,
                Sha256Digests.text(digestInput.toString()));
    }

    private GovernancePreflightReport.ResolvedDependency unresolved(
            ResourceDependencyRef dependency,
            String status,
            GovernanceIssue issue) {
        return new GovernancePreflightReport.ResolvedDependency(
                normalizeType(dependency.targetResourceType()),
                dependency.targetResourceId(), null, null,
                dependency.referencePath(), dependency.relationType(),
                dependency.required(), status, null,
                issue.code(), issue.message(), dependency.versionSelection());
    }

    private GovernancePreflightReport.ResolvedDependency current(
            ResourceDependencyRef dependency,
            GovernedResource target,
            String status,
            GovernedResourceVersion version,
            String issueCode,
            String issueMessage) {
        return new GovernancePreflightReport.ResolvedDependency(
                normalizeType(dependency.targetResourceType()),
                dependency.targetResourceId(),
                target.getEffectiveVersionId(),
                target.getEffectiveVersionNo(),
                dependency.referencePath(), dependency.relationType(),
                dependency.required(), status,
                version == null ? null : version.getSnapshotDigest(),
                issueCode, issueMessage);
    }

    private GovernancePreflightReport.ResolvedDependency current(
            GovernancePreflightReport.ResolvedDependency previous,
            GovernedResource target,
            String status,
            GovernedResourceVersion version,
            String issueCode,
            String issueMessage) {
        return new GovernancePreflightReport.ResolvedDependency(
                previous.targetResourceType(),
                previous.targetResourceId(),
                target.getEffectiveVersionId(),
                target.getEffectiveVersionNo(),
                previous.referencePath(), previous.relationType(),
                previous.required(), status,
                version == null ? null : version.getSnapshotDigest(),
                issueCode, issueMessage);
    }

    private GovernancePreflightReport.ResolvedDependency withIssue(
            GovernancePreflightReport.ResolvedDependency previous,
            String status,
            String issueCode,
            String issueMessage) {
        return new GovernancePreflightReport.ResolvedDependency(
                previous.targetResourceType(),
                previous.targetResourceId(), null, null,
                previous.referencePath(), previous.relationType(),
                previous.required(), status, null,
                issueCode, issueMessage);
    }

    private GovernanceIssue issue(
            String code,
            String message,
            GovernancePreflightReport.ResolvedDependency dependency) {
        return GovernanceIssue.error(code, message,
                dependency.targetResourceType(),
                dependency.targetResourceId(),
                dependency.referencePath());
    }

    private void add(GovernanceIssue issue,
                     List<GovernanceIssue> errors,
                     List<GovernanceIssue> warnings) {
        (issue.isError() ? errors : warnings).add(issue);
    }

    private void splitIssues(List<GovernanceIssue> issues,
                             List<GovernanceIssue> errors,
                             List<GovernanceIssue> warnings) {
        for (GovernanceIssue issue : safeIssues(issues)) {
            add(issue, errors, warnings);
        }
    }

    private String normalizeType(String resourceType) {
        if (resourceType == null || resourceType.isBlank()) {
            throw new IllegalArgumentException("资源类型不能为空");
        }
        return resourceType.trim().toUpperCase(Locale.ROOT);
    }

    private boolean safeEquals(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private List<ResourceDependencyRef> safe(
            List<ResourceDependencyRef> dependencies) {
        return dependencies == null ? List.of() : dependencies;
    }

    private List<GovernanceIssue> safeIssues(
            List<GovernanceIssue> issues) {
        return issues == null ? List.of() : issues;
    }

    private List<GovernancePreflightReport.ResolvedDependency> safeResolved(
            List<GovernancePreflightReport.ResolvedDependency> dependencies) {
        return dependencies == null ? List.of() : dependencies;
    }
}
