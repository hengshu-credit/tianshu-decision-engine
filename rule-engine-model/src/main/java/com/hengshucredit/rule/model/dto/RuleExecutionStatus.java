package com.hengshucredit.rule.model.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/** 跨节点幂等执行状态查询结果。 */
@Data
public class RuleExecutionStatus {
    private String traceId;
    private String status;
    private int attemptNo;
    private Long revisionId;
    private String artifactDigest;
    private RuleResult result;
    /** 未完成时返回脱敏的来源/步骤中间状态；完成时可为空。 */
    private Map<String, Object> intermediate;
    private String errorMessage;
    private LocalDateTime expireTime;
}
