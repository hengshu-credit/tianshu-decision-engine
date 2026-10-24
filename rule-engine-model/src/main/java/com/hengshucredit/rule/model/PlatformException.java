package com.hengshucredit.rule.model;

/** 跨模块传播的类型化错误；异常描述与响应描述来自同一字典。 */
public class PlatformException extends RuntimeException {
    private final PlatformCode platformStatus;
    private final String detail;
    private transient ExternalCodeMapping externalMapping = ExternalCodeMapping.DEFAULT;

    public PlatformException(PlatformCode status, String detail) {
        this(status, detail, null);
    }

    public PlatformException(PlatformCode status, String detail, Throwable cause) {
        super(status.message(), cause);
        this.platformStatus = status;
        this.detail = detail;
    }

    public String getCode() { return externalMapping.code(platformStatus); }
    @Override public String getMessage() { return externalMapping.message(platformStatus); }
    public PlatformException mapExternal(ExternalCodeMapping mapping) { externalMapping = java.util.Objects.requireNonNull(mapping); return this; }
    public ExternalCodeMapping externalMapping() { return externalMapping; }
    public PlatformCode platformStatus() { return platformStatus; }
    public String getDetail() { return detail; }
}
