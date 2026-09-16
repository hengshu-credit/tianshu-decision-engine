package com.hengshucredit.rule.server.service;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class SourceResolutionExecutor implements AutoCloseable {

    private static final long SHUTDOWN_WAIT_SECONDS = 5L;
    private final int parallelism;
    private final ThreadPoolExecutor executor;

    public SourceResolutionExecutor(
            @Value("${rule-engine.source-resolution.parallelism:1}") int parallelism) {
        if (parallelism <= 0) {
            throw new IllegalArgumentException("Source resolution parallelism must be greater than zero");
        }
        this.parallelism = parallelism;
        this.executor = parallelism == 1 ? null : new ThreadPoolExecutor(
                parallelism,
                parallelism,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(parallelism * 4),
                new SourceResolutionThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    public int getParallelism() {
        return parallelism;
    }

    public <T> CompletableFuture<T> submit(Callable<T> task) {
        if (task == null) {
            throw new IllegalArgumentException("Source resolution task must not be null");
        }
        if (executor == null) {
            return callInline(task);
        }
        Long deadline = RequestDeadlineContext.capture();
        return CompletableFuture.supplyAsync(() -> {
            try (RequestDeadlineContext.Scope ignored = RequestDeadlineContext.install(deadline)) {
                return call(task);
            }
        }, executor);
    }

    private <T> CompletableFuture<T> callInline(Callable<T> task) {
        try {
            return CompletableFuture.completedFuture(task.call());
        } catch (Exception e) {
            CompletableFuture<T> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }

    private <T> T call(Callable<T> task) {
        try {
            return task.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new SourceResolutionTaskException(e);
        }
    }

    @Override
    @PreDestroy
    public void close() {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private static final class SourceResolutionThreadFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable,
                    "rule-source-resolver-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }

    private static final class SourceResolutionTaskException extends RuntimeException {
        private SourceResolutionTaskException(Exception cause) {
            super(cause);
        }
    }
}
