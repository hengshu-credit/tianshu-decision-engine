package com.hengshucredit.rule.server.service;

interface ExternalResponseDistributedStore {
    ExternalApiResponseCache.CachedResponse get(Long apiConfigId, String cacheKey);

    void put(Long apiConfigId, String cacheKey, ExternalApiResponseCache.CachedResponse response);

    void invalidateApi(Long apiConfigId);
}
