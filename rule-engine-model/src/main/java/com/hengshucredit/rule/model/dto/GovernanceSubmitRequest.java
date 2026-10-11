package com.hengshucredit.rule.model.dto;

import lombok.Data;

@Data
public class GovernanceSubmitRequest {
    private String expectedSnapshotDigest;
    private String comment;
}
