package com.hengshucredit.rule.model;

/** 跨模块传播的类型化错误；异常描述与响应描述来自同一字典。 */
public class PlatformException extends RuntimeException {
    private final PlatformCode platformStatus;
    private final String detail;

    public PlatformException(PlatformCode status, String detail) {
        this(status, detail, null);
    }

    public PlatformException(PlatformCode status, String detail, Throwable cause) {
        super(status.message(), cause);
        this.platformStatus = status;
        this.detail = detail;
    }

    public String getCode() { return platformStatus.code(); }
    public PlatformCode platformStatus() { return platformStatus; }
    public String getDetail() { return detail; }
}
