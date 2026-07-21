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
