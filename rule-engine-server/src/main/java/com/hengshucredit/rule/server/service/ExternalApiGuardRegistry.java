package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Component
public class ExternalApiGuardRegistry {

    private final ExternalCallProperties properties;
    private final LinkedHashMap<Long, GuardState> states = new LinkedHashMap<>(16, 0.75F, true);

    public ExternalApiGuardRegistry(ExternalCallProperties properties) {
        this.properties = properties;
    }

    public Permit acquire(RuleExternalApiConfig config) {
        if (config == null || config.getId() == null) {
            throw new IllegalArgumentException("API configuration id must not be null");
        }
        GuardState state = state(config);
        if (!state.rateLimiter.tryAcquire()) {
            throw new RejectedException("API_RATE_LIMITED", "外数API请求超过QPS限制");
        }
        int configuredWait = config.getConcurrentWaitTimeoutMs() == null
                ? 0 : Math.max(0, config.getConcurrentWaitTimeoutMs());
        int remaining = RequestDeadlineContext.remainingMillis();
        int waitMillis = remaining == Integer.MAX_VALUE ? configuredWait : Math.min(configuredWait, remaining);
        boolean acquired;
        try {
            acquired = waitMillis > 0
                    ? state.semaphore.tryAcquire(waitMillis, TimeUnit.MILLISECONDS)
                    : state.semaphore.tryAcquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RejectedException("API_MAX_CONCURRENT", "等待外数API并发许可时被中断");
        }
        if (!acquired) {
            throw new RejectedException("API_MAX_CONCURRENT", "外数API达到最大并发数");
        }
        synchronized (state) {
            state.active++;
        }
        return new Permit(state);
    }

    public synchronized void invalidate(Long apiConfigId) {
        if (apiConfigId == null) return;
        GuardState state = states.get(apiConfigId);
        if (state == null) return;
        synchronized (state) {
            if (state.active == 0) states.remove(apiConfigId);
            else state.retired = true;
        }
    }

    private synchronized GuardState state(RuleExternalApiConfig config) {
        GuardSettings settings = GuardSettings.from(config);
        GuardState existing = states.get(config.getId());
        if (existing != null && existing.settings.equals(settings) && !existing.retired) {
            return existing;
        }
        if (existing != null) {
            synchronized (existing) {
                existing.retired = true;
                if (existing.active == 0) states.remove(config.getId());
            }
        }
        evictIdleIfNeeded();
        GuardState created = new GuardState(settings);
        states.put(config.getId(), created);
        return created;
    }

    private void evictIdleIfNeeded() {
        int max = Math.max(1, properties.getApiGuardRegistryMaxEntries());
        Iterator<Map.Entry<Long, GuardState>> iterator = states.entrySet().iterator();
        while (states.size() >= max && iterator.hasNext()) {
            GuardState candidate = iterator.next().getValue();
            synchronized (candidate) {
                if (candidate.active == 0) iterator.remove();
            }
        }
        if (states.size() >= max) {
            throw new RejectedException("API_GUARD_REGISTRY_FULL", "外数API流控注册表已满");
        }
    }

    synchronized int size() {
        return states.size();
    }

    public static final class Permit implements AutoCloseable {
        private GuardState state;

        private Permit(GuardState state) {
            this.state = state;
        }

        @Override
        public void close() {
            GuardState current = state;
            if (current == null) return;
            state = null;
            current.semaphore.release();
            synchronized (current) {
                if (current.active > 0) current.active--;
            }
        }
    }

    public static class RejectedException extends IllegalStateException {
        private final String code;

        RejectedException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    private static final class GuardState {
        private final GuardSettings settings;
        private final TokenBucket rateLimiter;
        private final Semaphore semaphore;
        private int active;
        private boolean retired;

        private GuardState(GuardSettings settings) {
            this.settings = settings;
            this.rateLimiter = new TokenBucket(settings.qps, settings.burst);
            this.semaphore = new Semaphore(settings.maxConcurrent, true);
        }
    }

    private static final class GuardSettings {
        private final double qps;
        private final int burst;
        private final int maxConcurrent;

        private GuardSettings(double qps, int burst, int maxConcurrent) {
            this.qps = qps;
            this.burst = burst;
            this.maxConcurrent = maxConcurrent;
        }

        private static GuardSettings from(RuleExternalApiConfig config) {
            BigDecimal configuredQps = config.getQpsLimit();
            double qps = configuredQps == null || configuredQps.signum() <= 0
                    ? 0D : configuredQps.doubleValue();
            int burst = qps <= 0 ? Integer.MAX_VALUE : (config.getBurstCapacity() == null
                    ? Math.max(1, (int) Math.ceil(qps)) : Math.max(1, config.getBurstCapacity()));
            int maxConcurrent = config.getMaxConcurrent() == null ? 50 : Math.max(1, config.getMaxConcurrent());
            return new GuardSettings(qps, burst, maxConcurrent);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof GuardSettings)) return false;
            GuardSettings that = (GuardSettings) other;
            return Double.compare(qps, that.qps) == 0 && burst == that.burst
                    && maxConcurrent == that.maxConcurrent;
        }

        @Override
        public int hashCode() {
            long qpsBits = Double.doubleToLongBits(qps);
            int result = (int) (qpsBits ^ (qpsBits >>> 32));
            result = 31 * result + burst;
            return 31 * result + maxConcurrent;
        }
    }

    private static final class TokenBucket {
        private final double qps;
        private final double capacity;
        private double tokens;
        private long lastNanos;

        private TokenBucket(double qps, int capacity) {
            this.qps = qps;
            this.capacity = capacity;
            this.tokens = capacity;
            this.lastNanos = System.nanoTime();
        }

        private synchronized boolean tryAcquire() {
            if (qps <= 0D) return true;
            long now = System.nanoTime();
            tokens = Math.min(capacity, tokens + (now - lastNanos) / 1_000_000_000D * qps);
            lastNanos = now;
            if (tokens < 1D) return false;
            tokens -= 1D;
            return true;
        }
    }
}
