package com.hengshucredit.rule.model.dto;

import lombok.Data;

@Data
public class RuleLifecycleActionRequest {
    private String comment;
    private String forcePublishReason;
}
