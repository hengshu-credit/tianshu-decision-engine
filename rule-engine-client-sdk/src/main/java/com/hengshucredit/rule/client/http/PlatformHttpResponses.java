package com.hengshucredit.rule.client.http;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.model.PlatformCode;
import com.hengshucredit.rule.model.ExternalCodeMapping;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.dto.RuleExperimentExecuteResult;
import okhttp3.Response;
import java.io.IOException;

/** Java SDK 的服务端响应解析器，保留平台码、固定描述、trace 和重试信息。 */
public final class PlatformHttpResponses {
    private PlatformHttpResponses() { }

    public static JSONObject read(Response response) throws IOException {
        if (response.body() == null) throw invalid(response.code());
        JSONObject body;
        try { body = JSON.parseObject(response.body().string()); }
        catch (RuntimeException e) { throw invalid(response.code()); }
        if (body == null || !(body.get("code") instanceof String code)) {
            throw invalid(response.code());
        }
        body.remove("_externalCode"); body.remove("_externalMessage"); body.remove("_mappingVersion");
        String standard = response.header(ExternalCodeMapping.STANDARD_CODE_HEADER);
        PlatformCode status = PlatformCode.find(standard == null ? code : standard);
        if (status == null) throw invalid(response.code());
        if (response.code() != status.httpStatus()
                || !Boolean.valueOf(status == PlatformCode.SUCCESS).equals(body.get("success"))
                || !(body.get("message") instanceof String message)) throw invalid(response.code());
        if (standard == null) {
            if (!status.message().equals(message)) throw invalid(response.code());
        } else {
            try {
                String version = response.header(ExternalCodeMapping.VERSION_HEADER);
                if (version == null || !version.matches("[0-9]{1,18}") || code.isEmpty() || message.isBlank()) throw new IllegalArgumentException();
                new ExternalCodeMapping.Entry(code, message);
                body.put("_externalCode", code); body.put("_externalMessage", message); body.put("_mappingVersion", Long.parseLong(version));
                // 内部同步、鉴权和重试仍读取标准码，公开结果在下面单独应用展示映射。
                body.put("code", status.code()); body.put("message", status.message());
            } catch (IllegalArgumentException error) { throw invalid(response.code()); }
        }
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
        return result.mapExternal(mapping(body));
    }

    public static RuleExperimentExecuteResult experimentResult(Response response, JSONObject body) {
        JSONObject data = executionData(response, body);
        RuleExperimentExecuteResult result;
        try { result = data.toJavaObject(RuleExperimentExecuteResult.class); }
        catch (RuntimeException e) { throw invalid(response.code()); }
        if (!result.isSuccess()) result.setPlatformCode(body.getString("code"));
        if (!result.isSuccess()) result.setErrorMessage(body.getString("detail"));
        if (result.getExperimentTraceId() == null) result.setExperimentTraceId(trace(response, body));
        return result.mapExternal(mapping(body));
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
        RuleHttpException error = new RuleHttpException(body.getString("detail"), response.code(), code, status, trace, retryAfter(response));
        error.mapExternal(mapping(body)); return error;
    }

    public static ExternalCodeMapping mapping(JSONObject body) {
        if (!(body.get("_externalCode") instanceof String code)) return ExternalCodeMapping.DEFAULT;
        return new ExternalCodeMapping(body.getLongValue("_mappingVersion"), true, java.util.Map.of(body.getString("code"),
                new ExternalCodeMapping.Entry(code, body.getString("_externalMessage"))));
    }

    public static com.hengshucredit.rule.model.dto.RuleExecutionStatus executionStatus(Response response, JSONObject body) {
        requireSuccess(response, body);
        if (!(body.get("data") instanceof JSONObject data)) throw invalid(response.code());
        var status = data.toJavaObject(com.hengshucredit.rule.model.dto.RuleExecutionStatus.class);
        if (status.getResult() != null && body.containsKey("_externalCode") && data.get("result") instanceof JSONObject raw) {
            var result = status.getResult();
            if (!(raw.get("code") instanceof String code) || !(raw.get("message") instanceof String message)) throw invalid(response.code());
            result.mapExternal(new ExternalCodeMapping(body.getLongValue("_mappingVersion"), true, java.util.Map.of(result.platformStatus().code(), new ExternalCodeMapping.Entry(code, message))));
        }
        return status;
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
