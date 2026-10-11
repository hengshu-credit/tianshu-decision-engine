package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.model.entity.GovernedResourceVersion;

import java.time.LocalDateTime;

public record GovernanceVersionView(
        Long id,
        String resourceType,
        Long resourceId,
        Integer versionNo,
        Integer revisionNo,
        Long sourceVersionId,
        Long approvalRequestId,
        String snapshotJson,
        String snapshotDigest,
        boolean hasProtectedCredential,
        boolean deleted,
        String effectiveStatus,
        String changeSummary,
        String createBy,
        LocalDateTime createTime) {

    public static GovernanceVersionView from(
            GovernedResourceVersion version) {
        return new GovernanceVersionView(
                version.getId(),
                version.getResourceType(),
                version.getResourceId(),
                version.getDisplayVersion(),
                version.getVersionNo(),
                version.getSourceVersionId(),
                version.getApprovalRequestId(),
                version.getSnapshotJson(),
                version.getSnapshotDigest(),
                version.getSecretDigest() != null,
                Boolean.TRUE.equals(version.getDeleted()),
                version.getEffectiveStatus(),
                version.getChangeSummary(),
                version.getCreateBy(),
                version.getCreateTime());
    }
}
