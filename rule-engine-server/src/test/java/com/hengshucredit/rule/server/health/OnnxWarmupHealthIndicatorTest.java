package com.hengshucredit.rule.server.health;

import org.junit.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class OnnxWarmupHealthIndicatorTest {
    @Test
    public void retryKeepsReadinessClosedUntilAllTargetsPassAndClearsPreviousFailure() {
        OnnxWarmupStatus status = new OnnxWarmupStatus();
        OnnxWarmupHealthIndicator indicator = new OnnxWarmupHealthIndicator(status);
        status.start(1);
        status.recordFailure(new IllegalStateException("temporary database error"));
        status.complete();
        status.resetForRetry();
        assertEquals(Status.OUT_OF_SERVICE, indicator.health().getStatus());
        assertEquals(0, status.details().get("failureCount"));
        assertFalse(status.details().containsKey("lastErrorType"));
        status.start(1);
        org.junit.Assert.assertThrows(IllegalStateException.class, status::resetForRetry);
        status.recordSuccess(false);
        status.complete();
        assertEquals(Status.UP, indicator.health().getStatus());
    }

    @Test
    public void notStartedAndWarmingAreOutOfService() {
        OnnxWarmupStatus status = new OnnxWarmupStatus();
        OnnxWarmupHealthIndicator indicator = new OnnxWarmupHealthIndicator(status);

        assertEquals(Status.OUT_OF_SERVICE, indicator.health().getStatus());
        assertEquals("NOT_STARTED", indicator.health().getDetails().get("state"));

        status.start(2);

        assertEquals(Status.OUT_OF_SERVICE, indicator.health().getStatus());
        assertEquals("WARMING", indicator.health().getDetails().get("state"));
        assertEquals(2, indicator.health().getDetails().get("targetCount"));
    }

    @Test
    public void noPreloadTargetsCompleteAsReady() {
        OnnxWarmupStatus status = new OnnxWarmupStatus();
        OnnxWarmupHealthIndicator indicator = new OnnxWarmupHealthIndicator(status);

        status.start(0);
        status.complete();

        Health health = indicator.health();
        assertEquals(Status.UP, health.getStatus());
        assertEquals("READY", health.getDetails().get("state"));
        assertEquals(0, health.getDetails().get("targetCount"));
    }

    @Test
    public void successfulCpuFallbackRemainsReadyAndIsCounted() {
        OnnxWarmupStatus status = new OnnxWarmupStatus();
        OnnxWarmupHealthIndicator indicator = new OnnxWarmupHealthIndicator(status);

        status.start(2);
        status.recordSuccess(false);
        status.recordSuccess(true);
        status.complete();

        Health health = indicator.health();
        assertEquals(Status.UP, health.getStatus());
        assertEquals(2, health.getDetails().get("successCount"));
        assertEquals(1, health.getDetails().get("cpuFallbackCount"));
    }

    @Test
    public void anyFailureMakesReadinessOutOfServiceWithoutLeakingMessage() {
        OnnxWarmupStatus status = new OnnxWarmupStatus();
        OnnxWarmupHealthIndicator indicator = new OnnxWarmupHealthIndicator(status);

        status.start(1);
        status.recordFailure(new IllegalStateException("password=do-not-expose"));
        status.complete();

        Health health = indicator.health();
        assertEquals(Status.OUT_OF_SERVICE, health.getStatus());
        assertEquals("FAILED", health.getDetails().get("state"));
        assertEquals(1, health.getDetails().get("failureCount"));
        assertEquals("IllegalStateException", health.getDetails().get("lastErrorType"));
        assertFalse(health.getDetails().toString().contains("do-not-expose"));
    }
}
