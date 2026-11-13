package com.hengshucredit.rule.server.config;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Queue unstarted server requests before they acquire a project permit or issue any chargeable work. */
@Component
final class ExecutionAdmission {
    private final Semaphore executing;
    private final Semaphore waiting;
    private final long waitMillis;

    ExecutionAdmission(@Value("${rule-engine.execution-admission.parallelism:0}") int parallelism,
                       @Value("${rule-engine.execution-admission.queue-capacity:256}") int queueCapacity,
                       @Value("${rule-engine.execution-admission.wait-ms:90000}") long waitMillis) {
        if (parallelism < 0 || queueCapacity < 0 || waitMillis < 0) throw new IllegalArgumentException("执行并发数、排队容量或等待时间无效");
        int slots = parallelism == 0 ? Math.max(4, Runtime.getRuntime().availableProcessors() * 4) : parallelism;
        executing = new Semaphore(slots, true);
        waiting = new Semaphore(queueCapacity);
        this.waitMillis = waitMillis;
    }

    boolean enter(long started, int projectBudgetMillis) {
        try {
            if (executing.tryAcquire(0, TimeUnit.MILLISECONDS)) return true;
            if (!waiting.tryAcquire()) return false;
            try {
                long left = waitMillis;
                if (projectBudgetMillis > 0) left = Math.min(left, projectBudgetMillis -
                        TimeUnit.NANOSECONDS.toMillis(com.hengshucredit.rule.server.service.RequestDeadlineContext.currentNanoTime() - started));
                return left > 0 && executing.tryAcquire(left, TimeUnit.MILLISECONDS);
            } finally { waiting.release(); }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    int depth() { return executing.getQueueLength(); }
    void leave() { executing.release(); }
}
