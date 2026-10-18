package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.core.engine.ExecutionCapacityException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ModelExecutionTimeoutExecutor {

    private final ThreadPoolExecutor executor;

    public ModelExecutionTimeoutExecutor() {
        this(Math.max(2, Runtime.getRuntime().availableProcessors() / 2), 256);
    }

    @Autowired
    public ModelExecutionTimeoutExecutor(
            @Value("${rule-engine.model.execution-parallelism:8}") int parallelism,
            @Value("${rule-engine.model.execution-queue-capacity:256}") int queueCapacity) {
        int workers = Math.max(1, parallelism);
        int capacity = Math.max(1, queueCapacity);
        this.executor = new ThreadPoolExecutor(
                workers, workers, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(capacity),
                new ModelThreadFactory(), new ThreadPoolExecutor.AbortPolicy());
    }

    public <T> T execute(Callable<T> task, int timeoutMs) {
        final Future<T> future;
        try {
            future = executor.submit(task);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            throw new ExecutionCapacityException(ExecutionCapacityException.Resource.MODEL_EXECUTION,
                    "模型执行队列已满，请稍后重试");
        }
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            cancelAndRemove(future);
            throw new IllegalArgumentException("模型执行超时（" + timeoutMs + " ms）", e);
        } catch (InterruptedException e) {
            cancelAndRemove(future);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("模型执行被中断", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new IllegalStateException("模型执行失败: " + cause.getMessage(), cause);
        }
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("active", executor.getActiveCount());
        result.put("poolSize", executor.getPoolSize());
        result.put("queueDepth", executor.getQueue().size());
        result.put("queueCapacity", executor.getQueue().size() + executor.getQueue().remainingCapacity());
        result.put("completed", executor.getCompletedTaskCount());
        return result;
    }

    private void cancelAndRemove(Future<?> future) {
        future.cancel(true);
        executor.remove((Runnable) future);
    }

    @PreDestroy
    public void close() {
        for (Runnable task : executor.shutdownNow()) {
            if (task instanceof Future<?> queued) queued.cancel(false);
        }
    }

    private static class ModelThreadFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "model-execution-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
