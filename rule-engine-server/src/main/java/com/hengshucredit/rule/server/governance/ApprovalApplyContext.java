package com.hengshucredit.rule.server.governance;

public record ApprovalApplyContext(Long requestId,
                                   Long resourceId,
                                   Integer nextVersionNo,
                                   String action,
                                   ResourceSnapshot snapshot,
                                   String actor,
                                   Long sourceVersionId,
                                   String restoreMode,
                                   boolean isolatedPublication) {
    public ApprovalApplyContext(Long requestId, Long resourceId, Integer nextVersionNo, String action,
                                ResourceSnapshot snapshot, String actor, Long sourceVersionId, String restoreMode) {
        this(requestId, resourceId, nextVersionNo, action, snapshot, actor, sourceVersionId, restoreMode, false);
    }
    public ApprovalApplyContext(Long requestId, Long resourceId, Integer nextVersionNo, String action,
                                ResourceSnapshot snapshot, String actor, Long sourceVersionId) {
        this(requestId, resourceId, nextVersionNo, action, snapshot, actor, sourceVersionId, "NEW");
    }

    public boolean reuseVersion() { return "PROJECTION_RESTORE".equals(action) || "RESTORE".equals(action) && ("REUSE".equals(restoreMode) || "OVERWRITE".equals(restoreMode)); }

    public boolean projectionOnly() { return "PROJECTION_RESTORE".equals(action); }

    public boolean draftWrite() {
        return "DRAFT_CREATE".equals(action) || "DRAFT_UPDATE".equals(action) || "DRAFT_DELETE".equals(action);
    }

    public boolean insertRoot() {
        return "DRAFT_CREATE".equals(action) || "CREATE".equals(action) && resourceId == null;
    }
}
