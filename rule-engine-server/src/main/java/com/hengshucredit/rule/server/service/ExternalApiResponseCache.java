package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;

@Component
public class ExternalApiResponseCache {

    private final ExternalCallProperties properties;
    private final ExternalResponseDistributedStore distributedStore;
    private final LongSupplier currentTimeMillis;
    private final LinkedHashMap<Long, ApiCache> caches = new LinkedHashMap<>(16, 0.75F, true);

    @Autowired
    public ExternalApiResponseCache(ExternalCallProperties properties,
                                    ObjectProvider<RedisExternalResponseCacheStore> storeProvider) {
        this(properties, storeProvider.getIfAvailable(), System::currentTimeMillis);
    }

    ExternalApiResponseCache(ExternalCallProperties properties,
                             ExternalResponseDistributedStore distributedStore,
                             LongSupplier currentTimeMillis) {
        this.properties = properties;
        this.distributedStore = distributedStore;
        this.currentTimeMillis = currentTimeMillis;
    }

    public Lookup get(RuleExternalApiConfig config, String cacheKey) {
        if (config == null || config.getId() == null || cacheKey == null) return null;
        ApiCache apiCache = apiCache(config);
        CachedResponse cached = apiCache.cache.getIfPresent(cacheKey);
        long now = currentTimeMillis.getAsLong();
        if (cached == null && apiCache.settings.redisEnabled && distributedStore != null) {
            try {
                cached = distributedStore.get(config.getId(), cacheKey);
                if (cached != null) apiCache.cache.put(cacheKey, cached);
            } catch (RuntimeException ignored) {
                cached = null;
            }
        }
        if (cached == null) return null;
        if (cached.staleUntilMillis < now) {
            apiCache.cache.invalidate(cacheKey);
            return null;
        }
        return new Lookup(copy(cached.response), now > cached.expiresAtMillis);
    }

    public void put(RuleExternalApiConfig config, String cacheKey, Map<String, Object> response) {
        if (config == null || config.getId() == null || cacheKey == null || response == null) return;
        int ttlSeconds = config.getResponseCacheSeconds() == null
                ? 0 : Math.max(0, config.getResponseCacheSeconds());
        if (ttlSeconds <= 0) return;
        ApiCache apiCache = apiCache(config);
        Map<String, Object> copied = copy(response);
        byte[] serialized = JSON.toJSONString(copied).getBytes(StandardCharsets.UTF_8);
        if (serialized.length > apiCache.settings.maxBytes) return;
        long now = currentTimeMillis.getAsLong();
        long expiresAt = now + ttlSeconds * 1000L;
        long staleUntil = expiresAt + Math.max(0, config.getStaleCacheSeconds() == null
                ? 0 : config.getStaleCacheSeconds()) * 1000L;
        CachedResponse cached = new CachedResponse(copied, expiresAt, staleUntil);
        apiCache.cache.put(cacheKey, cached);
        if (apiCache.settings.redisEnabled && distributedStore != null) {
            try {
                distributedStore.put(config.getId(), cacheKey, cached);
            } catch (RuntimeException ignored) {
                // Redis cache failures do not affect the provider result.
            }
        }
    }

    public LoadResult singleFlight(RuleExternalApiConfig config, String cacheKey,
                                   Callable<Map<String, Object>> loader) throws Exception {
        ApiCache apiCache = apiCache(config);
        if (apiCache.inFlight.size() >= apiCache.settings.maxInFlight) {
            return new LoadResult(loader.call(), false);
        }
        CompletableFuture<Map<String, Object>> created = new CompletableFuture<>();
        CompletableFuture<Map<String, Object>> existing = apiCache.inFlight.putIfAbsent(cacheKey, created);
        if (existing != null) {
            return new LoadResult(copy(await(existing)), true);
        }
        try {
            Map<String, Object> loaded = loader.call();
            created.complete(copy(loaded));
            return new LoadResult(loaded, false);
        } catch (Throwable e) {
            created.completeExceptionally(e);
            if (e instanceof Exception) throw (Exception) e;
            throw new IllegalStateException(e.getMessage(), e);
        } finally {
            apiCache.inFlight.remove(cacheKey, created);
        }
    }

    public synchronized void invalidate(Long apiConfigId) {
        if (apiConfigId == null) return;
        ApiCache removed = caches.remove(apiConfigId);
        if (removed != null) removed.cache.invalidateAll();
        if (distributedStore != null) {
            try {
                distributedStore.invalidateApi(apiConfigId);
            } catch (RuntimeException ignored) {
                // Cache invalidation remains best-effort when Redis is unavailable.
            }
        }
    }

    synchronized long estimatedSize(Long apiConfigId) {
        ApiCache apiCache = caches.get(apiConfigId);
        return apiCache == null ? 0L : apiCache.cache.estimatedSize();
    }

    synchronized void cleanUp(Long apiConfigId) {
        ApiCache apiCache = caches.get(apiConfigId);
        if (apiCache != null) apiCache.cache.cleanUp();
    }

    private synchronized ApiCache apiCache(RuleExternalApiConfig config) {
        CacheSettings settings = CacheSettings.from(config);
        ApiCache existing = caches.get(config.getId());
        if (existing != null && existing.settings.equals(settings)) return existing;
        if (existing != null) existing.cache.invalidateAll();
        int maxRegistries = Math.max(1, properties.getResponseCacheRegistryMaxEntries());
        Iterator<Map.Entry<Long, ApiCache>> iterator = caches.entrySet().iterator();
        while (caches.size() >= maxRegistries && iterator.hasNext()) {
            ApiCache evicted = iterator.next().getValue();
            iterator.remove();
            evicted.cache.invalidateAll();
        }
        ApiCache created = new ApiCache(settings);
        caches.put(config.getId(), created);
        return created;
    }

    private Map<String, Object> await(CompletableFuture<Map<String, Object>> future) throws Exception {
        try {
            int remaining = RequestDeadlineContext.remainingMillis();
            if (remaining == Integer.MAX_VALUE) return future.get();
            if (remaining <= 0) throw new TimeoutException("外数响应等待超过总超时");
            return future.get(remaining, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw new IllegalStateException(cause == null ? "外数请求失败" : cause.getMessage(), cause);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> copy(Map<String, Object> value) {
        if (value == null) return new LinkedHashMap<>();
        return JSON.parseObject(JSON.toJSONString(value), LinkedHashMap.class);
    }

    public static final class Lookup {
        private final Map<String, Object> response;
        private final boolean stale;

        private Lookup(Map<String, Object> response, boolean stale) {
            this.response = response;
            this.stale = stale;
        }

        public Map<String, Object> getResponse() { return response; }
        public boolean isStale() { return stale; }
    }

    public static final class LoadResult {
        private final Map<String, Object> response;
        private final boolean shared;

        private LoadResult(Map<String, Object> response, boolean shared) {
            this.response = response;
            this.shared = shared;
        }

        public Map<String, Object> getResponse() { return response; }
        public boolean isShared() { return shared; }
    }

    public static final class CachedResponse {
        private final Map<String, Object> response;
        private final long expiresAtMillis;
        private final long staleUntilMillis;

        public CachedResponse(Map<String, Object> response, long expiresAtMillis, long staleUntilMillis) {
            this.response = response;
            this.expiresAtMillis = expiresAtMillis;
            this.staleUntilMillis = staleUntilMillis;
        }

        public Map<String, Object> getResponse() { return response; }
        public long getExpiresAtMillis() { return expiresAtMillis; }
        public long getStaleUntilMillis() { return staleUntilMillis; }
    }

    private static final class ApiCache {
        private final CacheSettings settings;
        private final Cache<String, CachedResponse> cache;
        private final ConcurrentHashMap<String, CompletableFuture<Map<String, Object>>> inFlight =
                new ConcurrentHashMap<>();

        private ApiCache(CacheSettings settings) {
            this.settings = settings;
            this.cache = Caffeine.newBuilder().maximumSize(settings.maxSize).build();
        }
    }

    private static final class CacheSettings {
        private final int maxSize;
        private final int maxBytes;
        private final boolean redisEnabled;
        private final int maxInFlight;

        private CacheSettings(int maxSize, int maxBytes, boolean redisEnabled, int maxInFlight) {
            this.maxSize = maxSize;
            this.maxBytes = maxBytes;
            this.redisEnabled = redisEnabled;
            this.maxInFlight = maxInFlight;
        }

        private static CacheSettings from(RuleExternalApiConfig config) {
            int maxSize = config.getResponseCacheMaxSize() == null
                    ? 10000 : Math.max(1, config.getResponseCacheMaxSize());
            int maxConcurrent = config.getMaxConcurrent() == null ? 50 : Math.max(1, config.getMaxConcurrent());
            return new CacheSettings(maxSize,
                    config.getResponseCacheMaxBytes() == null ? 1048576
                            : Math.max(1024, config.getResponseCacheMaxBytes()),
                    Integer.valueOf(1).equals(config.getResponseCacheRedisEnabled()),
                    Math.max(1, Math.min(maxSize, maxConcurrent * 2)));
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof CacheSettings)) return false;
            CacheSettings that = (CacheSettings) other;
            return maxSize == that.maxSize && maxBytes == that.maxBytes
                    && redisEnabled == that.redisEnabled && maxInFlight == that.maxInFlight;
        }

        @Override
        public int hashCode() {
            int result = maxSize;
            result = 31 * result + maxBytes;
            result = 31 * result + (redisEnabled ? 1 : 0);
            return 31 * result + maxInFlight;
        }
    }
}
