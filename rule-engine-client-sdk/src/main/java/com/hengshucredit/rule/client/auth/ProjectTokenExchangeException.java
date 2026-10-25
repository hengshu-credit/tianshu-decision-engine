package com.hengshucredit.rule.client.auth;

import com.hengshucredit.rule.model.PlatformCode;
import java.io.IOException;

/** 保留 Token 端点的平台错误，同时沿用既有刷新退避和宽限期行为。 */
public class ProjectTokenExchangeException extends IOException {
    private final PlatformCode platformStatus;
    private final int httpStatus;
    private final String traceId;
    private com.hengshucredit.rule.model.ExternalCodeMapping mapping = com.hengshucredit.rule.model.ExternalCodeMapping.DEFAULT;

    public ProjectTokenExchangeException(PlatformCode code, int httpStatus, String traceId) {
        super(code.message());
        this.platformStatus = code;
        this.httpStatus = httpStatus;
        this.traceId = traceId;
    }
    public PlatformCode platformStatus() { return platformStatus; }
    public int httpStatus() { return httpStatus; }
    public String traceId() { return traceId; }
    public ProjectTokenExchangeException mapExternal(com.hengshucredit.rule.model.ExternalCodeMapping value) { mapping = value; return this; }
    public com.hengshucredit.rule.model.ExternalCodeMapping mapping() { return mapping; }
}
