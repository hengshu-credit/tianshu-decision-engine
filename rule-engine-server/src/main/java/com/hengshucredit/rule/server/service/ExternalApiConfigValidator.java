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
    private ExternalApiConfigValidator() { }

    public static void validate(RuleExternalApiConfig config) {
        if (hasText(config.getExceptionStrategy())) {
            String strategy = config.getExceptionStrategy().trim().toUpperCase(Locale.ROOT);
            if (!Set.of("FAIL_FAST", "RETURN_DEFAULT", "IGNORE", "USE_CACHE").contains(strategy)) {
                throw new IllegalArgumentException("异常处理策略不受支持: " + config.getExceptionStrategy());
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
        if (hasText(config.getRetryCondition())) {
            validateCondition(parseCondition(config.getRetryCondition(), "重试条件"), "重试条件");
        }
        if (hasText(config.getTokenFailureCondition())) {
            validateCondition(parseCondition(config.getTokenFailureCondition(), "Token鉴权失败条件"), "Token鉴权失败条件");
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
        String mode = config.getAsyncResultMode() == null ? ""
                : config.getAsyncResultMode().trim().toUpperCase(Locale.ROOT);
        if (!"POLL".equals(mode) && !"CALLBACK".equals(mode)) {
            throw new IllegalArgumentException("异步接口必须选择引擎轮询或外部回调");
        }
        JSONObject protocol = JSON.parseObject("POLL".equals(mode)
                ? config.getAsyncPollConfig() : config.getAsyncCallbackConfig());
        if (protocol == null) throw new IllegalArgumentException("请填写异步结果获取配置");
        required(protocol.getString("taskIdPath"), "任务号路径");
        required(protocol.getString("statusPath"), "异步状态路径");
        required(protocol.getString("successValue"), "异步成功值");
        if (hasText(protocol.getString("failureValue"))
                && protocol.getString("failureValue").equals(protocol.getString("successValue"))) {
            throw new IllegalArgumentException("异步成功值和失败值不能相同");
        }
        if ("POLL".equals(mode)) {
            required(protocol.getString("resultEndpointUrl"), "结果查询地址");
            positive(protocol, "intervalMs", 3000);
            positive(protocol, "maxAttempts", 20);
            for (String field : new String[]{"headerConfig", "queryConfig", "requestMapping"}) {
                Object value = protocol.get(field);
                if (value != null && !(value instanceof java.util.Map)) {
                    throw new IllegalArgumentException("轮询 " + field + " 必须是 JSON 对象");
                }
            }
        } else {
            required(config.getAsyncCallbackUrl(), "引擎公网回调地址模板");
            if (!config.getAsyncCallbackUrl().matches("https?://.+/api/external-callback/\\$\\{invocationId}")) {
                throw new IllegalArgumentException("回调地址必须是 http(s)://公网地址/api/external-callback/${invocationId}");
            }
            required(protocol.getString("signatureHeader"), "回调签名 Header");
            required(protocol.getString("signatureSecret"), "回调签名密钥");
        }
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

    private static JSONObject parseCondition(String text, String label) {
        try {
            return JSON.parseObject(text);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException(label + "必须是合法 JSON", error);
        }
    }

    private static void validateCondition(JSONObject condition, String label) {
        if (condition == null || condition.isEmpty()) throw new IllegalArgumentException(label + "不能为空");
        if (condition.containsKey("condition")) {
            validateCondition(condition.getJSONObject("condition"), label);
        } else if ("group".equalsIgnoreCase(condition.getString("type"))) {
            var children = condition.getJSONArray("children");
            if (children == null || children.isEmpty()) throw new IllegalArgumentException(label + "至少需要一个判断条件");
            for (int i = 0; i < children.size(); i++) validateCondition(children.getJSONObject(i), label);
        } else {
            String path = condition.getString("path");
            if (!hasText(path)) path = condition.getString("field");
            if (!hasText(path)) path = condition.getString("varCode");
            required(path, label + "响应字段路径");
        }
    }

    private static void required(String value, String label) {
        if (!hasText(value)) throw new IllegalArgumentException(label + "不能为空");
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
}
