package com.hengshucredit.rule.model.dto;

import lombok.Data;

@Data
public class GovernanceDraftRequest {
    private String resourceType;
    private Long resourceId;
    private Long projectId;
    private String action;
    private String snapshotJson;
    private String effectiveStatus;
    private String secretPayloadCiphertext;
    private String secretDigest;
    private String changeSummary;
    private Long sourceVersionId;
    private String restoreMode;
    /** NEW creates an independent draft; OVERWRITE targets a business version. */
    private String publishMode;
    private Long targetVersionId;
    private Long expectedTargetSnapshotId;
    private String expectedSnapshotDigest;
}
