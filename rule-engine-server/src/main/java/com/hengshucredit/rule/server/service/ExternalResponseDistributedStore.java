package com.hengshucredit.rule.server.service;

interface ExternalResponseDistributedStore {
    ExternalApiResponseCache.CachedResponse get(Long apiConfigId, String cacheKey);

    void put(Long apiConfigId, String cacheKey, ExternalApiResponseCache.CachedResponse response);

    void invalidateApi(Long apiConfigId);

    boolean acquireLoad(Long apiConfigId, String cacheKey, String owner, long leaseMillis);

    void releaseLoad(Long apiConfigId, String cacheKey, String owner);
}
