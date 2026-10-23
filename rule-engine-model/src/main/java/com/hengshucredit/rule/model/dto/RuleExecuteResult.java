package com.hengshucredit.rule.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class RuleExecuteResult implements com.hengshucredit.rule.model.PlatformOutcome {
    private Object result;
    private boolean success;
    private String errorMessage;
    private Long executeTimeMs;
    private List<Object> traces;

    public String getCode() { return platformStatus().code(); }
    public String getMessage() { return platformStatus().message(); }
    public String getDetail() { return platformDetail(); }
    @Override public com.hengshucredit.rule.model.PlatformCode platformStatus() {
        return success ? com.hengshucredit.rule.model.PlatformCode.SUCCESS : com.hengshucredit.rule.model.PlatformCode.RESULT_ERROR;
    }
    @Override public String platformDetail() { return success ? null : errorMessage; }
}
