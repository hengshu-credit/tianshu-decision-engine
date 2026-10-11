package com.hengshucredit.rule.server.service;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class OpenRuleExecutionExecutorTest {

    @Test
    public void closeCancelsQueuedRequestWithoutADeadline() throws Exception {
        OpenRuleExecutionExecutor executor = new OpenRuleExecutionExecutor(1, 1);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean queuedTaskRan = new java.util.concurrent.atomic.AtomicBoolean();
        try {
            callers.submit(() -> executor.execute(() -> {
                started.countDown();
                release.await(5, TimeUnit.SECONDS);
                return "running";
            }));
            assertTrue(started.await(1, TimeUnit.SECONDS));
            Future<String> queued = callers.submit(() -> executor.execute(() -> {
                queuedTaskRan.set(true);
                return "queued";
            }));
            awaitQueueDepth(executor, 1);

            executor.close();

            ExecutionException cancelled = assertThrows(ExecutionException.class,
                    () -> queued.get(1, TimeUnit.SECONDS));
            assertTrue(cancelled.getCause() instanceof CancellationException);
            assertFalse(queuedTaskRan.get());
        } finally {
            release.countDown();
            executor.close();
            callers.shutdownNow();
            callers.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    @Test
    public void queuedRequestKeepsItsOriginalDeadlineAndDoesNotLeakIt() throws Exception {
        AtomicLong now = new AtomicLong();
        RequestDeadlineContext.setNanoTimeSource(now::get);
        OpenRuleExecutionExecutor executor = new OpenRuleExecutionExecutor(1, 1);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            Future<String> first = callers.submit(() -> executor.execute(() -> {
                started.countDown();
                assertTrue(release.await(2, TimeUnit.SECONDS));
                return "first";
            }));
            assertTrue(started.await(1, TimeUnit.SECONDS));
            Future<Integer> queued = callers.submit(() -> {
                RequestDeadlineContext.start(10000);
                try {
                    return executor.execute(RequestDeadlineContext::remainingMillis);
                } finally {
                    RequestDeadlineContext.clear();
                }
            });
            awaitQueueDepth(executor, 1);
            now.set(TimeUnit.SECONDS.toNanos(4));
            release.countDown();

            assertEquals("first", first.get(1, TimeUnit.SECONDS));
            assertEquals(Integer.valueOf(6000), queued.get(1, TimeUnit.SECONDS));
            assertEquals(Integer.valueOf(Integer.MAX_VALUE),
                    executor.execute(RequestDeadlineContext::remainingMillis));
        } finally {
            release.countDown();
            executor.close();
            callers.shutdownNow();
            callers.awaitTermination(1, TimeUnit.SECONDS);
            RequestDeadlineContext.resetNanoTimeSource();
            RequestDeadlineContext.clear();
        }
    }

    @Test
    public void doesNotRunRequestWhoseDeadlineExpiredInQueue() throws Exception {
        AtomicLong now = new AtomicLong();
        RequestDeadlineContext.setNanoTimeSource(now::get);
        OpenRuleExecutionExecutor executor = new OpenRuleExecutionExecutor(1, 1);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean executed = new java.util.concurrent.atomic.AtomicBoolean();
        try {
            Future<String> first = callers.submit(() -> executor.execute(() -> {
                started.countDown();
                assertTrue(release.await(2, TimeUnit.SECONDS));
                return "first";
            }));
            assertTrue(started.await(1, TimeUnit.SECONDS));
            Future<?> queued = callers.submit(() -> {
                RequestDeadlineContext.start(10000);
                try {
                    assertThrows(OpenRuleExecutionExecutor.TimedOut.class, () -> executor.execute(() -> {
                        executed.set(true);
                        return "expired";
                    }));
                } finally {
                    RequestDeadlineContext.clear();
                }
            });
            awaitQueueDepth(executor, 1);
            now.set(TimeUnit.SECONDS.toNanos(11));
            release.countDown();

            assertEquals("first", first.get(1, TimeUnit.SECONDS));
            queued.get(1, TimeUnit.SECONDS);
            assertFalse(executed.get());
            assertEquals(1L, ((Number) executor.snapshot().get("timedOut")).longValue());
        } finally {
            release.countDown();
            executor.close();
            callers.shutdownNow();
            callers.awaitTermination(1, TimeUnit.SECONDS);
            RequestDeadlineContext.resetNanoTimeSource();
            RequestDeadlineContext.clear();
        }
    }

    private static void awaitQueueDepth(OpenRuleExecutionExecutor executor, int expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (((Number) executor.snapshot().get("queueDepth")).intValue() != expected
                && System.nanoTime() < deadline) {
            Thread.sleep(1L);
        }
        assertEquals(expected, ((Number) executor.snapshot().get("queueDepth")).intValue());
    }

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

    @Test
    public void exposesQueueAndTimeoutCounters() {
        OpenRuleExecutionExecutor executor = new OpenRuleExecutionExecutor(1, 1);
        RequestDeadlineContext.start(1);
        try {
            try {
                executor.execute(() -> {
                    Thread.sleep(50L);
                    return "late";
                });
            } catch (OpenRuleExecutionExecutor.TimedOut ignored) {
                // expected
            }
            assertEquals(1L, ((Number) executor.snapshot().get("submitted")).longValue());
            assertEquals(1L, ((Number) executor.snapshot().get("timedOut")).longValue());
        } finally {
            RequestDeadlineContext.clear();
            executor.close();
        }
    }

    @Test
    public void removesTimedOutQueuedTaskSoLaterRequestsCanUseTheQueue() throws Exception {
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

        RequestDeadlineContext.start(50);
        try {
            executor.execute(() -> "queued-timeout");
        } catch (OpenRuleExecutionExecutor.TimedOut expected) {
            assertEquals(0, ((Number) executor.snapshot().get("queueDepth")).intValue());
        } finally {
            RequestDeadlineContext.clear();
            release.countDown();
            first.join(1000L);
            executor.close();
        }
    }
}
