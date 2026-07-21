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

    private void assertInvalid(String json, String expectedMessage) {
        try {
            OpenApiContractCodec.validateAndNormalize(json);
            Assert.fail("Expected invalid open API contract");
        } catch (IllegalArgumentException exception) {
            Assert.assertTrue(exception.getMessage(), exception.getMessage().contains(expectedMessage));
        }
    }
}
