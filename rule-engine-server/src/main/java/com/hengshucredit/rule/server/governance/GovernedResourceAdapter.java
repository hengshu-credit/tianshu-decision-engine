package com.hengshucredit.rule.server.governance;

import java.util.List;

public interface GovernedResourceAdapter {
    String resourceType();

    ResourceSnapshot loadEffective(Long resourceId);

    /** Capture the published business line before restoring a different production projection. */
    default ResourceSnapshot loadApplied(AppliedResource applied) { return loadEffective(applied.resourceId()); }

    ResourceSnapshot normalizeDraft(ResourceSnapshot draft);

    List<GovernanceIssue> validate(ResourceSnapshot draft);

    /**
     * Validates a draft with its requested lifecycle action. Adapters that
     * only validate snapshot content keep the original implementation;
     * action-sensitive resources can additionally protect disable/delete.
     */
    default List<GovernanceIssue> validate(ResourceSnapshot draft,
                                           String action) {
        return validate(draft);
    }

    List<ResourceDependencyRef> collectDependencies(ResourceSnapshot draft);

    ResourceDiff diff(ResourceSnapshot left, ResourceSnapshot right);

    AppliedResource apply(ApprovalApplyContext context);

    /** Restore the latest projection after publishing another business version, without publication hooks. */
    default void restoreLatest(Long id, ResourceSnapshot snapshot, String actor) {
        apply(new ApprovalApplyContext(null, id, 0, "PROJECTION_RESTORE", snapshot, actor, null));
    }

    /** A workspace projection remains inactive and must not emit publication side effects. */
    default boolean supportsDraftWorkspace() { return false; }

    /**
     * Called when an approval ends without applying its submitted snapshot.
     * Most resources do not need projection cleanup; adapters with a legacy
     * staging workflow can close that workflow here.
     */
    default void onApprovalTerminated(Long resourceId,
                                      ResourceSnapshot effectiveSnapshot,
                                      String actor,
                                      String comment,
                                      String terminalStatus) {
    }

    default void onApprovalTerminated(Long resourceId, ResourceSnapshot effectiveSnapshot,
                                      String actor, String comment, String terminalStatus, Long requestId) {
        onApprovalTerminated(resourceId, effectiveSnapshot, actor, comment, terminalStatus);
    }

    /**
     * Called for create approvals that own server-side staging data.
     * The request snapshot was normalized before it was persisted.
     */
    default void onCreateApprovalTerminated(
            ResourceSnapshot requestSnapshot,
            String actor,
            String comment,
            String terminalStatus) {
    }
}
