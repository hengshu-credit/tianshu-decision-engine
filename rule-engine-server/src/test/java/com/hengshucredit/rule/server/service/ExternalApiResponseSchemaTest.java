package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import org.junit.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class ExternalApiResponseSchemaTest {
    @Test public void realInvocationsDropOnlyEditorSamplesAndKeepLiteralPayloadsAndNulls() {
        var configured = ExternalApiTestConfig.api();
        configured.setExecutionConfig("""
                {"version":2,"requestFields":[],"responseSample":{"uiOnly":true},"steps":[{"id":"poll","responseSample":{"state":"DONE"},
                 "responseBranches":[{"id":"map","sample":{"uiOnly":true},"mode":"VALUE",
                 "value":{"kind":"LITERAL","value":{"sample":"actual payload","responseSample":null}}}]}]}
                """);
        configured.setPayloadCaptureConfig("{\"response\":{\"processedSample\":{\"x\":1},\"source\":\"PROCESSED\"}}");
        var runtime = ExternalApiExecutionConfig.forInvocation(configured);
        var step = ExternalApiRequestPlan.specification(runtime).getJSONArray("steps").getJSONObject(0);
        var branch = step.getJSONArray("responseBranches").getJSONObject(0);
        var literal = branch.getJSONObject("value").getJSONObject("value");
        assertFalse(step.containsKey("responseSample"));
        assertFalse(branch.containsKey("sample"));
        assertEquals("actual payload", literal.getString("sample"));
        assertTrue(literal.containsKey("responseSample"));
        assertNull(literal.get("responseSample"));
        assertFalse(runtime.getPayloadCaptureConfig().contains("processedSample"));
        assertEquals("PROCESSED", JSON.parseObject(runtime.getPayloadCaptureConfig()).getJSONObject("response").getString("source"));
        assertTrue(configured.getPayloadCaptureConfig().contains("processedSample"));
        assertTrue(ExternalApiRequestPlan.specification(configured).containsKey("responseSample"));
    }

    @Test public void passthroughEndpointAndStepSamplesExposeStableProtocolPaths() {
        var spec = JSON.parseObject("""
                {"steps":[{"id":"submit","responseSample":{"ticket":"T"}},
                          {"id":"poll","responseSample":{"state":"DONE","Odd.Key":[{"value":1}]}}]}
                """);
        var paths = ExternalApiResponseSchema.catalog(spec).stream().map(f -> f.get("value")).toList();
        assertTrue(paths.contains("steps.submit.body.ticket"));
        assertTrue(paths.contains("steps.poll.body.state"));
        assertTrue(paths.contains("body[\"Odd.Key\"][0].value"));
        assertFalse(paths.contains("body.ticket"));
        assertEquals("DONE", ExternalApiConsumerService.select(ExternalApiResponseSchema.sampleEnvelopes(spec).get(0), "body.state"));
    }

    @Test public void mappedSubtreeInheritsTheStepSampleWithoutDuplicatingItOnTheBranch() {
        var spec = JSON.parseObject("""
                {"steps":[{"id":"fetch","responseSample":{"data":{"Score":82}},"responseBranches":[
                  {"id":"map","mode":"VALUE","value":{"kind":"PATH","value":"body.data"}}]}]}
                """);
        var fields = ExternalApiResponseSchema.catalog(spec);
        assertTrue(fields.stream().anyMatch(f -> "steps.fetch.body.Score".equals(f.get("value")) && "NUMBER".equals(f.get("type"))));
        assertTrue(fields.stream().anyMatch(f -> "body.Score".equals(f.get("value"))));
        assertEquals(82, ExternalApiConsumerService.select(ExternalApiResponseSchema.sampleEnvelopes(spec).get(0), "body.Score"));
    }

    @Test public void catalogDescribesOutputsAndStepsWithoutExecutingExpressions() {
        var spec = JSON.parseObject("""
                {"responseBranches":[{"mode":"FIELDS","outputFields":[
                  {"path":"risk.score","value":{"kind":"FUNCTION","functionCode":"must_not_execute","valueType":"NUMBER","args":[]}}
                ]}],"steps":[{"id":"first","responseBranches":[{"mode":"VALUE","sample":{"items":[{"code":"ok"}]},
                  "value":{"kind":"PATH","value":"response.body.items"}}]}]}
                """);
        var fields = ExternalApiResponseSchema.catalog(spec);
        assertTrue(fields.stream().anyMatch(field -> "body.risk.score".equals(field.get("value")) && "NUMBER".equals(field.get("type"))));
        assertTrue(fields.stream().anyMatch(field -> "steps.first.body[0].code".equals(field.get("value")) && "STRING".equals(field.get("type"))));
        assertTrue(fields.stream().anyMatch(field -> "steps.first.response.body.items[0].code".equals(field.get("value"))));
    }
    @Test public void wholeCompanyDataRetainsAllFeatureFieldsAndTheirTypes() {
        Map<String, Object> features = new LinkedHashMap<>();
        for (int i = 0; i < 9937; i++) features.put("QY_FIELD_" + i, i);
        features.put("原始.字段", false);
        var data = Map.of("securityComputingResult", features, "code", "0000", "ErrorInfo", "");
        var branch = JSON.parseObject(JSON.toJSONString(Map.of("id", "normal", "mode", "VALUE", "value", Map.of("kind", "PATH", "value", "response.body.data"), "sample", Map.of("code", "0000", "data", data))));
        assertEquals(data, ExternalApiResponseSchema.sampleValue(branch));
        var spec = JSON.parseObject(JSON.toJSONString(Map.of("responseBranches", List.of(branch))));
        var fields = ExternalApiResponseSchema.catalog(spec);
        assertTrue(fields.stream().anyMatch(field -> "body.securityComputingResult.QY_FIELD_9936".equals(field.get("value")) && "NUMBER".equals(field.get("type"))));
        assertTrue(fields.stream().anyMatch(field -> "body.securityComputingResult[\"原始.字段\"]".equals(field.get("value")) && "BOOLEAN".equals(field.get("type"))));
    }
    @Test public void wholeValueMappingUsesConfiguredOutputPath() {
        var branch = JSON.parseObject(JSON.toJSONString(Map.of("id", "mapped", "mode", "VALUE", "outputPath", "data.score",
                "value", Map.of("kind", "PATH", "value", "response.body.score"), "sample", Map.of("score", 88))));
        var spec = JSON.parseObject(JSON.toJSONString(Map.of("responseBranches", List.of(branch))));
        var fields = ExternalApiResponseSchema.catalog(spec);
        assertTrue(fields.stream().anyMatch(field -> "body.data.score".equals(field.get("value"))));
    }
}
