package com.hengshucredit.rule.server.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hengshucredit.rule.server.mapper.RuleProjectAuthTokenMapper;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

@Component
public class ProjectTokenLastUsedCoalescer {

    private final ExternalCallProperties properties;
    private final RuleProjectAuthTokenMapper tokenMapper;
    private final Map<Long, LocalDateTime> pending = new LinkedHashMap<>();
    private final Cache<Long, Long> nextAllowedMillis;
    private final ScheduledExecutorService executor;

    public ProjectTokenLastUsedCoalescer(ExternalCallProperties properties,
                                         RuleProjectAuthTokenMapper tokenMapper) {
        this.properties = properties;
        this.tokenMapper = tokenMapper;
        this.nextAllowedMillis = Caffeine.newBuilder()
                .maximumSize(Math.max(1, properties.getTokenLastUsedMaxEntries()))
                .expireAfterAccess(Math.max(60L, properties.getTokenLastUsedCoalesceSeconds() * 2L),
                        TimeUnit.SECONDS)
                .build();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "project-token-last-used-writer");
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newSingleThreadScheduledExecutor(factory);
    }

    @PostConstruct
    public void start() {
        executor.scheduleWithFixedDelay(this::safeFlush, 1L, 1L, TimeUnit.SECONDS);
    }

    public void touch(Long tokenId, LocalDateTime usedTime) {
        if (tokenId == null || usedTime == null) return;
        long nowMillis = usedTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        Long nextAllowed = nextAllowedMillis.getIfPresent(tokenId);
        if (nextAllowed != null && nowMillis < nextAllowed) return;
        nextAllowedMillis.put(tokenId, nowMillis + properties.getTokenLastUsedCoalesceSeconds() * 1000L);
        synchronized (pending) {
            if (pending.containsKey(tokenId) || pending.size() < properties.getTokenLastUsedMaxEntries()) {
                pending.put(tokenId, usedTime);
            }
        }
    }

    void flush() {
        Map<Long, LocalDateTime> batch;
        synchronized (pending) {
            if (pending.isEmpty()) return;
            batch = new LinkedHashMap<>(pending);
            pending.clear();
        }
        for (Map.Entry<Long, LocalDateTime> entry : batch.entrySet()) {
            try {
                tokenMapper.updateLastUsedTime(entry.getKey(), entry.getValue());
            } catch (RuntimeException ignored) {
                // Last-used telemetry must not affect token authentication.
            }
        }
    }

    private void safeFlush() {
        try {
            flush();
        } catch (RuntimeException ignored) {
            // Keep the scheduled writer alive after an unexpected mapper failure.
        }
    }

    @PreDestroy
    public void close() {
        executor.shutdown();
        flush();
    }
}
