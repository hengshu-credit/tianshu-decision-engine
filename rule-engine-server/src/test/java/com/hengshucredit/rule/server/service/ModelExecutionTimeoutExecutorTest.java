package com.hengshucredit.rule.server.service;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class ModelExecutionTimeoutExecutorTest {

    @Test
    public void stopsWaitingWhenConfiguredModelTimeoutExpires() {
        ModelExecutionTimeoutExecutor executor = new ModelExecutionTimeoutExecutor();
        long start = System.currentTimeMillis();
        try {
            executor.execute(() -> {
                Thread.sleep(1000L);
                return "late";
            }, 100);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("100 ms"));
            assertTrue("timeout should return promptly", System.currentTimeMillis() - start < 700L);
            executor.close();
            return;
        }
        executor.close();
        throw new AssertionError("Expected model execution timeout");
    }
}
