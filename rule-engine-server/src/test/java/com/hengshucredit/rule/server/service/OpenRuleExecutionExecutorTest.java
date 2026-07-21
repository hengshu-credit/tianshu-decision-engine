package com.hengshucredit.rule.server.service;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OpenRuleExecutionExecutorTest {

    @Test
    public void enforcesTotalRequestDeadlineAndPropagatesItToWorker() throws Exception {
        OpenRuleExecutionExecutor executor = new OpenRuleExecutionExecutor(1, 1);
        RequestDeadlineContext.start(100);
        long start = System.currentTimeMillis();
        try {
            executor.execute(() -> {
                assertTrue(RequestDeadlineContext.remainingMillis() <= 100);
                Thread.sleep(1000L);
                return "late";
            });
        } catch (OpenRuleExecutionExecutor.TimedOut e) {
            assertTrue(System.currentTimeMillis() - start < 700L);
            executor.close();
            RequestDeadlineContext.clear();
            return;
        }
        executor.close();
        RequestDeadlineContext.clear();
        throw new AssertionError("Expected open rule execution timeout");
    }

    @Test
    public void rejectsWhenBoundedWorkerQueueIsFull() throws Exception {
        OpenRuleExecutionExecutor executor = new OpenRuleExecutionExecutor(1, 1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread first = new Thread(() -> executor.execute(() -> {
            started.countDown();
            release.await(2, TimeUnit.SECONDS);
            return "first";
        }));
        first.start();
        assertTrue(started.await(1, TimeUnit.SECONDS));
        Thread queued = new Thread(() -> executor.execute(() -> {
            release.await(2, TimeUnit.SECONDS);
            return "queued";
        }));
        queued.start();
        Thread.sleep(50L);

        try {
            executor.execute(() -> "rejected");
        } catch (OpenRuleExecutionExecutor.Busy e) {
            release.countDown();
            first.join(1000L);
            queued.join(1000L);
            executor.close();
            assertEquals(0L, release.getCount());
            return;
        }
        release.countDown();
        executor.close();
        throw new AssertionError("Expected bounded executor rejection");
    }
}
