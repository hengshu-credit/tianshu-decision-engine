package com.hengshucredit.rule.server.service;

interface SftpFileDistributedCache {
    CachedFile get(String cacheKey);

    void put(String cacheKey, CachedFile value);

    void invalidateDatasource(Long datasourceId);

    record CachedFile(byte[] content, long expiresAtMillis) { }
}
