package com.hengshucredit.rule.server.openapi;

/** 开放接口稳定业务状态码。HTTP 状态用于传输语义，code 用于下游业务判断。 */
public final class OpenApiStatuses {

    public static final String SUCCESS = "000000";
    public static final String PARAMETER_VALIDATION_ERROR = "100001";
    public static final String REQUIRED_FIELD_MISSING = "100002";
    public static final String RESULT_ERROR = "200001";
    public static final String TOKEN_EXPIRED = "300001";
    public static final String ACCOUNT_PASSWORD_ERROR = "300002";
    public static final String IP_RESTRICTED = "300003";
    public static final String DOMAIN_RESTRICTED = "300004";
    public static final String ACCOUNT_DISABLED = "300005";
    public static final String QPS_CONCURRENCY_EXCEEDED = "400001";
    public static final String REQUEST_TOO_FREQUENT = "400002";
    public static final String REQUEST_TIMEOUT = "400003";
    public static final String PRODUCT_UNAUTHORIZED = "500001";
    public static final String DAILY_QUOTA_EXCEEDED = "500002";
    public static final String MONTHLY_QUOTA_EXCEEDED = "500003";
    public static final String REQUEST_PRODUCT_UNAUTHORIZED = "500004";
    public static final String SYSTEM_ERROR = "900001";

    private OpenApiStatuses() {
    }

    public static OpenApiStatus success() {
        return status(true, SUCCESS, "成功", 200);
    }

    public static OpenApiStatus parameterValidation(String message) {
        return status(false, PARAMETER_VALIDATION_ERROR, fallback(message, "入参校验失败"), 400);
    }

    public static OpenApiStatus requiredField(String message) {
        return status(false, REQUIRED_FIELD_MISSING, fallback(message, "必填字段缺失"), 400);
    }

    public static OpenApiStatus resultError(String message) {
        return status(false, RESULT_ERROR, fallback(message, "结果处理异常"), 500);
    }

    public static OpenApiStatus tokenExpired() {
        return status(false, TOKEN_EXPIRED, "Token 已过期或失效", 401);
    }

    public static OpenApiStatus accountPasswordError() {
        return status(false, ACCOUNT_PASSWORD_ERROR, "账户或密码错误", 401);
    }

    public static OpenApiStatus ipRestricted() {
        return status(false, IP_RESTRICTED, "IP 不在访问白名单", 403);
    }

    public static OpenApiStatus domainRestricted() {
        return status(false, DOMAIN_RESTRICTED, "域名不在访问白名单", 403);
    }

    public static OpenApiStatus accountDisabled() {
        return status(false, ACCOUNT_DISABLED, "账户已停用", 403);
    }

    public static OpenApiStatus qpsConcurrencyExceeded() {
        return status(false, QPS_CONCURRENCY_EXCEEDED, "QPS 或并发超过限制", 429);
    }

    public static OpenApiStatus requestTooFrequent() {
        return status(false, REQUEST_TOO_FREQUENT, "请求过于频繁", 429);
    }

    public static OpenApiStatus requestTimeout() {
        return status(false, REQUEST_TIMEOUT, "请求处理超时", 504);
    }

    public static OpenApiStatus productUnauthorized() {
        return status(false, PRODUCT_UNAUTHORIZED, "产品无访问权限", 403);
    }

    public static OpenApiStatus dailyQuotaExceeded() {
        return status(false, DAILY_QUOTA_EXCEEDED, "已超过日调用限额", 429);
    }

    public static OpenApiStatus monthlyQuotaExceeded() {
        return status(false, MONTHLY_QUOTA_EXCEEDED, "已超过月调用限额", 429);
    }

    public static OpenApiStatus requestProductUnauthorized() {
        return status(false, REQUEST_PRODUCT_UNAUTHORIZED, "请求产品不存在或未授权", 403);
    }

    public static OpenApiStatus systemError() {
        return status(false, SYSTEM_ERROR, "系统执行异常", 500);
    }

    private static OpenApiStatus status(boolean success, String code, String message, int httpStatus) {
        return new OpenApiStatus(success, code, message, httpStatus);
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
