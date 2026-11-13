package com.hengshucredit.rule.server.service;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class RuleIdempotencyServiceTest {

    @Test
    public void multipartReplayKeepsBytesAndTreatsLiteralFileLikeObjectsAsOrdinaryInput() throws Exception {
        var upload = new org.springframework.mock.web.MockMultipartFile("p.raw", "原件.bin", "application/octet-stream", new byte[]{0,-1,42});
        var literal = Map.of("name", "ordinary", "bytes", "unchanged");
        var snapshot = ExecutionRequestSnapshot.capture(Map.of("nested.key", java.util.List.of(upload, literal)));
        String json = com.hengshucredit.rule.server.artifact.CanonicalJson.write(snapshot.checkpoint());
        var row = new RuleExecutionStateService.State(1L, 8L, 9L, "rule", "key", "digest", "trace", "QUEUED", 0,
                3L, 3L, "artifact", json, null, null, null, null, null);
        var restored = new RuleIdempotencyService().requestParams(row);
        var nested = (java.util.List<?>) restored.get("nested.key");
        var file = (org.springframework.web.multipart.MultipartFile) nested.get(0);
        assertEquals("原件.bin", file.getOriginalFilename());
        assertEquals(upload.getContentType(), file.getContentType());
        org.junit.Assert.assertArrayEquals(upload.getBytes(), file.getBytes());
        assertEquals(literal, nested.get(1));
        assertEquals(json, com.hengshucredit.rule.server.artifact.CanonicalJson.write(ExecutionRequestSnapshot.capture(restored).checkpoint()));
    }

    @Test
    public void checkpointKeepsOriginalParamsForTraceOnlyResume() throws Exception {
        RuleExecutionStateService.State state = new RuleExecutionStateService.State(
                1L, 8L, 9L, "credit_rule", "key", "digest", "trace-1", "RUNNING", 1,
                3L, 3L, "artifact", null, null, null, null, null, null);
        RuleIdempotencyService service = new RuleIdempotencyService();
        RuleIdempotencyService.Decision decision = RuleIdempotencyService.Decision.claimed(
                state, "owner", 3600, 120, 3L, "artifact");

        String checkpoint = service.withDecision(decision, () -> {
            service.recordRequestParams(decision, Map.of("orderNo", "A-1", "amount", 88));
            service.recordRequestParams(decision, Map.of("orderNo", "A-1", "amount", 88.0, "projected", true));
            return service.snapshotCheckpoint(new VariableResolutionInvocationCache(), Map.of("orderNo", "A-1"));
        });
        RuleExecutionStateService.State saved = new RuleExecutionStateService.State(
                1L, 8L, 9L, "credit_rule", "key", "digest", "trace-1", "WAITING_EXTERNAL", 1,
                3L, 3L, "artifact", checkpoint, null, null, null, null, null);
        assertEquals(Map.of("orderNo", "A-1", "amount", 88), service.requestParams(saved));
    }
}
