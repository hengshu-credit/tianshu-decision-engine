package com.hengshucredit.rule.server.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/** authId 维度的有界令牌桶与并发隔离。 */
@Component
public class ProjectExecutionGuard {
    private final int maxEntries;
    private final LongSupplier nanoTime;
    private final LinkedHashMap<Long, State> states = new LinkedHashMap<>(16, 0.75f, true);

    @Autowired
    public ProjectExecutionGuard(ProjectAuthProperties properties) {
        this(properties.getGuardRegistryMaxEntries(), System::nanoTime);
    }

    public ProjectExecutionGuard(int maxEntries, LongSupplier nanoTime) {
        if (maxEntries <= 0) throw new IllegalArgumentException("guard registry maxEntries 必须大于 0");
        this.maxEntries = maxEntries;
        this.nanoTime = nanoTime;
    }

    public Permit acquire(Long authId, ProjectAccessPolicy policy) {
        if (authId == null) throw new IllegalArgumentException("authId 不能为空");
        ProjectAccessPolicy effective = policy == null ? new ProjectAccessPolicy() : policy;
        effective.validate();
        State state = state(authId, effective);
        if (!state.tryToken(nanoTime.getAsLong())) throw new Rejected(Reason.RATE_LIMITED);
        if (!state.tryConcurrent()) throw new Rejected(Reason.CONCURRENT_LIMITED);
        return new Permit(state);
    }

    synchronized int registrySize() {
        return states.size();
    }

    private synchronized State state(Long authId, ProjectAccessPolicy policy) {
        String fingerprint = fingerprint(policy);
        State state = states.get(authId);
        if (state != null && state.fingerprint.equals(fingerprint)) return state;
        if (state != null && state.active.get() == 0) states.remove(authId);
        if (!states.containsKey(authId) && states.size() >= maxEntries) evictInactive();
        if (states.size() >= maxEntries) throw new Rejected(Reason.REGISTRY_FULL);
        state = new State(fingerprint, policy, nanoTime.getAsLong());
        states.put(authId, state);
        return state;
    }

    private void evictInactive() {
        Iterator<Map.Entry<Long, State>> iterator = states.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().active.get() == 0) {
                iterator.remove();
                return;
            }
        }
    }

    private String fingerprint(ProjectAccessPolicy policy) {
        return policy.getQps() + ":" + policy.getBurst() + ":" + policy.getMaxConcurrent();
    }

    public enum Reason { RATE_LIMITED, CONCURRENT_LIMITED, REGISTRY_FULL }

    public static class Rejected extends RuntimeException {
        private final Reason reason;

        private Rejected(Reason reason) {
            super(reason.name());
            this.reason = reason;
        }

        public Reason getReason() { return reason; }
    }

    public static class Permit implements AutoCloseable {
        private final State state;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private Permit(State state) { this.state = state; }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) state.release();
        }
    }

    private static class State {
        private final String fingerprint;
        private final int qps;
        private final int burst;
        private final Semaphore semaphore;
        private final AtomicInteger active = new AtomicInteger();
        private double tokens;
        private long lastRefill;

        private State(String fingerprint, ProjectAccessPolicy policy, long now) {
            this.fingerprint = fingerprint;
            this.qps = policy.getQps();
            this.burst = policy.getBurst();
            this.tokens = qps <= 0 ? 0 : burst;
            this.lastRefill = now;
            this.semaphore = policy.getMaxConcurrent() <= 0 ? null : new Semaphore(policy.getMaxConcurrent());
        }

        private synchronized boolean tryToken(long now) {
            if (qps <= 0) return true;
            long elapsed = Math.max(0L, now - lastRefill);
            tokens = Math.min(burst, tokens + elapsed / 1_000_000_000D * qps);
            lastRefill = now;
            if (tokens < 1D) return false;
            tokens -= 1D;
            return true;
        }

        private boolean tryConcurrent() {
            if (semaphore != null && !semaphore.tryAcquire()) return false;
            active.incrementAndGet();
            return true;
        }

        private void release() {
            active.decrementAndGet();
            if (semaphore != null) semaphore.release();
        }
    }
}
