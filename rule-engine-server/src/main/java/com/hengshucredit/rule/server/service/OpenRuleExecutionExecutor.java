package com.hengshucredit.rule.server.service;

import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OpenRuleExecutionExecutor {

    private final ThreadPoolExecutor executor;
    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong timedOut = new AtomicLong();

    public OpenRuleExecutionExecutor() {
        this(Math.max(4, Math.min(32, Runtime.getRuntime().availableProcessors())), 1000);
    }

    public OpenRuleExecutionExecutor(int workerThreads, int queueCapacity) {
        int threads = Math.max(1, workerThreads);
        this.executor = new ThreadPoolExecutor(threads, threads, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(Math.max(1, queueCapacity)), new OpenRuleThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    public <T> T execute(Callable<T> task) {
        int timeoutMs = RequestDeadlineContext.remainingMillis();
        if (timeoutMs == 0) throw new TimedOut();
        Future<T> future;
        submitted.incrementAndGet();
        try {
            future = executor.submit(() -> {
                if (timeoutMs == Integer.MAX_VALUE) RequestDeadlineContext.clear();
                else RequestDeadlineContext.start(timeoutMs);
                try {
                    return task.call();
                } finally {
                    RequestDeadlineContext.clear();
                }
            });
        } catch (RejectedExecutionException e) {
            rejected.incrementAndGet();
            throw new Busy(e);
        }
        try {
            return timeoutMs == Integer.MAX_VALUE
                    ? future.get() : future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            timedOut.incrementAndGet();
            future.cancel(true);
            throw new TimedOut(e);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("开放规则执行被中断", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("开放规则执行失败", cause);
        }
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("poolSize", executor.getPoolSize());
        result.put("active", executor.getActiveCount());
        result.put("queueDepth", executor.getQueue().size());
        result.put("queueCapacity", executor.getQueue().remainingCapacity() + executor.getQueue().size());
        result.put("submitted", submitted.get());
        result.put("completed", executor.getCompletedTaskCount());
        result.put("rejected", rejected.get());
        result.put("timedOut", timedOut.get());
        return result;
    }

    @PreDestroy
    public void close() {
        executor.shutdownNow();
    }

    public static class TimedOut extends RuntimeException {
        TimedOut() { super("项目请求总超时"); }
        TimedOut(Throwable cause) { super("项目请求总超时", cause); }
    }

    public static class Busy extends RuntimeException {
        Busy(Throwable cause) { super("开放规则执行队列已满", cause); }
    }

    private static class OpenRuleThreadFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "open-rule-execution-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
