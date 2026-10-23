package com.hengshucredit.rule.server.openapi;

import com.hengshucredit.rule.model.PlatformCode;

public class OpenApiStatus {
    private final boolean success;
    private final String code;
    private final String message;
    private final int httpStatus;
    private final String detail;

    public OpenApiStatus(PlatformCode platform, String detail) {
        java.util.Objects.requireNonNull(platform);
        this.success = platform == PlatformCode.SUCCESS;
        this.code = platform.code();
        this.message = platform.message();
        this.httpStatus = platform.httpStatus();
        this.detail = detail;
    }

    public String getDetail() { return detail; }

    public boolean isSuccess() {
        return success;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
