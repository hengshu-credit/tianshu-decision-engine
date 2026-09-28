package com.hengshucredit.rule.server.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.function.LongSupplier;
import java.util.UUID;

/** authId 维度的有界令牌桶与并发隔离。 */
@Component
public class ProjectExecutionGuard {
    private final int maxEntries;
    private final LongSupplier nanoTime;
    private final LinkedHashMap<Long, State> states = new LinkedHashMap<>(16, 0.75f, true);

    private static final ScheduledExecutorService RENEW_EXECUTOR =
            Executors.newScheduledThreadPool(
                    Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors() / 2)),
                    new RenewThreadFactory());
    private final ProjectExecutionGuardStore distributedStore;

    @Autowired
    public ProjectExecutionGuard(ProjectAuthProperties properties,
                                 ProjectExecutionGuardStore distributedStore) {
        this(properties.getGuardRegistryMaxEntries(), System::nanoTime, distributedStore);
    }

    public ProjectExecutionGuard(int maxEntries, LongSupplier nanoTime) {
        this(maxEntries, nanoTime, null);
    }

    public ProjectExecutionGuard(int maxEntries, LongSupplier nanoTime,
                                 ProjectExecutionGuardStore distributedStore) {
        if (maxEntries <= 0) throw new IllegalArgumentException("guard registry maxEntries 必须大于 0");
        this.maxEntries = maxEntries;
        this.nanoTime = nanoTime;
        this.distributedStore = distributedStore;
    }

    public Permit acquire(Long authId, ProjectAccessPolicy policy) {
        if (authId == null) throw new IllegalArgumentException("authId 不能为空");
        ProjectAccessPolicy effective = policy == null ? new ProjectAccessPolicy() : policy;
        effective.validate();
        if (distributedStore != null) {
            String permitId = UUID.randomUUID().toString();
            ProjectExecutionGuardStore.AcquireResult result;
            try {
                result = distributedStore.acquire(authId, effective, permitId);
            } catch (ProjectExecutionGuardStore.DistributedUnavailable e) {
                throw new Rejected(Reason.DISTRIBUTED_UNAVAILABLE, e);
            }
            if (result == null || !result.isAllowed()) {
                throw new Rejected(result == null || result.getReason() == null
                        ? Reason.DISTRIBUTED_UNAVAILABLE : result.getReason());
            }
            return new Permit(distributedStore, authId, permitId,
                    result.getLeaseMillis(), result.getHardExpiryMillis());
        }
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
        return policy.getQps() + ":" + policy.getBurst() + ":" + policy.getMaxConcurrent()
                + ":" + policy.getRequestTimeoutMs();
    }

    public enum Reason { RATE_LIMITED, CONCURRENT_LIMITED, REGISTRY_FULL, DISTRIBUTED_UNAVAILABLE }

    public static class Rejected extends RuntimeException {
        private final Reason reason;

        private Rejected(Reason reason) {
            super(reason.name());
            this.reason = reason;
        }

        private Rejected(Reason reason, Throwable cause) {
            super(reason.name(), cause);
            this.reason = reason;
        }

        public Reason getReason() { return reason; }
    }

    public static class Permit implements AutoCloseable {
        private final State state;
        private final ProjectExecutionGuardStore distributedStore;
        private final Long authId;
        private final String permitId;
        private final Thread ownerThread;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final AtomicBoolean lost = new AtomicBoolean(false);
        private final Object lifecycle = new Object();
        private final ScheduledFuture<?> renewTask;

        private Permit(State state) {
            this.state = state;
            this.distributedStore = null;
            this.authId = null;
            this.permitId = null;
            this.ownerThread = null;
            this.renewTask = null;
        }

        private Permit(ProjectExecutionGuardStore distributedStore, Long authId, String permitId,
                       long leaseMillis, long hardExpiryMillis) {
            this.state = null;
            this.distributedStore = distributedStore;
            this.authId = authId;
            this.permitId = permitId;
            this.ownerThread = Thread.currentThread();
            long period = Math.max(250L, Math.max(1L, leaseMillis) / 3L);
            this.renewTask = distributedStore == null || leaseMillis <= 0L
                    ? null : RENEW_EXECUTOR.scheduleAtFixedRate(() -> {
                        if (closed.get()) return;
                        try {
                            if (!distributedStore.renew(authId, permitId, leaseMillis, hardExpiryMillis)) {
                                markLost();
                            }
                        } catch (RuntimeException ignored) {
                            markLost();
                        }
                    }, period, period, java.util.concurrent.TimeUnit.MILLISECONDS);
        }

        @Override
        public void close() {
            synchronized (lifecycle) {
                if (!closed.compareAndSet(false, true)) return;
            }
            if (renewTask != null) renewTask.cancel(false);
            if (distributedStore != null) {
                try {
                    distributedStore.release(authId, permitId);
                } catch (ProjectExecutionGuardStore.DistributedUnavailable ignored) {
                    // 释放按 permitId 幂等；Redis 恢复后租约自动过期。
                }
            } else if (state != null) {
                state.release();
            }
        }

        private void markLost() {
            synchronized (lifecycle) {
                if (closed.get() || !lost.compareAndSet(false, true)) return;
                // 让正在阻塞的 Servlet/开放执行线程尽快结束；规则代码若继续计算，
                // 也不能再续租该 permit，Redis 侧会在有界租约后自动回收。
                Thread owner = ownerThread;
                if (owner != null && owner != Thread.currentThread()) owner.interrupt();
            }
        }
    }

    private static final class RenewThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "rule-auth-guard-renew");
            thread.setDaemon(true);
            return thread;
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
