package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ExternalApiResponseCacheTest {

    @Test
    public void enforcesEntryAndPayloadBounds() {
        ExternalApiResponseCache cache = cache(new AtomicLong(1000L));
        RuleExternalApiConfig config = config();
        config.setResponseCacheMaxSize(2);
        config.setResponseCacheMaxBytes(1024);

        cache.put(config, "1", Collections.singletonMap("v", "1"));
        cache.put(config, "2", Collections.singletonMap("v", "2"));
        cache.put(config, "3", Collections.singletonMap("v", "3"));
        cache.cleanUp(config.getId());
        assertTrue(cache.estimatedSize(config.getId()) <= 2L);

        String largePayload = new String(new char[2048]).replace('\0', 'x');
        cache.put(config, "large", Collections.singletonMap("v", largePayload));
        assertNull(cache.get(config, "large"));
    }

    @Test
    public void returnsFreshAndConfiguredStaleEntries() {
        AtomicLong clock = new AtomicLong(1000L);
        ExternalApiResponseCache cache = cache(clock);
        RuleExternalApiConfig config = config();
        config.setResponseCacheSeconds(1);
        config.setStaleCacheSeconds(5);
        cache.put(config, "key", Collections.singletonMap("v", 1));

        assertFalse(cache.get(config, "key").isStale());
        clock.addAndGet(2000L);
        assertTrue(cache.get(config, "key").isStale());
        clock.addAndGet(5000L);
        assertNull(cache.get(config, "key"));
    }

    @Test
    public void concurrentMissesUseOneLoader() throws Exception {
        ExternalApiResponseCache cache = cache(new AtomicLong(1000L));
        RuleExternalApiConfig config = config();
        AtomicInteger loads = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<ExternalApiResponseCache.LoadResult>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(executor.submit(() -> cache.singleFlight(config, "key", () -> {
                    loads.incrementAndGet();
                    Thread.sleep(50L);
                    return Collections.singletonMap("v", 1);
                })));
            }
            int shared = 0;
            for (Future<ExternalApiResponseCache.LoadResult> future : futures) {
                if (future.get().isShared()) shared++;
            }
            assertEquals(1, loads.get());
            assertEquals(7, shared);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void cachedOrderKeepsNullsExactDecimalsAndIndependentCopies() {
        var cache = cache(new AtomicLong(1000L));
        var cfg = config();
        Map<String, Object> shared = new java.util.LinkedHashMap<>();
        shared.put("missingScore", null);
        shared.put("amount", new java.math.BigDecimal("12000.1234567890123456789"));
        Map<String, Object> body = Map.of("first", shared, "second", shared);
        cache.put(cfg, "same-subject", body);
        shared.clear();
        var hit = cache.get(cfg, "same-subject").getResponse();
        assertEquals(Map.of("first", hit.get("first"), "second", hit.get("first")), hit);
        assertEquals(new java.math.BigDecimal("12000.1234567890123456789"), ((Map<?, ?>)hit.get("second")).get("amount"));
        assertTrue(((Map<?, ?>)hit.get("first")).containsKey("missingScore"));
        ((Map<?, ?>)hit.get("first")).clear();
        assertEquals(2, ((Map<?, ?>)cache.get(cfg, "same-subject").getResponse().get("first")).size());
    }

    @Test
    public void twoNodesShareOneColdLoadAndReleaseTheLeaseAfterFailure() throws Exception {
        var value = new java.util.concurrent.atomic.AtomicReference<ExternalApiResponseCache.CachedResponse>();
        var owner = new java.util.concurrent.atomic.AtomicReference<String>();
        var joined = new java.util.concurrent.CountDownLatch(1);
        var distributed = new ExternalResponseDistributedStore() {
            public ExternalApiResponseCache.CachedResponse get(Long id, String key) { return value.get(); }
            public void put(Long id, String key, ExternalApiResponseCache.CachedResponse response) { value.set(response); }
            public void invalidateApi(Long id) { value.set(null); }
            public boolean acquireLoad(Long id, String key, String token, long lease) {
                boolean ok = owner.compareAndSet(null, token);
                if (!ok) joined.countDown();
                return ok;
            }
            public void releaseLoad(Long id, String key, String token) { assertTrue(owner.compareAndSet(token, null)); }
        };
        var config = config();
        config.setResponseCacheRedisEnabled(1);
        var first = new ExternalApiResponseCache(new ExternalCallProperties(), distributed, System::currentTimeMillis);
        var second = new ExternalApiResponseCache(new ExternalCallProperties(), distributed, System::currentTimeMillis);
        org.junit.Assert.assertThrows(java.io.IOException.class, () -> first.singleFlight(config, "key", () -> {
            throw new java.io.IOException("provider unavailable");
        }));
        assertNull(owner.get());

        var pool = Executors.newFixedThreadPool(2);
        var loads = new AtomicInteger();
        try {
            var jobs = new ArrayList<Future<ExternalApiResponseCache.LoadResult>>();
            for (var node : List.of(first, second)) jobs.add(pool.submit(() -> node.singleFlight(config, "key", () -> {
                loads.incrementAndGet();
                assertTrue(joined.await(2, java.util.concurrent.TimeUnit.SECONDS));
                Map<String, Object> response = Map.of("approvedLimit", 21000);
                node.put(config, "key", response);
                return response;
            })));
            var a = jobs.get(0).get();
            var b = jobs.get(1).get();
            assertEquals(1, loads.get());
            assertEquals(a.getResponse(), b.getResponse());
            assertTrue(a.isShared() != b.isShared());
            assertNull(owner.get());
        } finally {
            pool.shutdownNow();
        }
    }

    private ExternalApiResponseCache cache(AtomicLong clock) {
        ExternalCallProperties properties = new ExternalCallProperties();
        properties.setResponseCacheRegistryMaxEntries(8);
        return new ExternalApiResponseCache(properties, null, clock::get);
    }

    private RuleExternalApiConfig config() {
        RuleExternalApiConfig config = new RuleExternalApiConfig();
        config.setId(1L);
        config.setResponseCacheSeconds(60);
        config.setResponseCacheMaxSize(100);
        config.setResponseCacheMaxBytes(1024);
        config.setResponseCacheRedisEnabled(0);
        config.setStaleCacheSeconds(0);
        return config;
    }
}
