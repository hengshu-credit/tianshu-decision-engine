package com.hengshucredit.rule.core.engine;

import com.alibaba.qlexpress4.runtime.function.CustomFunction;
import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.*;

public class RequestFunctionScopeTest {
    @Test public void emptyFrozenScopeAndLegacyScopeRemainDistinctAndRestoreOnException() {
        RequestContext context = new RequestContext();
        CustomFunction parent = (call, args) -> 1;
        assertFalse(context.hasFunctionBindings());
        try (var scope = context.bindFunctions(Map.of("score", parent))) {
            assertTrue(context.hasFunctionBindings()); assertSame(parent, context.function("score"));
            try (var child = context.bindFunctions(Map.of())) {
                assertTrue("空冻结集不能被当成默认注册域", context.hasFunctionBindings());
                assertNull(context.function("score"));
            }
            assertSame(parent, context.function("score"));
            try {
                try (var legacy = context.bindFunctions(null)) {
                    assertFalse(context.hasFunctionBindings()); assertNull(context.function("score"));
                    throw new IllegalStateException("controlled");
                }
            } catch (IllegalStateException expected) { assertEquals("controlled", expected.getMessage()); }
            assertTrue(context.hasFunctionBindings()); assertSame(parent, context.function("score"));
        }
        assertFalse(context.hasFunctionBindings()); assertNull(context.function("score"));
    }

    @Test public void workerInheritsSnapshotButItsNestedScopeCannotChangeParent() {
        RequestContext parent = new RequestContext(); CustomFunction frozen = (call, args) -> 1;
        try (var scope = parent.bindFunctions(Map.of("score", frozen))) {
            RequestContext worker = parent.forkForWorker(event -> { });
            assertTrue(worker.hasFunctionBindings()); assertSame(frozen, worker.function("score"));
            try (var legacy = worker.bindFunctions(null)) {
                assertFalse(worker.hasFunctionBindings());
                assertTrue(parent.hasFunctionBindings()); assertSame(frozen, parent.function("score"));
            }
            assertTrue(worker.hasFunctionBindings()); assertSame(frozen, worker.function("score"));
        }
    }
}
