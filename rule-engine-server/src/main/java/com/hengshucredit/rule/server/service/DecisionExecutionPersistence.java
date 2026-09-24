package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 将根执行日志和计费写入移出规则请求线程；队列满时回退到当前线程，保证审计和计费不丢失。
 */
@Component
public class DecisionExecutionPersistence implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(DecisionExecutionPersistence.class);

    private final RuleExecutionLogService logService;
    private final RuleBillingService billingService;
    private final ArrayBlockingQueue<Event> queue;
    private final ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicLong offered = new AtomicLong();
    private final AtomicLong persisted = new AtomicLong();
    private final AtomicLong fallback = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong totalWriteMs = new AtomicLong();
    private final AtomicLong maxQueueDepth = new AtomicLong();

    public DecisionExecutionPersistence(
            RuleExecutionLogService logService,
            RuleBillingService billingService,
            @Value("${rule-engine.execution-persistence.queue-capacity:5000}") int capacity) {
        this.logService = logService;
        this.billingService = billingService;
        this.queue = new ArrayBlockingQueue<>(Math.max(100, capacity));
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "decision-execution-persistence");
            thread.setDaemon(true);
            return thread;
        });
    }

    @PostConstruct
    public void start() {
        if (running.compareAndSet(false, true)) executor.submit(this::runLoop);
    }

    public void offer(RuleExecutionLog log, RuleDefinition definition,
                      boolean success, Long costTimeMs, String errorMessage,
                      ProjectAuthContext authContext) {
        if (log == null && definition == null) return;
        offered.incrementAndGet();
        Event event = new Event(log, definition, success, costTimeMs, errorMessage, authContext);
        if (!queue.offer(event)) {
            fallback.incrementAndGet();
            persist(event);
            return;
        }
        maxQueueDepth.accumulateAndGet(queue.size(), Math::max);
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("queueDepth", queue.size());
        result.put("queueCapacity", queue.remainingCapacity() + queue.size());
        result.put("offered", offered.get());
        result.put("persisted", persisted.get());
        result.put("fallback", fallback.get());
        result.put("failed", failed.get());
        result.put("maxQueueDepth", maxQueueDepth.get());
        long count = persisted.get();
        result.put("avgWriteMs", count == 0 ? 0D : (double) totalWriteMs.get() / count);
        return result;
    }

    private void runLoop() {
        while (running.get() || !queue.isEmpty()) {
            try {
                Event event = queue.poll(200, TimeUnit.MILLISECONDS);
                if (event != null) persist(event);
            } catch (InterruptedException interrupted) {
                if (!running.get()) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void persist(Event event) {
        long started = System.nanoTime();
        try {
            if (event.log != null) logService.save(event.log);
            if (event.definition != null) {
                billingService.recordEngineExecution(event.definition, event.success,
                        event.costTimeMs, event.errorMessage, event.authContext);
            }
            persisted.incrementAndGet();
        } catch (RuntimeException error) {
            failed.incrementAndGet();
            log.error("异步持久化规则执行日志或计费失败: {}", error.getMessage(), error);
        } finally {
            totalWriteMs.addAndGet(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        }
    }

    @PreDestroy
    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) return;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private record Event(RuleExecutionLog log, RuleDefinition definition, boolean success,
                         Long costTimeMs, String errorMessage, ProjectAuthContext authContext) {
    }
}
