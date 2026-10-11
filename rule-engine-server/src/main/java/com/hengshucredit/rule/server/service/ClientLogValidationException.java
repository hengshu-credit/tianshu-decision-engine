package com.hengshucredit.rule.server.service;

/** 不可重试的客户端日志输入错误；HTTP 返回业务错误，Kafka 转入死信。 */
public class ClientLogValidationException extends IllegalArgumentException {
    private final int statusCode;

    public ClientLogValidationException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() { return statusCode; }
}
