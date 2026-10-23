package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.model.entity.RuleDataObject;

import java.util.Locale;
import java.util.Set;

/** 保存、审批和草稿试调用使用同一份外数协议校验。 */
public final class ExternalApiConfigValidator {
    private static final Set<String> CONDITION_OPERATORS = Set.of(
            "*", "==", "eq", "!=", "<>", "ne", ">", ">=", "<", "<=",
            "between", "not_between", "contains", "not_contains", "contains_any", "contains_all",
            "starts_with", "not_starts_with", "ends_with", "not_ends_with",
            "regex", "matches", "string_matches", "not_regex", "not_matches",
            "in", "not_in", "has_key", "not_has_key",
            "missing", "not_exists", "absent", "exists", "present",
            "is_null", "not_null", "is_empty", "blank", "not_empty", "not_blank",
            "type_is", "type_not", "type_changed", "is_true", "is_false");
    private ExternalApiConfigValidator() { }

    public static void validateEditableConfig(RuleExternalApiConfig config) {
        if (java.util.stream.Stream.of(config.getHeaderConfig(), config.getQueryConfig(), config.getRequestMapping(),
                config.getResponseMapping(), config.getBodyTemplate(), config.getAsyncPollConfig(), config.getAsyncCallbackConfig(),
                config.getAsyncCallbackUrl(), config.getAsyncResultPath(), config.getAsyncResultMode()).anyMatch(ExternalApiConfigValidator::hasText)) {
            throw new IllegalArgumentException("请求、响应和异步步骤必须在 executionConfig 中配置");
        }
        validate(config);
    }

    public static void validate(RuleExternalApiConfig config) {
        if (ExternalApiRequestPlan.specification(config) == null) {
            throw new IllegalArgumentException("外数 API 必须使用 V2 executionConfig");
        }
        ExternalApiExecutionConfig.validate(config);
        ExternalApiAuthConfig.validate(config.getAuthApiConfig(), null);
        if (hasText(config.getExceptionStrategy())) {
            String strategy = config.getExceptionStrategy().trim().toUpperCase(Locale.ROOT);
            if (!Set.of("FAIL_FAST", "RETURN_DEFAULT", "IGNORE", "USE_CACHE", "SKIP", "BREAK", "WAIT", "RETRY").contains(strategy)) {
                throw new IllegalArgumentException("异常处理策略不受支持: " + config.getExceptionStrategy());
            }
            if ("RETRY".equals(strategy) && !hasRetryAttempt(config)) {
                throw new IllegalArgumentException("选择异常重试时必须配置至少 1 次重试");
            }
        }
        if (hasText(config.getAuthMode())) {
            String authMode = config.getAuthMode().trim().toUpperCase(Locale.ROOT);
            if (!Set.of("INHERIT", "NONE", "BASIC", "BEARER", "API_KEY",
                    "OAUTH2", "TOKEN_API", "CUSTOM").contains(authMode)) {
                throw new IllegalArgumentException("接口鉴权方式不受支持: " + config.getAuthMode());
            }
        }
        if (hasText(config.getSuccessCondition())) {
            validateCondition(parseCondition(config.getSuccessCondition(), "成功条件"), "成功条件");
        }
        if (hasText(config.getExceptionCondition())) {
            validateCondition(parseCondition(config.getExceptionCondition(), "接口异常条件"), "接口异常条件");
        }
        if (hasText(config.getRetryCondition())) {
            validateCondition(parseCondition(config.getRetryCondition(), "重试条件"), "重试条件");
        }
        if (hasText(config.getTokenFailureCondition())) {
            validateCondition(parseCondition(config.getTokenFailureCondition(), "Token鉴权失败条件"), "Token鉴权失败条件");
        }
        if (hasText(config.getBillingCondition())) {
            JSONObject billing = parseCondition(config.getBillingCondition(), "计费条件");
            String billingMode = billing.getString("mode");
            if (billingMode != null && !Set.of("QUERY", "HIT").contains(billingMode.trim().toUpperCase(Locale.ROOT))) {
                throw new IllegalArgumentException("计费模式只能是 QUERY 或 HIT");
            }
            if (!"QUERY".equalsIgnoreCase(billingMode)) {
                validateCondition(billing.containsKey("condition") ? billing.getJSONObject("condition") : billing, "计费条件");
            }
        }
        if (hasText(config.getPayloadCaptureConfig())) {
            ExternalApiPayloadCapturePolicy.validate(config.getPayloadCaptureConfig());
        }
        String requestMode = config.getRequestMode() == null ? "SYNC"
                : config.getRequestMode().trim().toUpperCase(Locale.ROOT);
        if (!"SYNC".equals(requestMode) && !"ASYNC".equals(requestMode)) {
            throw new IllegalArgumentException("请求模式只能是SYNC或ASYNC");
        }
        if (!"ASYNC".equals(requestMode)) return;
        int asyncTimeoutMs = config.getAsyncTimeoutMs() == null ? 30_000 : config.getAsyncTimeoutMs();
        if ((asyncTimeoutMs > 0 && asyncTimeoutMs < 1_000) || asyncTimeoutMs > 86_400_000) {
            throw new IllegalArgumentException("异步等待超时必须在 0 到 86400000 毫秒之间，0 表示持续等待");
        }
        JSONObject execution = ExternalApiRequestPlan.specification(config);
        if (execution != null) {
            if (execution.getJSONArray("steps") == null || execution.getJSONArray("steps").isEmpty()) throw new IllegalArgumentException("异步接口请至少添加一个链路步骤");
            return;
        }
        throw new IllegalArgumentException("异步接口必须配置执行链路");
    }

    /** 校验外数接口引用的数据源和请求/响应对象属于同一可见作用域。 */
    public static void validateReferences(RuleExternalApiConfig config,
                                           RuleExternalDatasource datasource,
                                           RuleDataObject requestObject,
                                           RuleDataObject responseObject) {
        if (config == null || config.getDatasourceId() == null) {
            throw new IllegalArgumentException("接口必须选择所属数据源");
        }
        if (datasource == null || !active(datasource.getStatus())) {
            throw new IllegalArgumentException("接口所属数据源不存在或已停用");
        }
        validateObject(requestObject, datasource, "请求数据对象");
        validateObject(responseObject, datasource, "响应数据对象");
    }

    private static void validateObject(RuleDataObject object, RuleExternalDatasource datasource,
                                       String label) {
        if (object == null) return;
        if (!active(object.getStatus())) {
            throw new IllegalArgumentException(label + "不存在或已停用");
        }
        String scope = object.getScope() == null ? "" : object.getScope().trim().toUpperCase(Locale.ROOT);
        boolean global = "GLOBAL".equals(scope) || object.getProjectId() == null || object.getProjectId() == 0L;
        Long ownerProjectId = datasource.getProjectId();
        if (!global && (ownerProjectId == null || !ownerProjectId.equals(object.getProjectId()))) {
            throw new IllegalArgumentException(label + "必须是全局对象或与数据源属于同一项目");
        }
    }

    private static boolean active(Integer status) {
        return status == null || status == 1;
    }

    static int positive(JSONObject config, String field, int fallback) {
        Object raw = config.get(field);
        if (raw == null) return fallback;
        try {
            int value = new java.math.BigDecimal(String.valueOf(raw)).intValueExact();
            if (value > 0) return value;
        } catch (RuntimeException ignored) {
            // 禁止小数和超出整型范围的配置。
        }
        throw new IllegalArgumentException("异步 " + field + " 必须是正整数");
    }

    static int nonNegative(JSONObject config, String field, int fallback) {
        Object raw = config.get(field);
        if (raw == null) return fallback;
        try {
            int value = new java.math.BigDecimal(String.valueOf(raw)).intValueExact();
            if (value >= 0) return value;
        } catch (RuntimeException ignored) {
            // 禁止小数和超出整型范围的配置。
        }
        throw new IllegalArgumentException("异步 " + field + " 必须是大于或等于 0 的整数");
    }

    private static JSONObject parseCondition(String text, String label) {
        try {
            return JSON.parseObject(text);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException(label + "必须是合法 JSON", error);
        }
    }

    static void validateCondition(Object raw, String label) {
        if (raw == null) return;
        JSONObject condition;
        if (raw instanceof JSONObject object) condition = object;
        else if (raw instanceof String text) condition = parseCondition(text, label);
        else {
            try {
                condition = JSON.parseObject(JSON.toJSONString(raw));
            } catch (RuntimeException error) {
                throw new IllegalArgumentException(label + "必须是合法条件对象", error);
            }
        }
        validateCondition(condition, label);
    }

    private static void validateCondition(JSONObject condition, String label) {
        if (condition == null || condition.isEmpty()) throw new IllegalArgumentException(label + "不能为空");
        if (condition.containsKey("condition")) {
            validateCondition(condition.getJSONObject("condition"), label);
        } else if ("group".equalsIgnoreCase(condition.getString("type"))) {
            var children = condition.getJSONArray("children");
            if (children == null || children.isEmpty()) throw new IllegalArgumentException(label + "至少需要一个判断条件");
            String groupOperator = hasText(condition.getString("operator"))
                    ? condition.getString("operator") : condition.getString("op");
            if (groupOperator != null && !Set.of("AND", "OR").contains(groupOperator.trim().toUpperCase(Locale.ROOT))) {
                throw new IllegalArgumentException(label + "条件组运算符只能是 AND 或 OR");
            }
            for (int i = 0; i < children.size(); i++) validateCondition(children.getJSONObject(i), label);
        } else {
            String path = condition.getString("path");
            if (!hasText(path)) path = condition.getString("field");
            if (!hasText(path)) path = condition.getString("varCode");
            boolean operandLeft = condition.get("left") != null;
            if (!operandLeft) required(path, label + "响应字段路径");
            String operator = hasText(condition.getString("operator"))
                    ? condition.getString("operator").trim().toLowerCase(Locale.ROOT) : "==";
            if (!CONDITION_OPERATORS.contains(operator)) {
                throw new IllegalArgumentException(label + "不支持运算符: " + condition.getString("operator"));
            }
            if (Set.of("type_is", "type_not", "type_changed").contains(operator)) {
                if (!hasText(condition.getString("value")) && condition.get("right") == null) {
                    required(condition.getString("value"), label + "类型值");
                }
            }
        }
    }

    private static void required(String value, String label) {
        if (!hasText(value)) throw new IllegalArgumentException(label + "不能为空");
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }

    private static boolean hasRetryAttempt(RuleExternalApiConfig config) {
        if (config.getRetryCount() != null && config.getRetryCount() > 0) return true;
        JSONObject execution = ExternalApiRequestPlan.specification(config);
        if (execution == null || execution.getJSONArray("retryBranches") == null) return false;
        for (Object raw : execution.getJSONArray("retryBranches")) {
            JSONObject branch = JSON.parseObject(JSON.toJSONString(raw));
            if (branch.getIntValue("retryCount") > 0) return true;
        }
        return false;
    }
}
