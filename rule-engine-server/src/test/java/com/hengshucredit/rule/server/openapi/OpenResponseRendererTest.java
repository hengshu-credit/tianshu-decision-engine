package com.hengshucredit.rule.server.openapi;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class OpenResponseRendererTest {

    @Test
    public void platformJudgementFieldsAreCanonical() {
        OpenApiContract contract = contract();
        JSONObject body = (JSONObject) new OpenResponseRenderer().render(contract,
                new OpenApiStatus(com.hengshucredit.rule.model.PlatformCode.PARAMETER_INVALID, "年龄缺失"),
                "trace-1", Map.of()).getBody();
        Assert.assertEquals("100001", body.getString("code"));
        Assert.assertEquals("入参校验失败", body.getString("message"));
        Assert.assertEquals("年龄缺失", body.getString("detail"));
        Assert.assertFalse(body.getBooleanValue("success"));
        Assert.assertEquals(java.util.Set.of("code", "message", "detail", "success", "traceId", "data"), body.keySet());
    }

    @Test
    public void rendersFixedEnvelopeForSuccessAndError() {
        OpenApiContract contract = contract();
        Map<String, Object> successValues = new LinkedHashMap<>();
        successValues.put("output.VARIABLE.201", "PASS");
        successValues.put("output.MODEL.202", 88);
        OpenApiStatus success = new OpenApiStatus(com.hengshucredit.rule.model.PlatformCode.SUCCESS, null);
        OpenApiStatus error = new OpenApiStatus(com.hengshucredit.rule.model.PlatformCode.PARAMETER_INVALID, "字段缺失");

        OpenResponseRenderer.RenderedResponse normal = new OpenResponseRenderer().render(
                contract, success, "trace-1", successValues);
        OpenResponseRenderer.RenderedResponse failed = new OpenResponseRenderer().render(
                contract, error, "trace-2", new LinkedHashMap<String, Object>());

        JSONObject normalBody = (JSONObject) normal.getBody();
        JSONObject errorBody = (JSONObject) failed.getBody();
        Assert.assertEquals(normalBody.keySet(), errorBody.keySet());
        Assert.assertEquals("000000", normalBody.getString("code"));
        Assert.assertEquals("100001", errorBody.getString("code"));
        Assert.assertEquals("trace-1", normalBody.getString("traceId"));
        Assert.assertEquals(88, normalBody.getJSONObject("data").getInteger("score").intValue());
        Assert.assertEquals("100001", errorBody.getJSONObject("data").getString("errorCode"));
        Assert.assertEquals("trace-1", normal.getHeaders().get("X-Business-Trace"));
    }

    @Test
    public void preservesNativeJsonTypesForExactPlaceholders() {
        OpenApiContract contract = contract();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("output.VARIABLE.201", Arrays.asList("A", "B"));
        values.put("output.MODEL.202", true);

        JSONObject payload = (JSONObject) new OpenResponseRenderer().render(contract,
                new OpenApiStatus(com.hengshucredit.rule.model.PlatformCode.SUCCESS, null), "trace", values)
                .getBody();

        Assert.assertEquals(Arrays.asList("A", "B"), payload.getJSONObject("data").getJSONArray("decision"));
        Assert.assertEquals(Boolean.TRUE, payload.getJSONObject("data").getBoolean("score"));
    }

    @Test
    public void rejectsUnsafeHeaders() {
        OpenApiContract unsafeHeader = contract();
        unsafeHeader.getResponseHeaders().put("Set-Cookie", "session=${traceId}");
        assertRenderFails(unsafeHeader, "Header");
    }

    private void assertRenderFails(OpenApiContract contract, String message) {
        try {
            new OpenResponseRenderer().render(contract,
                    new OpenApiStatus(com.hengshucredit.rule.model.PlatformCode.SUCCESS, null), "trace",
                    new LinkedHashMap<String, Object>());
            Assert.fail("Expected render to fail");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(message));
        }
    }

    private OpenApiContract contract() {
        OpenApiContract contract = new OpenApiContract();
        contract.setSuccessDataTemplate(JSON.parse("{\"decision\":\"${output.VARIABLE.201}\",\"score\":\"${output.MODEL.202}\"}"));
        contract.setErrorDataTemplate(JSON.parse("{\"errorCode\":\"${status.code}\",\"errorMessage\":\"${status.message}\"}"));
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Business-Trace", "${traceId}");
        contract.setResponseHeaders(headers);
        return contract;
    }
}
