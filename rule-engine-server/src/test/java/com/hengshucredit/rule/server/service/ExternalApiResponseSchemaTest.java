package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import org.junit.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class ExternalApiResponseSchemaTest {
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
