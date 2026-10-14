package com.hengshucredit.rule.server.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SFTP 文件内容缓存。缓存只保存成功读取的原始字节，变量输出格式在命中后再转换。
 * 同一文件的并发未命中请求通过 single-flight 合并，避免缓存刚失效时击穿远端 SFTP。
 */
@Component
public class SftpFileCache {
    private final ExternalCallProperties properties;
    private final SftpFileDistributedCache distributedCache;
    private final Cache<String, CachedFile> cache;
    private final ConcurrentHashMap<String, CompletableFuture<byte[]>> inFlight = new ConcurrentHashMap<>();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong coalesced = new AtomicLong();
    private final AtomicLong puts = new AtomicLong();

    @Autowired
    public SftpFileCache(ExternalCallProperties properties,
                         ObjectProvider<SftpFileDistributedCache> distributedCacheProvider) {
        this.properties = properties;
        this.distributedCache = properties.isSftpCacheRedisEnabled()
                ? distributedCacheProvider.getIfAvailable() : null;
        int minimumEntryWeight = Math.max(1,
                properties.getSftpCacheMaxBytes() / Math.max(1, properties.getSftpCacheMaxEntries()));
        this.cache = Caffeine.newBuilder()
                .maximumWeight(Math.max(1024, properties.getSftpCacheMaxBytes()))
                .weigher((String key, CachedFile value) -> Math.max(minimumEntryWeight, value.content.length))
                .build();
    }

    SftpFileCache(ExternalCallProperties properties, SftpFileDistributedCache distributedCache) {
        this.properties = properties;
        this.distributedCache = properties.isSftpCacheRedisEnabled() ? distributedCache : null;
        int minimumEntryWeight = Math.max(1,
                properties.getSftpCacheMaxBytes() / Math.max(1, properties.getSftpCacheMaxEntries()));
        this.cache = Caffeine.newBuilder()
                .maximumWeight(Math.max(1024, properties.getSftpCacheMaxBytes()))
                .weigher((String key, CachedFile value) -> Math.max(minimumEntryWeight, value.content.length))
                .build();
    }

    public Lookup get(String key, int cacheSeconds) {
        if (cacheSeconds <= 0 || key == null) return null;
        CachedFile value = cache.getIfPresent(key);
        if (value == null || value.expiresAtMillis < System.currentTimeMillis()) {
            if (value != null) cache.invalidate(key);
            if (distributedCache != null) {
                try {
                    SftpFileDistributedCache.CachedFile distributed = distributedCache.get(key);
                    if (distributed != null && distributed.expiresAtMillis() >= System.currentTimeMillis()) {
                        putLocal(key, distributed.content(), distributed.expiresAtMillis());
                        hits.incrementAndGet();
                        return new Lookup(distributed.content().clone(), true);
                    }
                } catch (RuntimeException ignored) {
                    // Redis failures fall back to a real SFTP read.
                }
            }
            misses.incrementAndGet();
            return null;
        }
        hits.incrementAndGet();
        return new Lookup(value.content.clone(), true);
    }

    public LoadResult load(String key, int cacheSeconds, Callable<byte[]> loader) throws Exception {
        if (loader == null) throw new IllegalArgumentException("SFTP缓存加载器不能为空");
        Lookup cached = get(key, cacheSeconds);
        if (cached != null) return new LoadResult(cached.content, true, false);
        if (cacheSeconds <= 0) return new LoadResult(loader.call(), false, false);

        CompletableFuture<byte[]> created = new CompletableFuture<>();
        CompletableFuture<byte[]> existing = inFlight.putIfAbsent(key, created);
        if (existing != null) {
            coalesced.incrementAndGet();
            return new LoadResult(await(existing), false, true);
        }
        try {
            byte[] content = loader.call();
            putInternal(key, content, cacheSeconds);
            created.complete(content.clone());
            return new LoadResult(content, false, false);
        } catch (Throwable failure) {
            created.completeExceptionally(failure);
            if (failure instanceof Exception exception) throw exception;
            throw new IllegalStateException("SFTP文件读取失败", failure);
        } finally {
            inFlight.remove(key, created);
        }
    }

    /** 为同一远端文件保留一个未规范化路径别名，避免后续命中前再次建立连接。 */
    public void put(String key, byte[] content, int cacheSeconds) {
        putInternal(key, content, cacheSeconds);
    }

    public void invalidateDatasource(Long datasourceId) {
        if (datasourceId == null) return;
        String prefix = "SFTP:" + datasourceId + ":";
        cache.asMap().keySet().removeIf(key -> key.startsWith(prefix));
        if (distributedCache != null) {
            try { distributedCache.invalidateDatasource(datasourceId); }
            catch (RuntimeException ignored) { }
        }
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entries", cache.estimatedSize());
        result.put("inFlight", inFlight.size());
        result.put("hits", hits.get());
        result.put("misses", misses.get());
        result.put("coalesced", coalesced.get());
        result.put("puts", puts.get());
        result.put("maxEntries", properties.getSftpCacheMaxEntries());
        result.put("maxBytes", properties.getSftpCacheMaxBytes());
        result.put("redisEnabled", distributedCache != null);
        return result;
    }

    public String key(Long datasourceId, String remotePath) {
        if (datasourceId == null || remotePath == null) return null;
        return "SFTP:" + datasourceId + ":" + sha256(remotePath.trim().replace('\\', '/'));
    }

    private void putInternal(String key, byte[] content, int cacheSeconds) {
        if (key == null || content == null || cacheSeconds <= 0
                || content.length > properties.getSftpCacheMaxBytes()) return;
        long expiresAt = System.currentTimeMillis() + cacheSeconds * 1000L;
        putLocal(key, content, expiresAt);
        puts.incrementAndGet();
        if (distributedCache != null) {
            try { distributedCache.put(key, new SftpFileDistributedCache.CachedFile(content.clone(), expiresAt)); }
            catch (RuntimeException ignored) { }
        }
    }

    private void putLocal(String key, byte[] content, long expiresAtMillis) {
        if (key == null || content == null || expiresAtMillis <= System.currentTimeMillis()
                || content.length > properties.getSftpCacheMaxBytes()) return;
        cache.put(key, new CachedFile(content.clone(), expiresAtMillis));
    }

    private byte[] await(CompletableFuture<byte[]> future) throws Exception {
        try {
            return future.get();
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) throw exception;
            throw new IllegalStateException("SFTP文件读取失败", cause);
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format("%02x", item & 0xff));
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SFTP缓存键摘要计算失败", e);
        }
    }

    public record Lookup(byte[] content, boolean hit) { }

    public record LoadResult(byte[] content, boolean cacheHit, boolean shared) { }

    private record CachedFile(byte[] content, long expiresAtMillis) { }
}
