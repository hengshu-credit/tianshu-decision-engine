package com.hengshucredit.rule.server.service;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ExternalTokenCacheTest {

    @Test
    public void cachesLoadedTokenAndForceRefreshReplacesIt() {
        ExternalCallProperties properties = properties(8);
        ExternalTokenCache cache = new ExternalTokenCache(properties, (ExternalTokenDistributedStore) null);
        AtomicInteger loads = new AtomicInteger();

        String first = cache.getOrLoad("api:1", false, () -> token("t" + loads.incrementAndGet()));
        String cached = cache.getOrLoad("api:1", false, () -> token("unexpected"));
        String refreshed = cache.getOrLoad("api:1", true, () -> token("t" + loads.incrementAndGet()));

        assertEquals("t1", first);
        assertEquals("t1", cached);
        assertEquals("t2", refreshed);
        assertEquals(2, loads.get());
    }

    @Test
    public void reportsFetchHitAndRefreshStatusesWithoutExposingCacheInternals() {
        ExternalTokenCache cache = new ExternalTokenCache(properties(8),
                (ExternalTokenDistributedStore) null);

        ExternalTokenCache.TokenResult fetched = cache.getOrLoadResult(
                "api:1", false, () -> token("t1"));
        ExternalTokenCache.TokenResult hit = cache.getOrLoadResult(
                "api:1", false, () -> token("unexpected"));
        ExternalTokenCache.TokenResult refreshed = cache.getOrLoadResult(
                "api:1", true, () -> token("t2"));

        assertEquals("FETCH", fetched.getCacheStatus());
        assertEquals("L1_HIT", hit.getCacheStatus());
        assertEquals("REFRESH", refreshed.getCacheStatus());
        assertEquals("t2", refreshed.getToken());
    }

    @Test
    public void concurrentMissesUseOneLocalLoader() throws Exception {
        ExternalTokenCache cache = new ExternalTokenCache(properties(8), (ExternalTokenDistributedStore) null);
        AtomicInteger loads = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(executor.submit(() -> cache.getOrLoad("api:1", false, () -> {
                    loads.incrementAndGet();
                    Thread.sleep(50L);
                    return token("shared");
                })));
            }
            for (Future<String> future : futures) {
                assertEquals("shared", future.get());
            }
            assertEquals(1, loads.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void localCacheIsBounded() {
        ExternalTokenCache cache = new ExternalTokenCache(properties(2), (ExternalTokenDistributedStore) null);

        cache.getOrLoad("api:1", false, () -> token("1"));
        cache.getOrLoad("api:2", false, () -> token("2"));
        cache.getOrLoad("api:3", false, () -> token("3"));

        assertEquals(2, cache.localSize());
    }

    @Test
    public void zeroSingleFlightWaitFailsImmediatelyInsteadOfWaitingWithoutBound() throws Exception {
        ExternalCallProperties properties = properties(8);
        properties.setTokenDistributedWaitMillis(0L);
        ExternalTokenCache cache = new ExternalTokenCache(properties, (ExternalTokenDistributedStore) null);
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> first = executor.submit(() -> cache.getOrLoad("api:1", false, () -> {
                loading.countDown();
                release.await(2, TimeUnit.SECONDS);
                return token("shared");
            }));
            assertTrue(loading.await(1, TimeUnit.SECONDS));

            long start = System.currentTimeMillis();
            try {
                cache.getOrLoad("api:1", false, () -> token("unexpected"));
                fail("expected an immediate single-flight timeout");
            } catch (IllegalStateException e) {
                assertTrue(e.getMessage().contains("Timed out"));
            }
            assertTrue(System.currentTimeMillis() - start < 500L);

            release.countDown();
            assertEquals("shared", first.get(1, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void secondInstanceReadsTokenFromDistributedCache() {
        InMemoryDistributedStore store = new InMemoryDistributedStore();
        ExternalTokenCache first = new ExternalTokenCache(properties(8), store);
        ExternalTokenCache second = new ExternalTokenCache(properties(8), store);
        AtomicInteger secondLoads = new AtomicInteger();

        assertEquals("shared", first.getOrLoad("api:1", false, () -> token("shared")));
        assertEquals("shared", second.getOrLoad("api:1", false, () -> {
            secondLoads.incrementAndGet();
            return token("unexpected");
        }));

        assertEquals(0, secondLoads.get());
    }

    private ExternalCallProperties properties(int maxEntries) {
        ExternalCallProperties properties = new ExternalCallProperties();
        properties.setTokenCacheMaxEntries(maxEntries);
        return properties;
    }

    private ExternalTokenCache.CachedToken token(String value) {
        return new ExternalTokenCache.CachedToken(value, System.currentTimeMillis() + 60000L);
    }

    private static class InMemoryDistributedStore implements ExternalTokenDistributedStore {
        private final Map<String, ExternalTokenCache.CachedToken> tokens = new ConcurrentHashMap<>();
        private final Map<String, String> locks = new ConcurrentHashMap<>();

        @Override
        public ExternalTokenCache.CachedToken get(String cacheKey) {
            return tokens.get(cacheKey);
        }

        @Override
        public void put(String cacheKey, ExternalTokenCache.CachedToken token) {
            tokens.put(cacheKey, token);
        }

        @Override
        public void invalidate(String cacheKey) {
            tokens.remove(cacheKey);
        }

        @Override
        public boolean tryLock(String cacheKey, String owner, long lockSeconds) {
            return locks.putIfAbsent(cacheKey, owner) == null;
        }

        @Override
        public void unlock(String cacheKey, String owner) {
            locks.remove(cacheKey, owner);
        }
    }
}
