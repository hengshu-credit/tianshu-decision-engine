package com.hengshucredit.rule.client.http;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.model.PlatformCode;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.dto.RuleExperimentExecuteResult;
import okhttp3.Response;
import java.io.IOException;

/** 两种 Java 客户端共用解析器，保留平台码、固定描述、trace 和重试信息。 */
public final class PlatformHttpResponses {
    private PlatformHttpResponses() { }

    public static JSONObject read(Response response) throws IOException {
        if (response.body() == null) throw invalid(response.code());
        JSONObject body;
        try { body = JSON.parseObject(response.body().string()); }
        catch (RuntimeException e) { throw invalid(response.code()); }
        if (body == null || !(body.get("code") instanceof String code) || PlatformCode.find(code) == null) {
            throw invalid(response.code());
        }
        PlatformCode status = PlatformCode.find(body.getString("code"));
        if (response.code() != status.httpStatus()
                || !Boolean.valueOf(status == PlatformCode.SUCCESS).equals(body.get("success"))
                || !status.message().equals(body.get("message"))) throw invalid(response.code());
        return body;
    }

    public static void requireSuccess(Response response, JSONObject body) {
        if (!response.isSuccessful() && PlatformCode.SUCCESS.code().equals(body.getString("code"))) throw invalid(response.code());
        if (!response.isSuccessful() || !PlatformCode.SUCCESS.code().equals(body.getString("code"))) {
            throw error(response, body);
        }
    }

    public static RuleResult ruleResult(Response response, JSONObject body) {
        JSONObject data = executionData(response, body);
        String code = body.getString("code");
        RuleResult result;
        try { result = data.toJavaObject(RuleResult.class); }
        catch (RuntimeException e) { throw invalid(response.code()); }
        if (!result.isSuccess()) result.setPlatformCode(code);
        if (!result.isSuccess()) result.setErrorMessage(body.getString("detail"));
        if (result.getTraceId() == null) result.setTraceId(trace(response, body));
        if (result.getRetryAfterMs() == null) result.setRetryAfterMs(retryAfter(response));
        return result;
    }

    public static RuleExperimentExecuteResult experimentResult(Response response, JSONObject body) {
        JSONObject data = executionData(response, body);
        RuleExperimentExecuteResult result;
        try { result = data.toJavaObject(RuleExperimentExecuteResult.class); }
        catch (RuntimeException e) { throw invalid(response.code()); }
        if (!result.isSuccess()) result.setPlatformCode(body.getString("code"));
        if (!result.isSuccess()) result.setErrorMessage(body.getString("detail"));
        if (result.getExperimentTraceId() == null) result.setExperimentTraceId(trace(response, body));
        return result;
    }

    private static JSONObject executionData(Response response, JSONObject body) {
        if (!(body.get("data") instanceof JSONObject data) || !(data.get("success") instanceof Boolean)) {
            requireSuccess(response, body);
            throw invalid(response.code());
        }
        if (data.getBooleanValue("success") != PlatformCode.SUCCESS.code().equals(body.getString("code"))) throw invalid(response.code());
        if (data.getBooleanValue("success") && !response.isSuccessful()) throw invalid(response.code());
        return data;
    }

    public static RuleHttpException error(Response response, JSONObject body) {
        String code = body.getString("code");
        JSONObject data = body.get("data") instanceof JSONObject object ? object : null;
        String status = data == null ? null : data.getString("executionStatus");
        if (status == null) {
            status = switch (PlatformCode.find(code)) {
                case IDEMPOTENCY_CONFLICT -> "CONFLICT";
                case EXECUTION_IN_PROGRESS -> "IN_PROGRESS";
                case WAITING_EXTERNAL -> "WAITING_EXTERNAL";
                case EXECUTION_UNKNOWN, REQUEST_TIMEOUT -> "UNKNOWN";
                case CAPACITY_EXCEEDED, REQUEST_TOO_FREQUENT -> "THROTTLED";
                default -> "REJECTED";
            };
        }
        String trace = trace(response, body);
        if (trace == null && data != null) trace = data.getString("traceId");
        return new RuleHttpException(body.getString("detail"), response.code(), code, status, trace, retryAfter(response));
    }

    private static String trace(Response response, JSONObject body) {
        String trace = body.getString("traceId");
        return trace == null || trace.isBlank() ? response.header("X-Trace-Id") : trace;
    }

    private static Long retryAfter(Response response) {
        try {
            if (response.header("Retry-After") != null) return Math.multiplyExact(Math.max(0, Long.parseLong(response.header("Retry-After"))), 1000);
        } catch (ArithmeticException | NumberFormatException ignored) { }
        return null;
    }

    public static RuleHttpException invalid(int httpStatus) {
        return new RuleHttpException(null, httpStatus, PlatformCode.UPSTREAM_RESPONSE_INVALID.code());
    }
}
