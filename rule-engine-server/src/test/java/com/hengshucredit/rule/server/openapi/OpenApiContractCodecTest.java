package com.hengshucredit.rule.server.openapi;

import org.junit.Assert;
import org.junit.Test;

public class OpenApiContractCodecTest {

    private static final String RESPONSE = "\"envelopeTemplate\":{\"data\":\"${data}\"},"
            + "\"dataPath\":\"$.data\",\"successDataTemplate\":\"${result}\","
            + "\"errorDataTemplate\":{\"code\":\"${status.code}\"}";

    @Test
    public void rejectsInvalidRequestPathsAndDuplicateStableTargetsBeforePublish() {
        assertInvalid("{\"enabled\":true," + RESPONSE + ",\"requestMappings\":[{"
                + "\"targetVarId\":1,\"targetRefType\":\"VARIABLE\",\"sourceType\":\"BODY\","
                + "\"sourcePath\":\"$..name\",\"targetType\":\"STRING\"}]}", "JSONPath");

        assertInvalid("{\"enabled\":true," + RESPONSE + ",\"requestMappings\":[{"
                + "\"targetVarId\":1,\"targetRefType\":\"VARIABLE\",\"sourceType\":\"BODY\","
                + "\"sourcePath\":\"$.name\",\"targetType\":\"STRING\"},{"
                + "\"targetVarId\":1,\"targetRefType\":\"VARIABLE\",\"sourceType\":\"HEADER\","
                + "\"sourcePath\":\"X-Name\",\"targetType\":\"STRING\"}]}", "重复");
    }

    @Test
    public void validatesResponseMappingsByStableReferenceAndExternalField() {
        String json = "{\"enabled\":true," + RESPONSE + ",\"responseMappings\":[{"
                + "\"sourceVarId\":9,\"sourceRefType\":\"VARIABLE\","
                + "\"targetField\":\"credit_score_v1\"}]}";
        OpenApiContract contract = OpenApiContractCodec.parse(
                OpenApiContractCodec.validateAndNormalize(json));

        Assert.assertEquals("credit_score_v1", contract.getResponseMappings().get(0).getTargetField());
        OpenApiContractCodec.validateResponseReferences(contract,
                java.util.Collections.singleton("VARIABLE:9"));

        assertInvalid("{\"enabled\":true," + RESPONSE + ",\"responseMappings\":[{"
                + "\"sourceVarId\":9,\"sourceRefType\":\"VARIABLE\","
                + "\"targetField\":\"score\"},{\"sourceVarId\":10,"
                + "\"sourceRefType\":\"VARIABLE\",\"targetField\":\"score\"}]}", "重复");
    }

    private void assertInvalid(String json, String expectedMessage) {
        try {
            OpenApiContractCodec.validateAndNormalize(json);
            Assert.fail("Expected invalid open API contract");
        } catch (IllegalArgumentException exception) {
            Assert.assertTrue(exception.getMessage(), exception.getMessage().contains(expectedMessage));
        }
    }
}
