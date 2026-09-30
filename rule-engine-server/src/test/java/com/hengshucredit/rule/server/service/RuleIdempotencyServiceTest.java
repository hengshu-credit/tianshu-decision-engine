package com.hengshucredit.rule.server.service;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class RuleIdempotencyServiceTest {

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
            return service.snapshotCheckpoint(new VariableResolutionInvocationCache(), Map.of("orderNo", "A-1"));
        });
        RuleExecutionStateService.State saved = new RuleExecutionStateService.State(
                1L, 8L, 9L, "credit_rule", "key", "digest", "trace-1", "WAITING_EXTERNAL", 1,
                3L, 3L, "artifact", checkpoint, null, null, null, null, null);
        assertEquals(Map.of("orderNo", "A-1", "amount", 88), service.requestParams(saved));
    }
}
