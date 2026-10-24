package com.hengshucredit.rule.client.http;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.ApiResponse;
import com.hengshucredit.rule.model.PlatformCode;
import okhttp3.*;
import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.*;

public class PlatformHttpResponsesTest {
    @Test public void stateQueryKeepsMappedNestedResultAndItsCanonicalFailure() throws Exception {
        var result = new com.hengshucredit.rule.model.dto.RuleResult(); result.setSuccess(false); result.setPlatformCode("100002");
        var status = new com.hengshucredit.rule.model.dto.RuleExecutionStatus(); status.setStatus("COMPLETED"); status.setResult(result);
        var mapping = new com.hengshucredit.rule.model.ExternalCodeMapping(5, true, Map.of("100002", new com.hengshucredit.rule.model.ExternalCodeMapping.Entry("E1", "参数错误")));
        var envelope = ApiResponse.ok(status).mapExternal(mapping);
        try (var response = response(200, JSON.toJSONString(envelope)).newBuilder()
                .header(com.hengshucredit.rule.model.ExternalCodeMapping.STANDARD_CODE_HEADER, "000000")
                .header(com.hengshucredit.rule.model.ExternalCodeMapping.VERSION_HEADER, "5").build()) {
            var actual = PlatformHttpResponses.executionStatus(response, PlatformHttpResponses.read(response));
            assertEquals("COMPLETED", actual.getStatus()); assertFalse(actual.getResult().isSuccess());
            assertEquals("E1", actual.getResult().getCode()); assertEquals("100002", actual.getResult().platformStatus().code());
        }
    }
    @Test public void sharedExternalCodesRetainDistinctCanonicalStatus() throws Exception {
        for (String code : java.util.List.of("100001", "100002")) {
            var body = JSON.parseObject(JSON.toJSONString(ApiResponse.fail(PlatformCode.find(code), "具体原因")));
            body.put("code", "E1000"); body.put("message", "参数不正确"); body.put("data", Map.of("success", false));
            try (var response = response(400, body.toJSONString()).newBuilder()
                    .header(com.hengshucredit.rule.model.ExternalCodeMapping.STANDARD_CODE_HEADER, code)
                    .header(com.hengshucredit.rule.model.ExternalCodeMapping.VERSION_HEADER, "3").build()) {
                var parsed = PlatformHttpResponses.read(response);
                var result = PlatformHttpResponses.ruleResult(response, parsed);
                assertEquals("E1000", result.getCode()); assertEquals("参数不正确", result.getMessage());
                assertEquals(code, result.platformStatus().code()); assertFalse(result.isSuccess());
                var error = PlatformHttpResponses.error(response, parsed);
                assertEquals("E1000", error.getCode()); assertEquals(code, error.getPlatformCode());
            }
        }
    }

    @Test public void mappedSuccessAndFailureCanShareCodeWithoutBecomingSuccessful() throws Exception {
        var body = JSON.parseObject(JSON.toJSONString(ApiResponse.fail(PlatformCode.TOKEN_INVALID, null)));
        body.put("code", "000000"); body.put("message", "统一返回");
        try (var response = response(401, body.toJSONString()).newBuilder()
                .header(com.hengshucredit.rule.model.ExternalCodeMapping.STANDARD_CODE_HEADER, "300001")
                .header(com.hengshucredit.rule.model.ExternalCodeMapping.VERSION_HEADER, "2").build()) {
            var parsed = PlatformHttpResponses.read(response);
            var error = assertThrows(RuleHttpException.class, () -> PlatformHttpResponses.requireSuccess(response, parsed));
            assertEquals("300001", error.getPlatformCode()); assertEquals("000000", error.getCode());
        }
        try (var response = response(401, body.toJSONString())) {
            assertThrows(RuleHttpException.class, () -> PlatformHttpResponses.read(response));
        }
    }
    private Response response(int status, String body) {
        return new Response.Builder().request(new Request.Builder().url("http://localhost/test").build())
                .protocol(Protocol.HTTP_1_1).code(status).message("test")
                .header("X-Trace-Id", "header-trace").header("Retry-After", "2")
                .body(ResponseBody.create(body, MediaType.get("application/json"))).build();
    }

    @Test public void rejectsContradictoryTransportStatusAndSuccessFlag() throws Exception {
        var failure = ApiResponse.fail(PlatformCode.CAPACITY_EXCEEDED, "容量已满");
        try (var response = response(200, JSON.toJSONString(failure))) {
            assertEquals("900003", assertThrows(RuleHttpException.class, () -> PlatformHttpResponses.read(response)).getCode());
        }
        var body = JSON.parseObject(JSON.toJSONString(ApiResponse.ok(1)));
        body.put("success", false);
        try (var response = response(200, body.toJSONString())) {
            assertThrows(RuleHttpException.class, () -> PlatformHttpResponses.read(response));
        }
    }

    @Test public void executionFailureRetainsEnvelopeDetailsAndTransportMetadata() throws Exception {
        var body = ApiResponse.<Object>fail(PlatformCode.CAPACITY_EXCEEDED, "模型并发已满");
        body.setData(Map.of("success", false));
        try (var response = response(429, JSON.toJSONString(body))) {
            var result = PlatformHttpResponses.ruleResult(response, PlatformHttpResponses.read(response));
            assertEquals("400001", result.getCode());
            assertEquals("模型并发已满", result.getDetail());
            assertEquals("header-trace", result.getTraceId());
            assertEquals(Long.valueOf(2000), result.getRetryAfterMs());
        }
    }
}
