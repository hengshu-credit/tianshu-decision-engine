package com.hengshucredit.rule.client.http;

/** 调用失败与规则执行失败分开处理；不向异常消息复制上游响应体或凭据。 */
public class RuleHttpException extends RuntimeException {
    private final int httpStatus;
    private final Integer platformCode;
    private final String executionStatus;
    private final String traceId;
    private final Long retryAfterMs;

    public RuleHttpException(String message, int httpStatus, Integer platformCode) {
        this(message, httpStatus, platformCode, null, null, null);
    }

    public RuleHttpException(String message, int httpStatus, Integer platformCode,
                             String executionStatus, String traceId, Long retryAfterMs) {
        super(message);
        this.httpStatus = httpStatus;
        this.platformCode = platformCode;
        this.executionStatus = executionStatus;
        this.traceId = traceId;
        this.retryAfterMs = retryAfterMs;
    }

    public int getHttpStatus() { return httpStatus; }
    public Integer getPlatformCode() { return platformCode; }
    public String getExecutionStatus() { return executionStatus; }
    public String getTraceId() { return traceId; }
    public Long getRetryAfterMs() { return retryAfterMs; }
}
