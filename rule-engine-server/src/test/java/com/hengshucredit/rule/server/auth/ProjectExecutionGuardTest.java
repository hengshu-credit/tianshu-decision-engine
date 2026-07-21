package com.hengshucredit.rule.server.auth;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

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

    private void assertRejected(ProjectExecutionGuard guard, Long authId, ProjectAccessPolicy policy,
                                ProjectExecutionGuard.Reason reason) {
        try {
            guard.acquire(authId, policy);
            Assert.fail("Expected guard rejection");
        } catch (ProjectExecutionGuard.Rejected expected) {
            Assert.assertEquals(reason, expected.getReason());
        }
    }
}
