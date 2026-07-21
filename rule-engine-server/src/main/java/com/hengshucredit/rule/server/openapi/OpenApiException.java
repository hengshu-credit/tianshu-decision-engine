package com.hengshucredit.rule.server.openapi;

public class OpenApiException extends RuntimeException {
    private final OpenApiStatus status;

    public OpenApiException(OpenApiStatus status) {
        super(status == null ? null : status.getMessage());
        this.status = status;
    }

    public OpenApiStatus getStatus() {
        return status;
    }
}
