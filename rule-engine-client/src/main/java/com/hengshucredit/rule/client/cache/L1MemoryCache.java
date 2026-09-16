package com.hengshucredit.rule.client.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Comparator;

public class L1MemoryCache {

    private static final Logger log = LoggerFactory.getLogger(L1MemoryCache.class);
    private final Object cacheLock = new Object();
    private volatile ConcurrentHashMap<String, CacheEntry> cache;
    private final AtomicLong accessSequence = new AtomicLong();
    private final Map<Long, String> latestById = new LinkedHashMap<>();
    private final int maxSize;
    private final java.util.function.Consumer<CachedRule> prepareRule;

    public L1MemoryCache(int maxSize) {
        this(maxSize, rule -> {});
    }

    public L1MemoryCache(int maxSize, java.util.function.Consumer<CachedRule> prepareRule) {
        this.prepareRule = prepareRule;
        if (maxSize <= 0) {
            throw new IllegalArgumentException("L1 cache maxSize must be greater than zero");
        }
        this.maxSize = maxSize;
        this.cache = new ConcurrentHashMap<>(maxSize);
    }

    public CachedRule get(String ruleCode) {
        CacheEntry entry = cache.get(ruleCode);
        if (entry == null) return null;
        entry.lastAccess = accessSequence.incrementAndGet();
        return entry.rule;
    }

    public void put(CachedRule rule) {
        prepareRule.accept(rule);
        synchronized (cacheLock) {
            String key = key(rule);
            CacheEntry old = cache.get(key);
            if (old != null && older(rule, old.rule)) return;
            if (cache.size() >= maxSize && !cache.containsKey(key)) {
                String toEvict = cache.entrySet().stream()
                        .min(Comparator.comparingLong(entry -> entry.getValue().lastAccess))
                        .orElseThrow().getKey();
                cache.remove(toEvict);
                latestById.values().removeIf(toEvict::equals);
                log.debug("L1 cache evicted: {}", toEvict);
            }
            cache.put(key, new CacheEntry(rule, accessSequence.incrementAndGet()));
            if (!rule.isFixedVersion() && rule.getDefinitionId() != null) latestById.put(rule.getDefinitionId(), key);
        }
    }

    public CachedRule getById(Long definitionId, Long bindingId) {
        synchronized (cacheLock) {
            String key = bindingId == null ? latestById.get(definitionId) : versionKey(definitionId, bindingId);
            return key == null ? null : get(key);
        }
    }

    public void invalidateVersions(Long definitionId) {
        synchronized (cacheLock) {
            cache.entrySet().removeIf(entry -> entry.getValue().rule.isFixedVersion()
                    && java.util.Objects.equals(definitionId, entry.getValue().rule.getDefinitionId()));
        }
    }

    private static String versionKey(Long id, Long binding) { return "#version:" + id + ":" + binding; }
    private static String key(CachedRule rule) { return rule.isFixedVersion() ? versionKey(rule.getDefinitionId(), rule.getVersionBindingId()) : rule.getRuleCode(); }
    private static boolean older(CachedRule incoming, CachedRule current) {
        if (incoming.getDefinitionId() == null || !java.util.Objects.equals(incoming.getDefinitionId(), current.getDefinitionId())) return false;
        return incoming.getVersion() < current.getVersion() || (incoming.getVersion() == current.getVersion()
                && incoming.getBindingGeneration() != null && current.getBindingGeneration() != null
                && incoming.getBindingGeneration() < current.getBindingGeneration());
    }

    public void remove(String ruleCode) {
        synchronized (cacheLock) {
            cache.remove(ruleCode);
            latestById.values().removeIf(ruleCode::equals);
            cache.entrySet().removeIf(entry -> java.util.Objects.equals(ruleCode, entry.getValue().rule.getRuleCode()));
        }
    }

    public void clear() {
        synchronized (cacheLock) {
            cache.clear();
            latestById.clear();
        }
    }

    /**
     * 使用服务端成功返回的完整快照原子替换本地缓存。
     * 构建快照过程中不会触碰当前缓存，避免同步失败或构建异常时丢失已缓存规则。
     */
    public void replaceSnapshot(List<CachedRule> rules) {
        if (rules == null) {
            throw new IllegalArgumentException("Rule snapshot must not be null");
        }
        synchronized (cacheLock) {
            Map<String, CacheEntry> incoming = new LinkedHashMap<>();
            for (CachedRule rule : rules) {
                if (rule == null || rule.getRuleCode() == null) {
                    continue;
                }
                prepareRule.accept(rule);
                CacheEntry prior = cache.get(key(rule));
                incoming.put(key(rule), new CacheEntry(prior != null && older(rule, prior.rule) ? prior.rule : rule, 0));
            }
            // 同步请求和 getVersions 不计为业务访问；保留热度但必须使用本次快照中的内容。
            incoming.forEach((code, entry) -> {
                CacheEntry previous = cache.get(code);
                if (previous != null) entry.lastAccess = previous.lastAccess;
            });
            ConcurrentHashMap<String, CacheEntry> snapshot = new ConcurrentHashMap<>(maxSize);
            incoming.entrySet().stream()
                    .sorted(Comparator.comparingLong(
                            (Map.Entry<String, CacheEntry> entry) -> entry.getValue().lastAccess).reversed())
                    .limit(maxSize)
                    .forEach(entry -> snapshot.put(entry.getKey(), entry.getValue()));
            cache = snapshot;
            latestById.clear();
            snapshot.forEach((key, value) -> { if (!value.rule.isFixedVersion() && value.rule.getDefinitionId() != null) latestById.put(value.rule.getDefinitionId(), key); });
        }
    }

    public int size() {
        return cache.size();
    }

    public Map<String, Integer> getVersions() {
        ConcurrentHashMap<String, CacheEntry> snapshot = cache;
        Map<String, Integer> versions = new LinkedHashMap<>();
        snapshot.forEach((k, v) -> { if (!v.rule.isFixedVersion()) versions.put(k, v.rule.getVersion()); });
        return versions;
    }

    private static final class CacheEntry {
        private final CachedRule rule;
        private volatile long lastAccess;

        private CacheEntry(CachedRule rule, long lastAccess) {
            this.rule = rule;
            this.lastAccess = lastAccess;
        }
    }
}
