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
    public void rendersOneConfigurableEnvelopeForSuccessAndError() {
        OpenApiContract contract = contract();
        Map<String, Object> successValues = new LinkedHashMap<>();
        successValues.put("output.VARIABLE.201", "PASS");
        successValues.put("output.MODEL.202", 88);
        OpenApiStatus success = new OpenApiStatus(true, "OK", "成功", 200);
        OpenApiStatus error = new OpenApiStatus(false, "REQUEST_INVALID", "字段缺失", 400);

        OpenResponseRenderer.RenderedResponse normal = new OpenResponseRenderer().render(
                contract, success, "trace-1", successValues);
        OpenResponseRenderer.RenderedResponse failed = new OpenResponseRenderer().render(
                contract, error, "trace-2", new LinkedHashMap<String, Object>());

        JSONObject normalBody = (JSONObject) normal.getBody();
        JSONObject errorBody = (JSONObject) failed.getBody();
        Assert.assertEquals(normalBody.keySet(), errorBody.keySet());
        Assert.assertEquals("OK", normalBody.getString("retCode"));
        Assert.assertEquals("REQUEST_INVALID", errorBody.getString("retCode"));
        Assert.assertEquals("trace-1", normalBody.getString("requestId"));
        Assert.assertEquals(88, normalBody.getJSONObject("payload").getInteger("score").intValue());
        Assert.assertEquals("REQUEST_INVALID", errorBody.getJSONObject("payload").getString("errorCode"));
        Assert.assertEquals("trace-1", normal.getHeaders().get("X-Business-Trace"));
    }

    @Test
    public void preservesNativeJsonTypesForExactPlaceholders() {
        OpenApiContract contract = contract();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("output.VARIABLE.201", Arrays.asList("A", "B"));
        values.put("output.MODEL.202", true);

        JSONObject payload = (JSONObject) new OpenResponseRenderer().render(contract,
                new OpenApiStatus(true, "OK", "成功", 200), "trace", values)
                .getBody();

        Assert.assertEquals(Arrays.asList("A", "B"), payload.getJSONObject("payload").getJSONArray("decision"));
        Assert.assertEquals(Boolean.TRUE, payload.getJSONObject("payload").getBoolean("score"));
    }

    @Test
    public void rejectsMissingDataSlotAndUnsafeHeaders() {
        OpenApiContract missingData = contract();
        missingData.setDataPath("$.missing");
        assertRenderFails(missingData, "dataPath");

        OpenApiContract unsafeHeader = contract();
        unsafeHeader.getResponseHeaders().put("Set-Cookie", "session=${traceId}");
        assertRenderFails(unsafeHeader, "Header");
    }

    private void assertRenderFails(OpenApiContract contract, String message) {
        try {
            new OpenResponseRenderer().render(contract,
                    new OpenApiStatus(true, "OK", "成功", 200), "trace",
                    new LinkedHashMap<String, Object>());
            Assert.fail("Expected render to fail");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(message));
        }
    }

    private OpenApiContract contract() {
        OpenApiContract contract = new OpenApiContract();
        contract.setEnvelopeTemplate(JSON.parse("{\"retCode\":\"${status.code}\",\"retMessage\":\"${status.message}\",\"requestId\":\"${traceId}\",\"payload\":\"${data}\"}"));
        contract.setDataPath("$.payload");
        contract.setSuccessDataTemplate(JSON.parse("{\"decision\":\"${output.VARIABLE.201}\",\"score\":\"${output.MODEL.202}\"}"));
        contract.setErrorDataTemplate(JSON.parse("{\"errorCode\":\"${status.code}\",\"errorMessage\":\"${status.message}\"}"));
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Business-Trace", "${traceId}");
        contract.setResponseHeaders(headers);
        return contract;
    }
}
