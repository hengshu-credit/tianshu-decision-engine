package com.hengshucredit.rule.server.service;

import org.junit.Assert;
import org.junit.Test;

public class TracePayloadSanitizerTest {
    @Test
    public void masksConfiguredNestedAndArrayPaths() {
        String result = TracePayloadSanitizer.mask(
                "{\"input\":{\"idCard\":\"1101\"},\"items\":[{\"phone\":\"138\"}]}",
                "$.input.idCard,$.items[*].phone");

        Assert.assertTrue(result.contains("\"idCard\":\"******\""));
        Assert.assertTrue(result.contains("\"phone\":\"******\""));
        Assert.assertFalse(result.contains("1101"));
        Assert.assertFalse(result.contains("138"));
    }

    @Test
    public void keepsInMemorySourceTextWhenNoPathsConfigured() {
        String source = "{\"phone\":\"138\"}";
        Assert.assertEquals(source, TracePayloadSanitizer.mask(source, ""));
    }
}
