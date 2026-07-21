package com.hengshucredit.rule.server.service;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

public class RequestDeadlineContextTest {

    @Test
    public void exposesRemainingTimeAndAlwaysClearsThreadState() throws Exception {
        AtomicLong now = new AtomicLong(1_000_000_000L);
        RequestDeadlineContext.setNanoTimeSource(now::get);
        try {
            RequestDeadlineContext.start(500);
            Assert.assertEquals(500, RequestDeadlineContext.remainingMillis());
            now.addAndGet(501_000_000L);
            try {
                RequestDeadlineContext.check();
                Assert.fail("Expected deadline timeout");
            } catch (TimeoutException expected) {
                Assert.assertTrue(expected.getMessage().contains("超时"));
            }
        } finally {
            RequestDeadlineContext.clear();
            RequestDeadlineContext.resetNanoTimeSource();
        }
        Assert.assertEquals(Integer.MAX_VALUE, RequestDeadlineContext.remainingMillis());
    }

    @Test
    public void nestedLimitUsesTheEarlierDeadlineAndRestoresTheOuterDeadline() {
        AtomicLong now = new AtomicLong(1_000_000_000L);
        RequestDeadlineContext.setNanoTimeSource(now::get);
        try {
            RequestDeadlineContext.start(500);
            try (RequestDeadlineContext.Scope ignored = RequestDeadlineContext.limit(100)) {
                Assert.assertEquals(100, RequestDeadlineContext.remainingMillis());
                now.addAndGet(50_000_000L);
                Assert.assertEquals(50, RequestDeadlineContext.remainingMillis());
            }
            Assert.assertEquals(450, RequestDeadlineContext.remainingMillis());
        } finally {
            RequestDeadlineContext.clear();
            RequestDeadlineContext.resetNanoTimeSource();
        }
    }

    @Test
    public void startAtIncludesTimeAlreadySpentAuthenticatingTheRequest() {
        AtomicLong now = new AtomicLong(1_000_000_000L);
        RequestDeadlineContext.setNanoTimeSource(now::get);
        try {
            long requestStartedAt = RequestDeadlineContext.currentNanoTime();
            now.addAndGet(75_000_000L);

            RequestDeadlineContext.startAt(requestStartedAt, 100);

            Assert.assertEquals(25, RequestDeadlineContext.remainingMillis());
        } finally {
            RequestDeadlineContext.clear();
            RequestDeadlineContext.resetNanoTimeSource();
        }
    }
}
