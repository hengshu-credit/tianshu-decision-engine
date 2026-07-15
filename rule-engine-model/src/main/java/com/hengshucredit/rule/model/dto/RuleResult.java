package com.hengshucredit.rule.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class RuleResult {
    private String traceId;
    private Object result;
    private List<Object> traces;
    private boolean success;
    private String errorMessage;
    private long executeTimeMs;
}
