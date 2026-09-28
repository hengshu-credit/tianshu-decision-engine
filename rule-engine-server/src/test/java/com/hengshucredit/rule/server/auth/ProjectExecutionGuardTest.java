package com.hengshucredit.rule.server.auth;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProjectExecutionGuardTest {

    @Test
    public void enforcesBurstRefillAndMaximumConcurrency() {
        AtomicLong now = new AtomicLong();
        ProjectExecutionGuard guard = new ProjectExecutionGuard(8, now::get);
        ProjectAccessPolicy ratePolicy = new ProjectAccessPolicy();
        ratePolicy.setQps(1);
        ratePolicy.setBurst(2);

        guard.acquire(1L, ratePolicy).close();
        guard.acquire(1L, ratePolicy).close();
        assertRejected(guard, 1L, ratePolicy, ProjectExecutionGuard.Reason.RATE_LIMITED);
        now.addAndGet(1_000_000_000L);
        guard.acquire(1L, ratePolicy).close();

        ProjectAccessPolicy concurrentPolicy = new ProjectAccessPolicy();
        concurrentPolicy.setMaxConcurrent(1);
        ProjectExecutionGuard.Permit permit = guard.acquire(2L, concurrentPolicy);
        assertRejected(guard, 2L, concurrentPolicy, ProjectExecutionGuard.Reason.CONCURRENT_LIMITED);
        permit.close();
        guard.acquire(2L, concurrentPolicy).close();
    }

    @Test
    public void registryHasAHardCapacity() {
        ProjectExecutionGuard guard = new ProjectExecutionGuard(2, System::nanoTime);
        ProjectAccessPolicy policy = new ProjectAccessPolicy();
        for (long authId = 1; authId <= 5; authId++) guard.acquire(authId, policy).close();

        Assert.assertEquals(2, guard.registrySize());
    }

    @Test
    public void distributedStoreSharesQpsAndConcurrencyAcrossGuardInstances() {
        FakeStore store = new FakeStore();
        ProjectExecutionGuard first = new ProjectExecutionGuard(8, System::nanoTime, store);
        ProjectExecutionGuard second = new ProjectExecutionGuard(8, System::nanoTime, store);

        ProjectAccessPolicy qps = new ProjectAccessPolicy();
        qps.setQps(1);
        qps.setBurst(1);
        first.acquire(7L, qps).close();
        assertRejected(second, 7L, qps, ProjectExecutionGuard.Reason.RATE_LIMITED);

        ProjectAccessPolicy concurrent = new ProjectAccessPolicy();
        concurrent.setMaxConcurrent(1);
        ProjectExecutionGuard.Permit permit = first.acquire(8L, concurrent);
        assertRejected(second, 8L, concurrent, ProjectExecutionGuard.Reason.CONCURRENT_LIMITED);
        permit.close();
        // release is idempotent, and the second guard can acquire after the first closes.
        permit.close();
        second.acquire(8L, concurrent).close();
        Assert.assertEquals(2, store.releaseCalls.get());
    }

    @Test
    public void distributedFailureRejectsWithoutLocalFallback() {
        ProjectExecutionGuard guard = new ProjectExecutionGuard(8, System::nanoTime, new FailingStore());
        try {
            guard.acquire(9L, new ProjectAccessPolicy());
            Assert.fail("Expected distributed guard rejection");
        } catch (ProjectExecutionGuard.Rejected expected) {
            Assert.assertEquals(ProjectExecutionGuard.Reason.DISTRIBUTED_UNAVAILABLE, expected.getReason());
        }
    }

    @Test
    public void distributedPermitRenewsAndReleaseIsIdempotent() throws Exception {
        FakeStore store = new FakeStore();
        ProjectExecutionGuard guard = new ProjectExecutionGuard(8, System::nanoTime, store);
        ProjectAccessPolicy policy = new ProjectAccessPolicy();
        policy.setMaxConcurrent(1);
        ProjectExecutionGuard.Permit permit = guard.acquire(10L, policy);
        long deadline = System.currentTimeMillis() + 1500L;
        while (store.renewCalls.get() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20L);
        }
        Assert.assertTrue("permit should renew before its lease expires", store.renewCalls.get() > 0);
        permit.close();
        permit.close();
        Assert.assertEquals(1, store.releaseCalls.get());
    }

    @Test
    public void lostRenewalInterruptsTheOwningRequestThread() throws Exception {
        AtomicBoolean interrupted = new AtomicBoolean();
        ProjectExecutionGuard guard = new ProjectExecutionGuard(8, System::nanoTime, new LostRenewStore());
        Thread worker = new Thread(() -> {
            ProjectAccessPolicy policy = new ProjectAccessPolicy();
            policy.setMaxConcurrent(1);
            ProjectExecutionGuard.Permit permit = guard.acquire(11L, policy);
            try {
                Thread.sleep(2000L);
            } catch (InterruptedException expected) {
                interrupted.set(true);
            } finally {
                permit.close();
            }
        });
        worker.start();
        worker.join(1500L);
        worker.join(1000L);
        Assert.assertTrue("lost renewal must fail-closed by interrupting the owner", interrupted.get());
    }

    private void assertRejected(ProjectExecutionGuard guard, Long authId, ProjectAccessPolicy policy,
                                ProjectExecutionGuard.Reason reason) {
        try {
            guard.acquire(authId, policy);
            Assert.fail("Expected guard rejection");
        } catch (ProjectExecutionGuard.Rejected expected) {
            Assert.assertEquals(reason, expected.getReason());
        }
    }

    private static class FakeStore implements ProjectExecutionGuardStore {
        private final Map<Long, String> active = new HashMap<>();
        private final Map<Long, Boolean> consumed = new HashMap<>();
        private final AtomicLong renewCalls = new AtomicLong();
        private final AtomicLong releaseCalls = new AtomicLong();

        @Override
        public synchronized AcquireResult acquire(Long authId, ProjectAccessPolicy policy, String permitId) {
            if (policy.getQps() > 0 && consumed.putIfAbsent(authId, Boolean.TRUE) != null) {
                return AcquireResult.rejected(ProjectExecutionGuard.Reason.RATE_LIMITED);
            }
            if (policy.getMaxConcurrent() > 0 && active.containsKey(authId)) {
                return AcquireResult.rejected(ProjectExecutionGuard.Reason.CONCURRENT_LIMITED);
            }
            if (policy.getMaxConcurrent() > 0) active.put(authId, permitId);
            return AcquireResult.allowed(1000L, 0L);
        }

        @Override
        public synchronized boolean renew(Long authId, String permitId,
                                          long leaseMillis, long hardExpiryMillis) {
            renewCalls.incrementAndGet();
            return permitId.equals(active.get(authId));
        }

        @Override
        public synchronized boolean release(Long authId, String permitId) {
            if (!permitId.equals(active.get(authId))) return false;
            active.remove(authId);
            releaseCalls.incrementAndGet();
            return true;
        }
    }

    private static class FailingStore implements ProjectExecutionGuardStore {
        @Override
        public AcquireResult acquire(Long authId, ProjectAccessPolicy policy, String permitId) {
            throw new DistributedUnavailable(new IllegalStateException("offline"));
        }

        @Override
        public boolean renew(Long authId, String permitId, long leaseMillis, long hardExpiryMillis) {
            return false;
        }

        @Override
        public boolean release(Long authId, String permitId) {
            return false;
        }
    }

    private static class LostRenewStore extends FakeStore {
        @Override
        public boolean renew(Long authId, String permitId, long leaseMillis, long hardExpiryMillis) {
            return false;
        }
    }
}
