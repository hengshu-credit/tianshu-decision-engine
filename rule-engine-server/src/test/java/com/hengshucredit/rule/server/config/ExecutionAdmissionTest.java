package com.hengshucredit.rule.server.config;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExecutionAdmissionTest {
    @Test
    public void authenticatedCallbackProjectsKeepDistinctBoundedSlotsWhenMainLaneIsFull() {
        var main = new ExecutionAdmission(1, 0, 10);
        main.configureCallbacks("9", 1);
        assertTrue(main.enter(System.nanoTime(), 10));
        try {
            assertFalse(main.forProject(8L).enter(System.nanoTime(), 10));
            var callbacks = main.forProject(9L);
            assertTrue(callbacks.enter(System.nanoTime(), 10));
            try {
                assertFalse(callbacks.enter(System.nanoTime(), 10));
            } finally {
                callbacks.leave();
            }
        } finally {
            main.leave();
        }
    }

    @Test public void boundsUnstartedWorkAndAccountsForTheOriginalDeadline() throws Exception {
        var guard = new ExecutionAdmission(1, 1, 5000);
        assertTrue(guard.enter(System.nanoTime(), 0));
        assertFalse(guard.enter(System.nanoTime() - TimeUnit.SECONDS.toNanos(2), 100));
        var waiter = CompletableFuture.supplyAsync(() -> guard.enter(System.nanoTime(), 5000));
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (guard.depth()==0 && System.nanoTime()<deadline) Thread.sleep(5);
            assertEquals(1, guard.depth());
            assertFalse(guard.enter(System.nanoTime(), 5000));
        } finally { guard.leave(); }
        assertTrue(waiter.get(2, TimeUnit.SECONDS));
        guard.leave();
    }
}
