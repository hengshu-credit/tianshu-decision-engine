package com.hengshucredit.rule.server.service;

interface ExternalTokenDistributedStore {
    ExternalTokenCache.CachedToken get(String cacheKey);

    void put(String cacheKey, ExternalTokenCache.CachedToken token);

    void invalidate(String cacheKey);

    boolean tryLock(String cacheKey, String owner, long lockSeconds);

    void unlock(String cacheKey, String owner);
}
