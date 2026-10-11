package com.hengshucredit.rule.server.service;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SourceResolutionExecutorTest {

    @Test
    public void submittingAfterCloseCompletesExceptionallyForInlineAndParallelModes() {
        for (int parallelism : new int[]{1, 2}) {
            SourceResolutionExecutor executor = new SourceResolutionExecutor(parallelism);
            executor.close();
            AtomicInteger invoked = new AtomicInteger();
            CompletableFuture<Integer> rejected = executor.submit(invoked::incrementAndGet);

            assertTrue("closed executor must not leave a future pending", rejected.isCompletedExceptionally());
            CompletionException failure = assertThrows(CompletionException.class, rejected::join);
            assertTrue(failure.getCause() instanceof RejectedExecutionException);
            assertEquals(0, invoked.get());
        }
    }

    @Test
    public void forcedShutdownCompletesDiscardedQueuedFutures() throws Exception {
        SourceResolutionExecutor executor = new SourceResolutionExecutor(2);
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        try {
            for (int i = 0; i < 2; i++) {
                executor.submit(() -> {
                    entered.countDown();
                    release.await(2, TimeUnit.SECONDS);
                    return "running";
                });
            }
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            CompletableFuture<String> queued = executor.submit(() -> "must-not-run");
            Thread closing = new Thread(() -> {
                Thread.currentThread().interrupt();
                executor.close();
            });
            closing.start();
            closing.join(1000);

            assertTrue("discarded task must reach a terminal state", queued.isDone());
            assertTrue(queued.isCancelled());
        } finally {
            release.countDown();
            executor.close();
        }
    }

    @Test
    public void parallelSourcesInheritDeadlineAndDoNotLeakItToNextRequest() throws Exception {
        try (SourceResolutionExecutor executor = new SourceResolutionExecutor(2)) {
            RequestDeadlineContext.start(5000);
            try {
                int remaining = executor.submit(RequestDeadlineContext::remainingMillis).get(1, TimeUnit.SECONDS);
                assertTrue(remaining > 0 && remaining <= 5000);
            } finally { RequestDeadlineContext.clear(); }
            assertEquals(Integer.valueOf(Integer.MAX_VALUE), executor.submit(RequestDeadlineContext::remainingMillis).get(1, TimeUnit.SECONDS));
        }
    }

    @Test
    public void rejectsNonPositiveParallelism() {
        assertThrows(IllegalArgumentException.class, () -> new SourceResolutionExecutor(0));
        assertThrows(IllegalArgumentException.class, () -> new SourceResolutionExecutor(-1));
    }

    @Test
    public void parallelismOneRunsInlineOnCallingThread() {
        SourceResolutionExecutor executor = new SourceResolutionExecutor(1);
        Thread caller = Thread.currentThread();
        AtomicReference<Thread> worker = new AtomicReference<>();

        CompletableFuture<String> future = executor.submit(() -> {
            worker.set(Thread.currentThread());
            return "resolved";
        });

        assertEquals("resolved", future.join());
        assertEquals(caller, worker.get());
        executor.close();
    }

    @Test
    public void configuredParallelismBoundsConcurrentTasksWithoutDroppingWork() throws Exception {
        SourceResolutionExecutor executor = new SourceResolutionExecutor(2);
        CountDownLatch firstTwoEntered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        List<CompletableFuture<Integer>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) {
                futures.add(executor.submit(() -> {
                    int current = active.incrementAndGet();
                    maximum.accumulateAndGet(current, Math::max);
                    firstTwoEntered.countDown();
                    try {
                        if (!release.await(2, TimeUnit.SECONDS)) {
                            throw new AssertionError("release timed out");
                        }
                        return completed.incrementAndGet();
                    } finally {
                        active.decrementAndGet();
                    }
                }));
            }
            assertTrue(firstTwoEntered.await(2, TimeUnit.SECONDS));
        } finally {
            release.countDown();
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        assertEquals(2, maximum.get());
        assertEquals(8, completed.get());
        executor.close();
    }

    @Test
    public void exposesQueueAndFailureCounters() throws Exception {
        try (SourceResolutionExecutor executor = new SourceResolutionExecutor(2)) {
            executor.submit(() -> "ok").join();
            assertThrows(RuntimeException.class, () -> executor.submit(() -> {
                throw new IllegalStateException("failed");
            }).join());
            assertEquals(2L, ((Number) executor.snapshot().get("submitted")).longValue());
            assertEquals(1L, ((Number) executor.snapshot().get("completed")).longValue());
            assertEquals(1L, ((Number) executor.snapshot().get("failed")).longValue());
        }
    }
}
