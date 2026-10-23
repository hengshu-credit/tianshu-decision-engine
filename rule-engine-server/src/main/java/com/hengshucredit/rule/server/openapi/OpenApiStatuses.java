package com.hengshucredit.rule.server.openapi;

import com.hengshucredit.rule.model.PlatformCode;

/** 开放接口兼容工厂，结果码、描述和 HTTP 状态均引用全局字典。 */
public final class OpenApiStatuses {
    public static final String SUCCESS = PlatformCode.SUCCESS.code();
    public static final String PARAMETER_VALIDATION_ERROR = PlatformCode.PARAMETER_INVALID.code();
    public static final String REQUIRED_FIELD_MISSING = PlatformCode.REQUIRED_FIELD_MISSING.code();
    public static final String RESULT_ERROR = PlatformCode.RESULT_ERROR.code();
    public static final String TOKEN_EXPIRED = PlatformCode.TOKEN_INVALID.code();
    public static final String ACCOUNT_PASSWORD_ERROR = PlatformCode.CREDENTIAL_INVALID.code();
    public static final String IP_RESTRICTED = PlatformCode.IP_RESTRICTED.code();
    public static final String DOMAIN_RESTRICTED = PlatformCode.DOMAIN_RESTRICTED.code();
    public static final String ACCOUNT_DISABLED = PlatformCode.ACCOUNT_DISABLED.code();
    public static final String QPS_CONCURRENCY_EXCEEDED = PlatformCode.CAPACITY_EXCEEDED.code();
    public static final String REQUEST_TOO_FREQUENT = PlatformCode.REQUEST_TOO_FREQUENT.code();
    public static final String REQUEST_TIMEOUT = PlatformCode.REQUEST_TIMEOUT.code();
    public static final String WAITING_EXTERNAL = PlatformCode.WAITING_EXTERNAL.code();
    public static final String PRODUCT_UNAUTHORIZED = PlatformCode.PRODUCT_UNAUTHORIZED.code();
    public static final String DAILY_QUOTA_EXCEEDED = PlatformCode.DAILY_QUOTA_EXCEEDED.code();
    public static final String MONTHLY_QUOTA_EXCEEDED = PlatformCode.MONTHLY_QUOTA_EXCEEDED.code();
    public static final String REQUEST_PRODUCT_UNAUTHORIZED = PlatformCode.REQUEST_PRODUCT_UNAUTHORIZED.code();
    public static final String SYSTEM_ERROR = PlatformCode.SYSTEM_ERROR.code();

    private OpenApiStatuses() { }

    public static OpenApiStatus success() {
        return new OpenApiStatus(PlatformCode.SUCCESS, null);
    }

    public static OpenApiStatus parameterValidation(String detail) {
        return new OpenApiStatus(PlatformCode.PARAMETER_INVALID, detail);
    }

    public static OpenApiStatus requiredField(String detail) {
        return new OpenApiStatus(PlatformCode.REQUIRED_FIELD_MISSING, detail);
    }

    public static OpenApiStatus resultError(String detail) {
        return new OpenApiStatus(PlatformCode.RESULT_ERROR, detail);
    }

    public static OpenApiStatus tokenExpired() {
        return new OpenApiStatus(PlatformCode.TOKEN_INVALID, null);
    }

    public static OpenApiStatus accountPasswordError() {
        return new OpenApiStatus(PlatformCode.CREDENTIAL_INVALID, null);
    }

    public static OpenApiStatus ipRestricted() {
        return new OpenApiStatus(PlatformCode.IP_RESTRICTED, null);
    }

    public static OpenApiStatus domainRestricted() {
        return new OpenApiStatus(PlatformCode.DOMAIN_RESTRICTED, null);
    }

    public static OpenApiStatus accountDisabled() {
        return new OpenApiStatus(PlatformCode.ACCOUNT_DISABLED, null);
    }

    public static OpenApiStatus qpsConcurrencyExceeded() {
        return new OpenApiStatus(PlatformCode.CAPACITY_EXCEEDED, null);
    }

    public static OpenApiStatus requestTooFrequent() {
        return new OpenApiStatus(PlatformCode.REQUEST_TOO_FREQUENT, null);
    }

    public static OpenApiStatus requestTimeout() {
        return new OpenApiStatus(PlatformCode.REQUEST_TIMEOUT, null);
    }

    public static OpenApiStatus waitingExternal() {
        return new OpenApiStatus(PlatformCode.WAITING_EXTERNAL, null);
    }

    public static OpenApiStatus productUnauthorized() {
        return new OpenApiStatus(PlatformCode.PRODUCT_UNAUTHORIZED, null);
    }

    public static OpenApiStatus dailyQuotaExceeded() {
        return new OpenApiStatus(PlatformCode.DAILY_QUOTA_EXCEEDED, null);
    }

    public static OpenApiStatus monthlyQuotaExceeded() {
        return new OpenApiStatus(PlatformCode.MONTHLY_QUOTA_EXCEEDED, null);
    }

    public static OpenApiStatus requestProductUnauthorized() {
        return new OpenApiStatus(PlatformCode.REQUEST_PRODUCT_UNAUTHORIZED, null);
    }

    public static OpenApiStatus systemError() {
        return new OpenApiStatus(PlatformCode.SYSTEM_ERROR, null);
    }
}
