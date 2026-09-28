package com.hengshucredit.rule.server.service;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public class ExternalApiPayloadCapturePolicyTest {

    @Test
    public void keepsOriginalBodyByteForByteWithoutFilters() {
        String original = "{\"message\": \"a  b\",\"file\":\"BASE64\"}";

        ExternalApiPayloadCapturePolicy.Capture capture = ExternalApiPayloadCapturePolicy.capture(
                "{\"response\":{\"source\":\"ORIGINAL\"}}", "response", original,
                Map.of("message", "processed"));

        Assert.assertEquals(original, capture.capturedBody());
        Assert.assertEquals("CAPTURED", capture.metadata().get("status"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void filtersNestedArrayFieldsWithoutMutatingProcessedValue() {
        Map<String, Object> processed = new LinkedHashMap<>();
        processed.put("items", new java.util.ArrayList<>(java.util.List.of(
                new LinkedHashMap<>(Map.of("id", 1, "base64", "AAA")),
                new LinkedHashMap<>(Map.of("id", 2, "base64", "BBB")))));

        ExternalApiPayloadCapturePolicy.Capture capture = ExternalApiPayloadCapturePolicy.capture(
                "{\"response\":{\"source\":\"PROCESSED\",\"excludePaths\":[\"$.items[*].base64\"]}}",
                "response", "ignored", processed);

        Assert.assertFalse(capture.capturedBody().contains("base64"));
        Assert.assertEquals("AAA", ((Map<String, Object>) ((java.util.List<?>) processed.get("items")).get(0)).get("base64"));
        Assert.assertEquals("FILTERED", capture.metadata().get("status"));
    }

    @Test
    public void base64DecryptsASelectedFieldForCaptureOnly() {
        String encoded = Base64.getEncoder().encodeToString("明文内容".getBytes(StandardCharsets.UTF_8));
        String original = "{\"payload\":\"" + encoded + "\"}";

        ExternalApiPayloadCapturePolicy.Capture capture = ExternalApiPayloadCapturePolicy.capture(
                "{\"response\":{\"source\":\"ORIGINAL\",\"decrypt\":{\"enabled\":true,\"mode\":\"BASE64\",\"path\":\"$.payload\"}}}",
                "response", original, null);

        Assert.assertTrue(capture.capturedBody().contains("明文内容"));
        Assert.assertEquals("CAPTURED", capture.metadata().get("status"));
    }

    @Test
    public void invalidPathFailsClosedInsteadOfReturningUnfilteredBody() {
        ExternalApiPayloadCapturePolicy.Capture capture = ExternalApiPayloadCapturePolicy.capture(
                "{\"response\":{\"source\":\"ORIGINAL\",\"excludePaths\":[\"invalid\"]}}",
                "response", "{\"secret\":\"do-not-return\"}", null);

        Assert.assertNull(capture.capturedBody());
        Assert.assertEquals("FAILED", capture.metadata().get("status"));
    }

    @Test
    public void maxFieldBytesOmitsOversizedFieldFromStoredCopy() {
        ExternalApiPayloadCapturePolicy.Capture capture = ExternalApiPayloadCapturePolicy.capture(
                "{\"response\":{\"source\":\"PROCESSED\",\"maxFieldBytes\":4}}",
                "response", null, new LinkedHashMap<>(Map.of("base64", "123456789")));

        Assert.assertFalse(capture.capturedBody().contains("123456789"));
        Assert.assertEquals("FILTERED", capture.metadata().get("status"));
    }

    @Test
    public void optionalOriginalRetentionCanDropLargeRawBody() {
        String original = "{\"base64\":\"very-large\"}";
        ExternalApiPayloadCapturePolicy.Capture capture = ExternalApiPayloadCapturePolicy.capture(
                "{\"response\":{\"source\":\"ORIGINAL\",\"saveOriginal\":false,\"maxFieldBytes\":4}}",
                "response", original, null);

        Assert.assertNull(ExternalApiPayloadCapturePolicy.originalBody(
                "{\"response\":{\"source\":\"ORIGINAL\",\"saveOriginal\":false}}",
                "response", original, capture));
    }

    @Test
    public void failedCaptureAlwaysRetainsOriginalAndMarksFallbackReason() {
        String original = "{\"payload\":\"cipher\"}";
        ExternalApiPayloadCapturePolicy.Capture capture = ExternalApiPayloadCapturePolicy.capture(
                "{\"response\":{\"source\":\"ORIGINAL\",\"saveOriginal\":false,\"excludePaths\":[\"bad\"]}}",
                "response", original, null);

        Assert.assertEquals(original, ExternalApiPayloadCapturePolicy.originalBody(
                "{\"response\":{\"source\":\"ORIGINAL\",\"saveOriginal\":false}}",
                "response", original, capture));
        Assert.assertEquals("CAPTURE_FAILED", capture.metadata().get("originalStoredReason"));
    }
}
