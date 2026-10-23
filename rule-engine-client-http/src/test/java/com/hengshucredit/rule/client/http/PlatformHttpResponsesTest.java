package com.hengshucredit.rule.client.http;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.ApiResponse;
import com.hengshucredit.rule.model.PlatformCode;
import okhttp3.*;
import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.*;

public class PlatformHttpResponsesTest {
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
