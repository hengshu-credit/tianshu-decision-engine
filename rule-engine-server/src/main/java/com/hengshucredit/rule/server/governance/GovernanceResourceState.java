package com.hengshucredit.rule.server.governance;

/** Production state and pending changes are independent facts. No configuration snapshots are returned. */
public record GovernanceResourceState(Long resourceId, String effectiveStatus, boolean hasEffectiveVersion,
                                      Long requestId, String requestStatus, String action,
                                      String applicant, boolean owned, boolean hasRuleDraft, boolean hasApprovedRuleRevision, Long projectId) {
    public GovernanceResourceState(Long resourceId, String effectiveStatus, boolean hasEffectiveVersion, Long requestId,
                                   String requestStatus, String action, String applicant, boolean owned, boolean hasRuleDraft, boolean hasApprovedRuleRevision) {
        this(resourceId, effectiveStatus, hasEffectiveVersion, requestId, requestStatus, action, applicant, owned, hasRuleDraft, hasApprovedRuleRevision, null);
    }
}
