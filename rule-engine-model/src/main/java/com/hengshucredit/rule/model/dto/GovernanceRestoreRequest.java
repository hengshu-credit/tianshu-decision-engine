package com.hengshucredit.rule.model.dto;

import lombok.Data;

@Data
public class GovernanceRestoreRequest {
    private Long sourceVersionId;
    private String restoreMode = "NEW";
}
