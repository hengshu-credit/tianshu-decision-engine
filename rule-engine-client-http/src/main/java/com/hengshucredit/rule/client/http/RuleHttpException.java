package com.hengshucredit.rule.client.http;

/** 调用失败与规则执行失败分开处理；不向异常消息复制上游响应体或凭据。 */
public class RuleHttpException extends RuntimeException {
    private final int httpStatus;
    private final Integer platformCode;

    public RuleHttpException(String message, int httpStatus, Integer platformCode) {
        super(message);
        this.httpStatus = httpStatus;
        this.platformCode = platformCode;
    }

    public int getHttpStatus() { return httpStatus; }
    public Integer getPlatformCode() { return platformCode; }
}
