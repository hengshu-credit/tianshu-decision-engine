package com.hengshucredit.rule.model.dto;

import lombok.Data;
import com.hengshucredit.rule.model.PlatformCode;
import com.hengshucredit.rule.model.PlatformOutcome;
import java.util.List;

@Data
public class RuleResult implements PlatformOutcome {
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
    /** THROTTLED 时的平台容量资源编码，不以错误文案推断资源类型。 */
    private String capacityResource;
    /** 平台错误码；执行成功时为空。 */
    private String platformCode;
    /** 服务端建议重试等待时间（毫秒）。 */
    private Long retryAfterMs;
    private Long revisionId;
    private String artifactDigest;
    /** Present only for an explicitly selected management-test source. */
    private RuleTestExecutionIdentity executionIdentity;

    public String getCode() { return platformStatus().code(); }
    public String getMessage() { return platformStatus().message(); }
    public String getDetail() { return platformDetail(); }
    @Override public PlatformCode platformStatus() {
        return PlatformCode.execution(success, platformCode, executionStatus);
    }
    @Override public String platformDetail() { return success ? null : errorMessage; }
    @Override public String platformTraceId() { return traceId; }
}
