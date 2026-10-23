package com.hengshucredit.rule.model.dto;

import lombok.Data;
import com.hengshucredit.rule.model.PlatformCode;
import com.hengshucredit.rule.model.PlatformOutcome;

import java.util.ArrayList;
import java.util.List;

@Data
public class RuleExperimentExecuteResult implements PlatformOutcome {
    private String experimentTraceId;
    private String experimentCode;
    private String experimentName;
    private Long versionId;
    private Integer version;
    private String requestKey;
    private boolean success;
    private String errorMessage;
    private String platformCode;
    private long executeTimeMs;
    private RuleExperimentGroupResult productionGroup;
    private List<RuleExperimentGroupResult> testGroups = new ArrayList<>();
    private List<String> tags = new ArrayList<>();

    public String getCode() { return platformStatus().code(); }
    public String getMessage() { return platformStatus().message(); }
    public String getDetail() { return platformDetail(); }
    @Override public PlatformCode platformStatus() {
        if (success) return PlatformCode.SUCCESS;
        PlatformCode code = PlatformCode.find(platformCode);
        return code == null || code == PlatformCode.SUCCESS ? PlatformCode.EXPERIMENT_EXECUTION_ERROR : code;
    }
    @Override public String platformDetail() { return success ? null : errorMessage; }
    @Override public String platformTraceId() { return experimentTraceId; }
}
