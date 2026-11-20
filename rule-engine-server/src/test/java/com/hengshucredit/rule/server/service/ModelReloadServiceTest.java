package com.hengshucredit.rule.server.service;

import org.junit.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class ModelReloadServiceTest {
    @Test public void onlyCommittedPublicationLoadsLocallyAndBroadcastsItsExactVersion() throws Exception {
        var loaded = new LinkedBlockingQueue<String>();
        List<String> sent = new ArrayList<>();
        var reload = new ModelReloadService() {
            @Override void loadCurrent(long id, int version, boolean startup) { loaded.add(id + "/" + version); }
        };
        ReflectionTestUtils.setField(reload, "redis", new StringRedisTemplate() {
            @Override public Long convertAndSend(String channel, Object message) {
                sent.add(channel + "/" + message);
                return 1L;
            }
        });
        ReflectionTestUtils.setField(reload, "listeners", new RedisMessageListenerContainer());
        try {
            TransactionSynchronizationManager.initSynchronization();
            try {
                reload.afterPublication(4, 8);
                assertTrue(sent.isEmpty());
                assertTrue(loaded.isEmpty());
            } finally { TransactionSynchronizationManager.clearSynchronization(); }
            TransactionSynchronizationManager.initSynchronization();
            try {
                reload.afterPublication(4, 9);
                TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
            } finally { TransactionSynchronizationManager.clearSynchronization(); }
            assertEquals("4/9",loaded.poll(5, TimeUnit.SECONDS));
            assertEquals(1,sent.size());
            assertTrue(sent.get(0).startsWith("rule:model:published/"));
            assertTrue(sent.get(0).endsWith(":4:9"));
        } finally { reload.close(); }
    }
}
