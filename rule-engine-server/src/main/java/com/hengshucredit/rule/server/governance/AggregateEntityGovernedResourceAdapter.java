package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.server.artifact.CanonicalJson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Base adapter for resources whose effective projection spans a root table and
 * one or more child tables.
 */
public abstract class AggregateEntityGovernedResourceAdapter<T>
        implements GovernedResourceAdapter {

    private final SimpleEntityGovernedResourceAdapter<T> rootAdapter;

    protected AggregateEntityGovernedResourceAdapter(
            SimpleEntityGovernedResourceAdapter<T> rootAdapter) {
        this.rootAdapter = rootAdapter;
    }

    @Override
    public String resourceType() {
        return rootAdapter.resourceType();
    }

    @Override
    public ResourceSnapshot loadEffective(Long resourceId) {
        ResourceSnapshot root = rootAdapter.loadEffective(resourceId);
        Map<String, Object> aggregate =
                CanonicalJson.readMap(root.snapshotJson());
        enrichSnapshot(resourceId, aggregate);
        return rootAdapter.normalizeDraft(new ResourceSnapshot(
                CanonicalJson.write(aggregate),
                root.effectiveStatus(),
                root.secretPayloadCiphertext(),
                root.secretDigest()));
    }

    @Override
    public ResourceSnapshot normalizeDraft(ResourceSnapshot draft) {
        return rootAdapter.normalizeDraft(draft);
    }

    @Override
    public List<GovernanceIssue> validate(ResourceSnapshot draft) {
        List<GovernanceIssue> issues =
                new ArrayList<>(rootAdapter.validate(draft));
        validateAggregate(CanonicalJson.readMap(
                draft.snapshotJson()), issues);
        return issues;
    }

    @Override
    public List<ResourceDependencyRef> collectDependencies(
            ResourceSnapshot draft) {
        return rootAdapter.collectDependencies(draft).stream()
                .filter(dependency ->
                        !isOwnershipReference(dependency))
                .toList();
    }

    @Override
    public ResourceDiff diff(ResourceSnapshot left,
                             ResourceSnapshot right) {
        return JsonResourceDiff.compare(left, right);
    }

    @Override
    public AppliedResource apply(ApprovalApplyContext context) {
        AppliedResource applied = rootAdapter.apply(context);
        Map<String, Object> aggregate =
                CanonicalJson.readMap(context.snapshot().snapshotJson());
        applied = applyAggregateWithResult(applied, aggregate);
        return afterAggregateApplied(context, applied);
    }

    protected AppliedResource applyAggregateWithResult(
            AppliedResource applied, Map<String, Object> snapshot) {
        applyAggregate(applied.resourceId(), snapshot);
        return applied;
    }

    protected abstract void enrichSnapshot(
            Long resourceId, Map<String, Object> snapshot);

    protected abstract void applyAggregate(
            Long resourceId, Map<String, Object> snapshot);

    protected void validateAggregate(Map<String, Object> snapshot,
                                     List<GovernanceIssue> issues) {
    }

    protected boolean isOwnershipReference(
            ResourceDependencyRef dependency) {
        return false;
    }

    protected final boolean isCollectionOwnershipReference(
            ResourceDependencyRef dependency,
            String collection, String field) {
        String path = dependency.referencePath();
        return path != null
                && path.startsWith("$." + collection + "[")
                && path.endsWith("]." + field);
    }

    protected AppliedResource afterAggregateApplied(
            ApprovalApplyContext context, AppliedResource applied) {
        return applied;
    }

    /**
     * Restores a staging projection from an already-effective snapshot without
     * creating a version or running post-approval lifecycle hooks.
     */
    protected final void restoreProjection(
            Long resourceId, ResourceSnapshot effectiveSnapshot) {
        if (resourceId == null || effectiveSnapshot == null) {
            return;
        }
        rootAdapter.apply(new ApprovalApplyContext(
                null, resourceId, 0, "UPDATE",
                effectiveSnapshot, "审批终止恢复", null));
        applyAggregate(resourceId, CanonicalJson.readMap(
                effectiveSnapshot.snapshotJson()));
    }
}
