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
    /** 跨请求恢复信息；不引入新的执行 ID，始终以 traceId 作为根关联。 */
    private boolean resumed;
    private int attemptNo;
    private int reusedCount;
    private int reexecutedCount;
    private String executionStatus;
    /** 平台错误码；执行成功时为空。 */
    private Integer platformCode;
    /** 服务端建议重试等待时间（毫秒）。 */
    private Long retryAfterMs;
    private Long revisionId;
    private String artifactDigest;
}
