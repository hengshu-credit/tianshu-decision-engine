package com.hengshucredit.rule.server.service;

import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ModelExecutionTimeoutExecutorTest {

    @Test
    public void closeCancelsQueuedModelWithoutWaitingForItsTimeout() throws Exception {
        ModelExecutionTimeoutExecutor executor = new ModelExecutionTimeoutExecutor();
        ThreadPoolExecutor pool = (ThreadPoolExecutor) ReflectionTestUtils.getField(executor, "executor");
        pool.setCorePoolSize(1);
        pool.setMaximumPoolSize(1);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean queuedTaskRan = new AtomicBoolean();
        try {
            callers.submit(() -> executor.execute(() -> {
                started.countDown();
                release.await(5, TimeUnit.SECONDS);
                return "running";
            }, 30000));
            assertTrue(started.await(1, TimeUnit.SECONDS));
            Future<String> queued = callers.submit(() -> executor.execute(() -> {
                queuedTaskRan.set(true);
                return "queued";
            }, 30000));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (pool.getQueue().isEmpty() && System.nanoTime() < deadline) Thread.sleep(1L);
            assertEquals(1, pool.getQueue().size());

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
    public void timedOutQueuedTaskReleasesCapacityBeforeRunningModelCompletes() throws Exception {
        ModelExecutionTimeoutExecutor executor = new ModelExecutionTimeoutExecutor();
        ThreadPoolExecutor pool = (ThreadPoolExecutor) ReflectionTestUtils.getField(executor, "executor");
        pool.setCorePoolSize(1);
        pool.setMaximumPoolSize(1);
        ExecutorService callers = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean cancelledTaskRan = new AtomicBoolean();
        try {
            Future<String> running = callers.submit(() -> executor.execute(() -> {
                started.countDown();
                assertTrue(release.await(2, TimeUnit.SECONDS));
                return "running";
            }, 5000));
            assertTrue(started.await(1, TimeUnit.SECONDS));

            assertThrows(IllegalArgumentException.class, () -> executor.execute(() -> {
                cancelledTaskRan.set(true);
                return "expired";
            }, 20));
            assertEquals("cancelled tasks must not consume bounded queue capacity", 0, pool.getQueue().size());
            assertEquals("the running model must still occupy its worker", 1L, release.getCount());

            release.countDown();
            assertEquals("running", running.get(1, TimeUnit.SECONDS));
            assertEquals("later", executor.execute(() -> "later", 1000));
            assertFalse(cancelledTaskRan.get());
        } finally {
            release.countDown();
            executor.close();
            callers.shutdownNow();
            callers.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

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
