package com.hengshucredit.rule.server.openapi;

import com.alibaba.fastjson.JSON;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class OpenRequestMapperTest {

    @Test
    public void mapsBodyAndHeadersToCurrentScriptNamesByStableVariableId() {
        OpenApiContract.RequestMapping idNo = mapping(101L, "BODY", "$.customer.id_no", true, null, "STRING");
        OpenApiContract.RequestMapping score = mapping(102L, "HEADER", "X-Risk-Score", true, null, "NUMBER");
        OpenApiContract contract = new OpenApiContract();
        contract.setRequestMappings(Arrays.asList(idNo, score));
        Map<String, String> targetNames = new LinkedHashMap<>();
        targetNames.put("VARIABLE:101", "CurrentIdCode");
        targetNames.put("VARIABLE:102", "RiskScore");
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("x-risk-score", "620.5");

        Map<String, Object> result = new OpenRequestMapper().map(contract,
                JSON.parse("{\"customer\":{\"id_no\":\"A001\"}}"), headers, targetNames);

        Assert.assertEquals("A001", result.get("CurrentIdCode"));
        Assert.assertEquals(new BigDecimal("620.5"), result.get("RiskScore"));
        Assert.assertFalse(result.containsKey("id_no"));
    }

    @Test
    public void appliesDefaultAndRejectsMissingRequiredOrUnknownVariableId() {
        OpenApiContract contract = new OpenApiContract();
        contract.setRequestMappings(Arrays.asList(
                mapping(101L, "BODY", "$.age", false, "18", "INTEGER")));
        Map<String, String> targetNames = new LinkedHashMap<>();
        targetNames.put("VARIABLE:101", "age");

        Map<String, Object> result = new OpenRequestMapper().map(contract,
                JSON.parse("{}"), new LinkedHashMap<String, String>(), targetNames);
        Assert.assertEquals(18, result.get("age"));

        contract.setRequestMappings(Arrays.asList(mapping(102L, "BODY", "$.name", true, null, "STRING")));
        targetNames.put("VARIABLE:102", "name");
        assertMappingFails(contract, targetNames, "必填");
        targetNames.put("VARIABLE:102", null);
        assertMappingFails(contract, targetNames, "ID");
    }

    private void assertMappingFails(OpenApiContract contract, Map<String, String> targetNames, String message) {
        try {
            new OpenRequestMapper().map(contract, JSON.parse("{}"),
                    new LinkedHashMap<String, String>(), targetNames);
            Assert.fail("Expected mapping to fail");
        } catch (OpenApiException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(message));
            Assert.assertEquals("100002", expected.getStatus().getCode());
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(message));
        }
    }

    private OpenApiContract.RequestMapping mapping(Long varId, String sourceType, String sourcePath,
                                                    boolean required, String defaultValue, String targetType) {
        OpenApiContract.RequestMapping mapping = new OpenApiContract.RequestMapping();
        mapping.setTargetVarId(varId);
        mapping.setTargetRefType("VARIABLE");
        mapping.setSourceType(sourceType);
        mapping.setSourcePath(sourcePath);
        mapping.setRequired(required);
        mapping.setDefaultValue(defaultValue);
        mapping.setTargetType(targetType);
        return mapping;
    }
}
