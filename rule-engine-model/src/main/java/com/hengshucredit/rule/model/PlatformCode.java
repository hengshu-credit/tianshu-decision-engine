package com.hengshucredit.rule.model;

/** 六位字符串 DMMNNN：领域 D、子类 MM、原因 NNN；只有 000000 表示成功。 */
public enum PlatformCode {
    SUCCESS("000000", "成功", 200),
    PARAMETER_INVALID("100001", "入参校验失败", 400),
    REQUIRED_FIELD_MISSING("100002", "必填字段缺失", 400),
    MALFORMED_REQUEST("100003", "请求格式无效", 400),
    METHOD_NOT_ALLOWED("100004", "请求方法不支持", 405),
    MEDIA_TYPE_UNSUPPORTED("100005", "请求内容类型不支持", 415),
    REQUEST_TOO_LARGE("100006", "请求内容超过大小限制", 413),
    REQUEST_REJECTED("100007", "请求内容不满足业务约束", 422),
    RESPONSE_TYPE_UNSUPPORTED("100008", "请求的响应类型不支持", 406),
    RESULT_ERROR("200001", "规则执行失败", 422),
    RULE_NOT_FOUND("201001", "已发布规则不存在", 404),
    LOCAL_EXECUTION_UNSUPPORTED("201002", "规则需要服务端执行", 422),
    RULE_COMPILE_ERROR("201003", "规则编译失败", 422),
    MODEL_EXECUTION_ERROR("202001", "模型执行失败", 422),
    EXPERIMENT_EXECUTION_ERROR("203001", "分流实验执行失败", 422),
    FUNCTION_EXECUTION_ERROR("204001", "函数执行失败", 422),
    TOKEN_INVALID("300001", "Token 已过期或失效", 401),
    CREDENTIAL_INVALID("300002", "鉴权凭据无效", 401),
    IP_RESTRICTED("300003", "IP 不在访问白名单", 403),
    DOMAIN_RESTRICTED("300004", "域名不在访问白名单", 403),
    ACCOUNT_DISABLED("300005", "账户已停用", 403),
    LOGIN_REQUIRED("301001", "登录会话已失效，请重新登录", 401),
    PERMISSION_DENIED("301002", "无操作权限", 403),
    CAPACITY_EXCEEDED("400001", "执行容量或并发超过限制", 429),
    REQUEST_TOO_FREQUENT("400002", "请求过于频繁", 429),
    REQUEST_TIMEOUT("400003", "请求处理超时", 504),
    IDEMPOTENCY_CONFLICT("400004", "幂等键已被不同请求使用", 409),
    EXECUTION_IN_PROGRESS("400005", "请求正在执行", 409),
    WAITING_EXTERNAL("400006", "外数结果尚未就绪，请查询状态或恢复执行", 202),
    EXECUTION_UNKNOWN("400007", "执行结果未知，请查询状态后再决定是否重试", 504),
    RESULT_PENDING("400008", "执行结果尚未就绪", 202),
    PRODUCT_UNAUTHORIZED("500001", "产品无访问权限", 403),
    DAILY_QUOTA_EXCEEDED("500002", "已超过日调用限额", 429),
    MONTHLY_QUOTA_EXCEEDED("500003", "已超过月调用限额", 429),
    REQUEST_PRODUCT_UNAUTHORIZED("500004", "请求产品不存在或未授权", 403),
    RESOURCE_NOT_FOUND("501001", "资源不存在", 404),
    RESOURCE_CONFLICT("501002", "资源状态冲突", 409),
    EXTERNAL_API_ERROR("601001", "外数接口调用失败", 502),
    DATABASE_ERROR("602001", "数据库调用失败", 502),
    LIST_ERROR("603001", "名单操作失败", 502),
    SFTP_ERROR("604001", "文件服务调用失败", 502),
    GOVERNANCE_CONFLICT("700001", "资源治理状态冲突", 409),
    PREFLIGHT_FAILED("700002", "发布前校验未通过", 422),
    SYSTEM_ERROR("900001", "系统执行异常", 500),
    SERVICE_UNAVAILABLE("900002", "服务暂不可用", 503),
    UPSTREAM_RESPONSE_INVALID("900003", "上游响应格式无效", 502),
    UPSTREAM_CALL_FAILED("900004", "上游调用失败", 502);

    private final String code;
    private final String message;
    private final int httpStatus;

    PlatformCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String code() { return code; }
    public String message() { return message; }
    public int httpStatus() { return httpStatus; }

    public static PlatformCode find(String code) {
        if (code == null) return null;
        for (PlatformCode value : values()) if (value.code.equals(code)) return value;
        return null;
    }

    /** 容器与框架 HTTP 错误的兜底映射；业务调用点直接选择明确的平台码。 */
    public static PlatformCode fromHttpStatus(int status) {
        return switch (status) {
            case 200, 201, 204 -> SUCCESS;
            case 202 -> RESULT_PENDING;
            case 400 -> PARAMETER_INVALID;
            case 401 -> TOKEN_INVALID;
            case 403 -> PERMISSION_DENIED;
            case 404 -> RESOURCE_NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 406 -> RESPONSE_TYPE_UNSUPPORTED;
            case 408, 504 -> REQUEST_TIMEOUT;
            case 409 -> RESOURCE_CONFLICT;
            case 413 -> REQUEST_TOO_LARGE;
            case 415 -> MEDIA_TYPE_UNSUPPORTED;
            case 422 -> REQUEST_REJECTED;
            case 429 -> CAPACITY_EXCEEDED;
            case 502 -> UPSTREAM_CALL_FAILED;
            case 503 -> SERVICE_UNAVAILABLE;
            default -> SYSTEM_ERROR;
        };
    }

    public static PlatformCode execution(boolean success, String code, String status) {
        if (success) return SUCCESS;
        PlatformCode explicit = find(code);
        if (explicit != null && explicit != SUCCESS) return explicit;
        if (status == null) return RESULT_ERROR;
        return switch (status) {
            case "WAITING_EXTERNAL" -> WAITING_EXTERNAL;
            case "THROTTLED" -> CAPACITY_EXCEEDED;
            case "UNKNOWN", "UNKNOWN_RETRYABLE" -> EXECUTION_UNKNOWN;
            case "CONFLICT" -> IDEMPOTENCY_CONFLICT;
            case "IN_PROGRESS", "RUNNING" -> EXECUTION_IN_PROGRESS;
            default -> RESULT_ERROR;
        };
    }
}
