package com.hengshucredit.rule.server.service;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class SourceResolutionExecutor implements AutoCloseable {
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.hengshucredit.rule.server.security.ConsoleProjectAccess consoleProjects;

    private static final long SHUTDOWN_WAIT_SECONDS = 5L;
    private final int parallelism;
    private final ThreadPoolExecutor executor;
    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private volatile boolean closed;

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
                (task, pool) -> {
                    if (pool.isShutdown()) throw new RejectedExecutionException("Source resolution executor is closed");
                    task.run();
                });
    }

    public int getParallelism() {
        return parallelism;
    }

    public <T> CompletableFuture<T> submit(Callable<T> task) {
        if (task == null) {
            throw new IllegalArgumentException("Source resolution task must not be null");
        }
        Callable<T> scopedTask = consoleProjects == null ? task : consoleProjects.wrap(task);
        submitted.incrementAndGet();
        if (closed) {
            CompletableFuture<T> rejected = CompletableFuture.failedFuture(
                    new RejectedExecutionException("Source resolution executor is closed"));
            observe(rejected);
            return rejected;
        }
        if (executor == null) {
            CompletableFuture<T> inline = callInline(scopedTask);
            observe(inline);
            return inline;
        }
        SourceResolutionTask<T> future = new SourceResolutionTask<>(scopedTask, RequestDeadlineContext.capture());
        observe(future);
        try {
            executor.execute(future);
        } catch (RejectedExecutionException rejected) {
            future.completeExceptionally(rejected);
        }
        return future;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("parallelism", parallelism);
        result.put("submitted", submitted.get());
        result.put("completed", completed.get());
        result.put("failed", failed.get());
        result.put("queueDepth", executor == null ? 0 : executor.getQueue().size());
        result.put("active", executor == null ? 0 : executor.getActiveCount());
        return result;
    }

    private <T> void observe(CompletableFuture<T> future) {
        future.whenComplete((value, error) -> {
            if (error == null) completed.incrementAndGet();
            else failed.incrementAndGet();
            if (future.isCancelled() && future instanceof Runnable queued && executor != null) {
                executor.remove(queued);
            }
        });
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

    private static <T> T call(Callable<T> task) {
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
        closed = true;
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                cancelQueuedTasks();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cancelQueuedTasks();
        }
    }

    private void cancelQueuedTasks() {
        for (Runnable task : executor.shutdownNow()) {
            if (task instanceof SourceResolutionTask<?> queued) queued.cancel(false);
        }
    }

    private static final class SourceResolutionTask<T> extends CompletableFuture<T> implements Runnable {
        private final Callable<T> task;
        private final Long deadline;

        private SourceResolutionTask(Callable<T> task, Long deadline) {
            this.task = task;
            this.deadline = deadline;
        }

        @Override
        public void run() {
            if (isDone()) return;
            T value;
            try (RequestDeadlineContext.Scope ignored = RequestDeadlineContext.install(deadline)) {
                value = call(task);
            } catch (Throwable failure) {
                completeExceptionally(failure);
                return;
            }
            complete(value);
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
