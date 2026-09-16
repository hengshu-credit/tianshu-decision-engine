package com.hengshucredit.rule.client.cache;

import org.junit.Test;

import java.util.Collections;
import java.util.AbstractList;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class L1MemoryCacheTest {
    @Test
    public void preparesBeforeAdmissionAndRejectsInvalidSnapshotAtomically() {
        com.hengshucredit.rule.core.engine.QLExpressEngine engine = new com.hengshucredit.rule.core.engine.QLExpressEngine();
        L1MemoryCache cache = new L1MemoryCache(10,
                item -> item.setPreparedScript(engine.prepare(item.getCompiledScript())));
        CachedRule prior = rule("current");
        prior.setCompiledScript("return 1;");
        cache.put(prior);
        org.junit.Assert.assertNotNull(cache.get("current").getPreparedScript());
        CachedRule replacement = rule("current", 2);
        replacement.setCompiledScript("return 2;");
        CachedRule invalid = rule("invalid");
        invalid.setCompiledScript("if (");
        assertThrows(RuntimeException.class, () -> cache.replaceSnapshot(java.util.List.of(replacement, invalid)));
        org.junit.Assert.assertSame(prior, cache.get("current"));
        assertNull(cache.get("invalid"));
        cache.put(replacement);
        assertEquals(2, engine.execute(cache.get("current").getPreparedScript(), Collections.emptyMap(), false).getResult());
    }
    @Test public void fixedBindingsCoexistAndOutOfOrderOverwriteCannotReplaceNewerGeneration() {
        L1MemoryCache cache = new L1MemoryCache(3);
        CachedRule current = rule("B"); current.setDefinitionId(22L); current.setVersion(2); current.setVersionBindingId(82L); current.setBindingGeneration(2L);
        CachedRule fixed = rule("B"); fixed.setDefinitionId(22L); fixed.setVersion(1); fixed.setVersionBindingId(81L); fixed.setBindingGeneration(1L); fixed.setFixedVersion(true);
        cache.put(current); cache.put(fixed);
        assertEquals(2, cache.getById(22L, null).getVersion());
        assertEquals(1, cache.getById(22L, 81L).getVersion());
        CachedRule stale = rule("B"); stale.setDefinitionId(22L); stale.setVersion(2); stale.setVersionBindingId(82L); stale.setBindingGeneration(1L);
        cache.put(stale); assertEquals(Long.valueOf(2), cache.getById(22L, null).getBindingGeneration());
        cache.invalidateVersions(22L); assertNull(cache.getById(22L, 81L)); assertEquals(1, cache.size());
    }

    @Test
    public void incrementalEvictionProtectsRecentlyReadRule() {
        L1MemoryCache cache = new L1MemoryCache(2);
        cache.put(rule("a"));
        cache.put(rule("b"));
        cache.get("a");
        cache.put(rule("c"));
        assertEquals("a", cache.get("a").getRuleCode());
        assertNull(cache.get("b"));
        assertEquals(2, cache.size());
    }

    @Test
    public void oversizedSnapshotKeepsHotRulesButUsesIncomingVersions() {
        L1MemoryCache cache = new L1MemoryCache(2);
        cache.put(rule("a", 7));
        cache.put(rule("b", 1));
        cache.get("a");
        cache.replaceSnapshot(java.util.List.of(rule("b", 2), rule("c", 2), rule("a", 3)));
        assertEquals(3, cache.get("a").getVersion());
        assertEquals(2, cache.size());
        cache.replaceSnapshot(java.util.List.of(rule("b", 4), rule("c", 4)));
        assertNull(cache.get("a"));
        assertEquals(4, cache.get("b").getVersion());
    }

    @Test
    public void repeatedColdInsertionsDoNotEvictContinuouslyUsedRule() {
        L1MemoryCache cache = new L1MemoryCache(8);
        cache.put(rule("a"));
        int misses = 0;
        for (int i = 0; i < 1000; i++) {
            if (cache.get("a") == null) {
                misses++;
                cache.put(rule("a"));
            }
            cache.put(rule("cold_" + i));
        }
        assertEquals("hot rule misses under 1000 cold insertions", 0, misses);
        assertEquals(8, cache.size());
    }

    @Test
    public void rejectsNonPositiveMaximumSize() {
        assertThrows(IllegalArgumentException.class, () -> new L1MemoryCache(0));
        assertThrows(IllegalArgumentException.class, () -> new L1MemoryCache(-1));
    }

    @Test
    public void replaceSnapshotRemovesRulesMissingFromSuccessfulFullSync() {
        L1MemoryCache cache = new L1MemoryCache(10);
        cache.put(rule("obsolete"));
        cache.put(rule("current"));

        cache.replaceSnapshot(Collections.singletonList(rule("current")));

        assertNull(cache.get("obsolete"));
        assertEquals("current", cache.get("current").getRuleCode());
        assertEquals(1, cache.size());
    }

    @Test
    public void incrementalPutWaitsForSnapshotCommitInsteadOfWritingTheReplacedMap() throws Exception {
        L1MemoryCache cache = new L1MemoryCache(10);
        CountDownLatch snapshotEntered = new CountDownLatch(1);
        CountDownLatch allowSnapshotCommit = new CountDownLatch(1);
        Thread snapshot = new Thread(() -> cache.replaceSnapshot(new BlockingRuleList(
                rule("synced"), snapshotEntered, allowSnapshotCommit)));
        snapshot.start();
        assertEquals(true, snapshotEntered.await(2, TimeUnit.SECONDS));

        Thread incremental = new Thread(() -> cache.put(rule("pushed")));
        incremental.start();
        allowSnapshotCommit.countDown();
        snapshot.join(2000);
        incremental.join(2000);

        assertEquals("synced", cache.get("synced").getRuleCode());
        assertEquals("pushed", cache.get("pushed").getRuleCode());
    }

    @Test
    public void readsCurrentSnapshotWithoutWaitingForSnapshotWriter() throws Exception {
        L1MemoryCache cache = new L1MemoryCache(10);
        cache.put(rule("current", 7));
        CountDownLatch snapshotEntered = new CountDownLatch(1);
        CountDownLatch allowSnapshotCommit = new CountDownLatch(1);
        Thread snapshot = new Thread(() -> cache.replaceSnapshot(new BlockingRuleList(
                rule("replacement", 8), snapshotEntered, allowSnapshotCommit)));
        snapshot.start();
        assertTrue(snapshotEntered.await(2, TimeUnit.SECONDS));

        CountDownLatch readsCompleted = new CountDownLatch(3);
        AtomicReference<CachedRule> found = new AtomicReference<>();
        AtomicInteger size = new AtomicInteger(-1);
        AtomicReference<Map<String, Integer>> versions = new AtomicReference<>();
        Thread getReader = new Thread(() -> {
            found.set(cache.get("current"));
            readsCompleted.countDown();
        });
        Thread sizeReader = new Thread(() -> {
            size.set(cache.size());
            readsCompleted.countDown();
        });
        Thread versionsReader = new Thread(() -> {
            versions.set(cache.getVersions());
            readsCompleted.countDown();
        });
        getReader.start();
        sizeReader.start();
        versionsReader.start();

        boolean completedBeforeCommit;
        try {
            completedBeforeCommit = readsCompleted.await(1, TimeUnit.SECONDS);
        } finally {
            allowSnapshotCommit.countDown();
            snapshot.join(2000);
            getReader.join(2000);
            sizeReader.join(2000);
            versionsReader.join(2000);
        }

        assertTrue("reads should not wait for the snapshot writer", completedBeforeCommit);
        assertFalse(snapshot.isAlive());
        assertFalse(getReader.isAlive());
        assertFalse(sizeReader.isAlive());
        assertFalse(versionsReader.isAlive());
        assertEquals("current", found.get().getRuleCode());
        assertEquals(1, size.get());
        assertEquals(Collections.singletonMap("current", 7), versions.get());
        assertNull(cache.get("current"));
        assertEquals("replacement", cache.get("replacement").getRuleCode());
    }

    private CachedRule rule(String code) {
        return rule(code, 0);
    }

    private CachedRule rule(String code, int version) {
        CachedRule rule = new CachedRule();
        rule.setRuleCode(code);
        rule.setVersion(version);
        return rule;
    }

    private static class BlockingRuleList extends AbstractList<CachedRule> {
        private final CachedRule rule;
        private final CountDownLatch entered;
        private final CountDownLatch release;

        private BlockingRuleList(CachedRule rule, CountDownLatch entered, CountDownLatch release) {
            this.rule = rule;
            this.entered = entered;
            this.release = release;
        }

        @Override
        public CachedRule get(int index) {
            if (index != 0) throw new IndexOutOfBoundsException();
            entered.countDown();
            try {
                if (!release.await(2, TimeUnit.SECONDS)) throw new AssertionError("Snapshot release timed out");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
            return rule;
        }

        @Override
        public int size() {
            return 1;
        }
    }
}
