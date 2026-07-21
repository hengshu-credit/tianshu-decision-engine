package com.hengshucredit.rule.server.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.PostConstruct;

@Data
@ConfigurationProperties(prefix = "rule-engine.external-call")
public class ExternalCallProperties {
    private int httpClientRegistryMaxEntries = 512;
    private int httpMaxRequests = 256;
    private int httpMaxRequestsPerHost = 64;
    private int httpMaxIdleConnections = 32;
    private long httpKeepAliveSeconds = 60L;
    private int tokenCacheMaxEntries = 1024;
    private long tokenDistributedLockSeconds = 5L;
    private long tokenDistributedWaitMillis = 1500L;
    private int apiGuardRegistryMaxEntries = 2048;
    private int responseCacheRegistryMaxEntries = 512;
    private int asyncLogQueueCapacity = 10000;
    private int asyncLogBatchSize = 100;
    private long asyncLogFlushMillis = 200L;
    private long asyncLogShutdownWaitMillis = 2000L;
    private int tokenLastUsedMaxEntries = 10000;
    private long tokenLastUsedCoalesceSeconds = 60L;

    @PostConstruct
    public void validate() {
        if (httpClientRegistryMaxEntries < 1 || httpClientRegistryMaxEntries > 4096) {
            throw new IllegalStateException("external-call.http-client-registry-max-entries must be between 1 and 4096");
        }
        if (httpMaxRequests < 1 || httpMaxRequests > 10000) {
            throw new IllegalStateException("external-call.http-max-requests must be between 1 and 10000");
        }
        if (httpMaxRequestsPerHost < 1 || httpMaxRequestsPerHost > httpMaxRequests) {
            throw new IllegalStateException("external-call.http-max-requests-per-host must be between 1 and http-max-requests");
        }
        if (httpMaxIdleConnections < 0 || httpMaxIdleConnections > 1000) {
            throw new IllegalStateException("external-call.http-max-idle-connections must be between 0 and 1000");
        }
        if (httpKeepAliveSeconds < 1 || httpKeepAliveSeconds > 3600) {
            throw new IllegalStateException("external-call.http-keep-alive-seconds must be between 1 and 3600");
        }
        if (tokenCacheMaxEntries < 1 || tokenCacheMaxEntries > 10000) {
            throw new IllegalStateException("external-call.token-cache-max-entries must be between 1 and 10000");
        }
        if (tokenDistributedLockSeconds < 1 || tokenDistributedLockSeconds > 60) {
            throw new IllegalStateException("external-call.token-distributed-lock-seconds must be between 1 and 60");
        }
        if (tokenDistributedWaitMillis < 0 || tokenDistributedWaitMillis > 10000) {
            throw new IllegalStateException("external-call.token-distributed-wait-millis must be between 0 and 10000");
        }
        if (apiGuardRegistryMaxEntries < 1 || apiGuardRegistryMaxEntries > 10000) {
            throw new IllegalStateException("external-call.api-guard-registry-max-entries must be between 1 and 10000");
        }
        if (responseCacheRegistryMaxEntries < 1 || responseCacheRegistryMaxEntries > 4096) {
            throw new IllegalStateException("external-call.response-cache-registry-max-entries must be between 1 and 4096");
        }
        if (asyncLogQueueCapacity < 2 || asyncLogQueueCapacity > 1000000) {
            throw new IllegalStateException("external-call.async-log-queue-capacity must be between 2 and 1000000");
        }
        if (asyncLogBatchSize < 1 || asyncLogBatchSize > 1000) {
            throw new IllegalStateException("external-call.async-log-batch-size must be between 1 and 1000");
        }
        if (asyncLogFlushMillis < 10 || asyncLogFlushMillis > 10000) {
            throw new IllegalStateException("external-call.async-log-flush-millis must be between 10 and 10000");
        }
        if (asyncLogShutdownWaitMillis < 0 || asyncLogShutdownWaitMillis > 30000) {
            throw new IllegalStateException("external-call.async-log-shutdown-wait-millis must be between 0 and 30000");
        }
        if (tokenLastUsedMaxEntries < 1 || tokenLastUsedMaxEntries > 1000000) {
            throw new IllegalStateException("external-call.token-last-used-max-entries must be between 1 and 1000000");
        }
        if (tokenLastUsedCoalesceSeconds < 1 || tokenLastUsedCoalesceSeconds > 3600) {
            throw new IllegalStateException("external-call.token-last-used-coalesce-seconds must be between 1 and 3600");
        }
    }
}
