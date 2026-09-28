package com.hengshucredit.rule.server.service;

import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ModelExecutionTimeoutExecutor {

    private final ExecutorService executor = new java.util.concurrent.ThreadPoolExecutor(
            Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
            Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
            0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(256),
            new ModelThreadFactory(), new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());

    public <T> T execute(Callable<T> task, int timeoutMs) {
        final Future<T> future;
        try {
            future = executor.submit(task);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            throw new IllegalStateException("模型执行队列已满，请稍后重试", e);
        }
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new IllegalArgumentException("模型执行超时（" + timeoutMs + " ms）", e);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("模型执行被中断", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new IllegalStateException("模型执行失败: " + cause.getMessage(), cause);
        }
    }

    @PreDestroy
    public void close() {
        executor.shutdownNow();
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
