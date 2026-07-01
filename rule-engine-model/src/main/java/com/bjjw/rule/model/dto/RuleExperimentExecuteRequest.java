package com.bjjw.rule.model.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class RuleExperimentExecuteRequest {
    private Map<String, Object> params;
    private String requestKey;
    private LocalDateTime requestTime;
    private String clientAppName;
}
