package com.hengshucredit.rule.server.derived;

import com.hengshucredit.rule.server.service.VariableResolutionInvocationCache;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.Assert.*;

public class IndexedHistoryCountTest {
    @Test public void sameSnapshotSharesWindowsButNotDifferentCentresOrScopes() {
        var now = LocalDateTime.of(2026, 9, 21, 12, 0);
        var region = new IndexedHistoryCount.Region("VARIABLE:31", "DATA_OBJECT:9", 120.1, 34, 800);
        var selection = new IndexedHistoryCount.Selection("PROJECT", 2L, 10L, now, region);
        var calls = new AtomicInteger();
        var cache = new VariableResolutionInvocationCache();
        Supplier<Map<LocalDateTime, IndexedHistoryCount.Count>> load = () -> {
            calls.incrementAndGet();
            return Map.of(now.minusDays(2), new IndexedHistoryCount.Count(20, 3),
                    now.minusDays(8), new IndexedHistoryCount.Count(90, 11));
        };
        assertEquals(3, cache.historyCount(selection, now.minusDays(2), load).matched());
        assertEquals(90, cache.historyCount(selection, now.minusDays(8), load).candidates());
        assertEquals(1, calls.get());
        cache.historyCount(new IndexedHistoryCount.Selection("RULE", 2L, 11L, now, region), now.minusDays(2), load);
        cache.historyCount(new IndexedHistoryCount.Selection("PROJECT", 2L, 10L, now,
                new IndexedHistoryCount.Region("VARIABLE:31", "DATA_OBJECT:9", -179.99, 34, 800)), now.minusDays(2), load);
        assertEquals(3, calls.get());
        assertThrows(IllegalArgumentException.class, () -> new IndexedHistoryCount.Region("a", "b", 181, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new IndexedHistoryCount.Region("a", "b", 0, 0, Double.NaN));
    }
}
