package com.hengshucredit.rule.client.http;

import com.hengshucredit.rule.model.PlatformCode;
import com.hengshucredit.rule.model.PlatformException;

/** 调用失败与规则执行失败分开处理；不向异常消息复制上游响应体或凭据。 */
public class RuleHttpException extends PlatformException {
    private final int httpStatus;
    private final String executionStatus;
    private final String traceId;
    private final Long retryAfterMs;

    public RuleHttpException(String message, int httpStatus, String platformCode) {
        this(message, httpStatus, platformCode, null, null, null);
    }

    public RuleHttpException(String message, int httpStatus, String platformCode,
                             String executionStatus, String traceId, Long retryAfterMs) {
        super(PlatformCode.find(platformCode) == null
                ? httpStatus == 0 ? PlatformCode.EXECUTION_UNKNOWN : PlatformCode.UPSTREAM_RESPONSE_INVALID
                : PlatformCode.find(platformCode), message);
        this.httpStatus = httpStatus;
        this.executionStatus = executionStatus == null && httpStatus == 0 ? "UNKNOWN" : executionStatus;
        this.traceId = traceId;
        this.retryAfterMs = retryAfterMs;
    }

    public int getHttpStatus() { return httpStatus; }
    public String getPlatformCode() { return platformStatus().code(); }
    public String getExecutionStatus() { return executionStatus; }
    public String getTraceId() { return traceId; }
    public Long getRetryAfterMs() { return retryAfterMs; }
}
