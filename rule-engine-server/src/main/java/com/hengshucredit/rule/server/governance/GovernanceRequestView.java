package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.model.entity.GovernanceApprovalRequest;

import java.time.LocalDateTime;

public record GovernanceRequestView(
        Long id,
        String requestNo,
        String resourceType,
        Long resourceId,
        Long projectId,
        String action,
        String status,
        Long baseVersionId,
        Integer baseVersionNo,
        Long sourceVersionId,
        String restoreMode,
        String publishMode,
        Long targetVersionId,
        Long expectedTargetSnapshotId,
        String draftSnapshotJson,
        String submittedSnapshotJson,
        String snapshotDigest,
        boolean hasProtectedCredential,
        String dependencyDigest,
        String validationReportJson,
        String changeSummary,
        String submitComment,
        String reviewComment,
        String applicant,
        LocalDateTime submitTime,
        String reviewer,
        LocalDateTime reviewTime,
        LocalDateTime createTime,
        LocalDateTime updateTime) {

    public static GovernanceRequestView from(
            GovernanceApprovalRequest request) {
        return new GovernanceRequestView(
                request.getId(),
                request.getRequestNo(),
                request.getResourceType(),
                request.getResourceId(),
                request.getProjectId(),
                request.getAction(),
                request.getStatus(),
                request.getBaseVersionId(),
                request.getBaseVersionNo(),
                request.getSourceVersionId(),
                request.getRestoreMode(),
                request.getPublishMode(),
                request.getTargetVersionId(),
                request.getExpectedTargetSnapshotId(),
                request.getDraftSnapshotJson(),
                request.getSubmittedSnapshotJson(),
                request.getSnapshotDigest(),
                request.getSecretDigest() != null,
                request.getDependencyDigest(),
                request.getValidationReportJson(),
                request.getChangeSummary(),
                request.getSubmitComment(),
                request.getReviewComment(),
                request.getApplicant(),
                request.getSubmitTime(),
                request.getReviewer(),
                request.getReviewTime(),
                request.getCreateTime(),
                request.getUpdateTime());
    }
}
