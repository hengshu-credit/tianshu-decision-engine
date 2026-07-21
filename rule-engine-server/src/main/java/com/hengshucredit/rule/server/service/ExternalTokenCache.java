package com.hengshucredit.rule.server.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class ExternalTokenCache {

    private static final long EXPIRY_SAFETY_MILLIS = 1000L;

    private final ExternalCallProperties properties;
    private final ExternalTokenDistributedStore distributedStore;
    private final LinkedHashMap<String, CachedToken> localCache = new LinkedHashMap<>(16, 0.75F, true);
    private final ConcurrentHashMap<String, CompletableFuture<CachedToken>> inFlight = new ConcurrentHashMap<>();

    @Autowired
    public ExternalTokenCache(ExternalCallProperties properties,
                              ObjectProvider<RedisExternalTokenStore> distributedStoreProvider) {
        this(properties, distributedStoreProvider.getIfAvailable());
    }

    ExternalTokenCache(ExternalCallProperties properties, ExternalTokenDistributedStore distributedStore) {
        this.properties = properties;
        this.distributedStore = distributedStore;
    }

    public String getOrLoad(String cacheKey, boolean forceRefresh, TokenLoader loader) {
        return getOrLoadResult(cacheKey, forceRefresh, loader).token;
    }

    public TokenResult getOrLoadResult(String cacheKey, boolean forceRefresh, TokenLoader loader) {
        if (cacheKey == null || cacheKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Token cache key must not be blank");
        }
        if (forceRefresh) {
            invalidate(cacheKey);
        } else {
            CachedToken cached = getLocal(cacheKey);
            if (isUsable(cached)) {
                return new TokenResult(cached, "L1_HIT");
            }
            cached = getDistributed(cacheKey);
            if (isUsable(cached)) {
                putLocal(cacheKey, cached);
                return new TokenResult(cached, "L2_HIT");
            }
        }

        CompletableFuture<CachedToken> created = new CompletableFuture<>();
        CompletableFuture<CachedToken> existing = inFlight.putIfAbsent(cacheKey, created);
        if (existing != null) {
            return new TokenResult(await(existing), "COALESCED");
        }
        try {
            TokenResult loaded = loadWithDistributedLock(cacheKey, forceRefresh, loader);
            created.complete(loaded.cachedToken);
            return loaded;
        } catch (Throwable e) {
            created.completeExceptionally(e);
            throw propagate(e);
        } finally {
            inFlight.remove(cacheKey, created);
        }
    }

    public void invalidate(String cacheKey) {
        synchronized (localCache) {
            localCache.remove(cacheKey);
        }
        if (distributedStore != null) {
            try {
                distributedStore.invalidate(cacheKey);
            } catch (RuntimeException ignored) {
                // Redis failure must not prevent a provider token refresh.
            }
        }
    }

    int localSize() {
        synchronized (localCache) {
            return localCache.size();
        }
    }

    private TokenResult loadWithDistributedLock(String cacheKey, boolean forceRefresh,
                                                TokenLoader loader) throws Exception {
        if (distributedStore == null) {
            return loadedResult(loadAndCache(cacheKey, loader, false), forceRefresh);
        }
        String owner = UUID.randomUUID().toString();
        boolean locked;
        try {
            locked = distributedStore.tryLock(cacheKey, owner, properties.getTokenDistributedLockSeconds());
        } catch (RuntimeException redisFailure) {
            return loadedResult(loadAndCache(cacheKey, loader, false), forceRefresh);
        }
        try {
            if (locked) {
                CachedToken cached = getDistributed(cacheKey);
                if (isUsable(cached)) {
                    putLocal(cacheKey, cached);
                    return new TokenResult(cached, "L2_HIT");
                }
                return loadedResult(loadAndCache(cacheKey, loader, true), forceRefresh);
            }
            long waitMillis = boundedWaitMillis();
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(waitMillis);
            do {
                CachedToken cached = getDistributed(cacheKey);
                if (isUsable(cached)) {
                    putLocal(cacheKey, cached);
                    return new TokenResult(cached, "L2_HIT");
                }
                if (waitMillis == 0 || System.nanoTime() >= deadline) {
                    break;
                }
                Thread.sleep(Math.min(25L, Math.max(1L,
                        TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()))));
            } while (System.nanoTime() < deadline);
            return loadedResult(loadAndCache(cacheKey, loader, true), forceRefresh);
        } finally {
            if (locked) {
                try {
                    distributedStore.unlock(cacheKey, owner);
                } catch (RuntimeException ignored) {
                    // The Redis lock has a short TTL and is owner-checked on release.
                }
            }
        }
    }

    private TokenResult loadedResult(CachedToken token, boolean forceRefresh) {
        return new TokenResult(token, forceRefresh ? "REFRESH" : "FETCH");
    }

    private CachedToken loadAndCache(String cacheKey, TokenLoader loader, boolean writeDistributed) throws Exception {
        CachedToken loaded = loader.load();
        if (loaded == null || loaded.token == null || loaded.token.trim().isEmpty()) {
            throw new IllegalStateException("Token interface returned an empty token");
        }
        if (isUsable(loaded)) {
            putLocal(cacheKey, loaded);
            if (writeDistributed && distributedStore != null) {
                try {
                    distributedStore.put(cacheKey, loaded);
                } catch (RuntimeException ignored) {
                    // L1 caching and the provider result remain usable when Redis is unavailable.
                }
            }
        }
        return loaded;
    }

    private CachedToken getDistributed(String cacheKey) {
        if (distributedStore == null) {
            return null;
        }
        try {
            return distributedStore.get(cacheKey);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private CachedToken getLocal(String cacheKey) {
        synchronized (localCache) {
            CachedToken cached = localCache.get(cacheKey);
            if (!isUsable(cached)) {
                localCache.remove(cacheKey);
                return null;
            }
            return cached;
        }
    }

    private void putLocal(String cacheKey, CachedToken token) {
        synchronized (localCache) {
            localCache.put(cacheKey, token);
            int maxEntries = Math.max(1, properties.getTokenCacheMaxEntries());
            Iterator<Map.Entry<String, CachedToken>> iterator = localCache.entrySet().iterator();
            while (localCache.size() > maxEntries && iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
    }

    private boolean isUsable(CachedToken token) {
        return token != null && token.token != null && !token.token.isEmpty()
                && token.usableExpiresAtMillis - EXPIRY_SAFETY_MILLIS > System.currentTimeMillis();
    }

    private long boundedWaitMillis() {
        long configured = Math.max(0L, properties.getTokenDistributedWaitMillis());
        int requestRemaining = RequestDeadlineContext.remainingMillis();
        return Math.min(configured, requestRemaining == Integer.MAX_VALUE ? configured : requestRemaining);
    }

    private CachedToken await(CompletableFuture<CachedToken> future) {
        try {
            long waitMillis = boundedWaitMillis();
            return future.get(waitMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for token refresh", e);
        } catch (ExecutionException e) {
            throw propagate(e.getCause());
        } catch (TimeoutException e) {
            throw new IllegalStateException("Timed out waiting for token refresh", e);
        }
    }

    private RuntimeException propagate(Throwable error) {
        if (error instanceof RuntimeException) {
            return (RuntimeException) error;
        }
        return new IllegalStateException(error == null ? "Token refresh failed" : error.getMessage(), error);
    }

    @FunctionalInterface
    public interface TokenLoader {
        CachedToken load() throws Exception;
    }

    public static final class CachedToken {
        private final String token;
        private final long expiresAtMillis;
        private final long usableExpiresAtMillis;

        public CachedToken(String token, long expiresAtMillis) {
            this(token, expiresAtMillis, expiresAtMillis);
        }

        public CachedToken(String token, long expiresAtMillis, long usableExpiresAtMillis) {
            this.token = token;
            this.expiresAtMillis = expiresAtMillis;
            this.usableExpiresAtMillis = Math.min(expiresAtMillis, usableExpiresAtMillis);
        }

        public String getToken() {
            return token;
        }

        public long getExpiresAtMillis() {
            return expiresAtMillis;
        }

        public long getUsableExpiresAtMillis() {
            return usableExpiresAtMillis;
        }
    }

    public static final class TokenResult {
        private final CachedToken cachedToken;
        private final String token;
        private final String cacheStatus;

        private TokenResult(CachedToken cachedToken, String cacheStatus) {
            this.cachedToken = cachedToken;
            this.token = cachedToken.token;
            this.cacheStatus = cacheStatus;
        }

        public String getToken() {
            return token;
        }

        public String getCacheStatus() {
            return cacheStatus;
        }
    }
}
