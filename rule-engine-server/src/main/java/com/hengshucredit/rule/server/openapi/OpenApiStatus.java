package com.hengshucredit.rule.server.openapi;

public class OpenApiStatus {
    private final boolean success;
    private final String code;
    private final String message;
    private final int httpStatus;

    public OpenApiStatus(boolean success, String code, String message, int httpStatus) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

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
