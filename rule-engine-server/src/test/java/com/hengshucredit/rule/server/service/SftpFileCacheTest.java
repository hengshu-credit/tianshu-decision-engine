package com.hengshucredit.rule.server.service;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class SftpFileCacheTest {
    @Test
    public void cachesSuccessfulFileAcrossLoadsAndCanBeDisabled() throws Exception {
        ExternalCallProperties properties = new ExternalCallProperties();
        SftpFileCache cache = new SftpFileCache(properties, (SftpFileDistributedCache) null);
        AtomicInteger calls = new AtomicInteger();

        Assert.assertArrayEquals(new byte[]{1, 2}, cache.load("SFTP:1:file", 60,
                () -> { calls.incrementAndGet(); return new byte[]{1, 2}; }).content());
        Assert.assertArrayEquals(new byte[]{1, 2}, cache.load("SFTP:1:file", 60,
                () -> { calls.incrementAndGet(); return new byte[]{9}; }).content());
        Assert.assertEquals("缓存命中不应再次读取远端", 1, calls.get());

        cache.load("SFTP:1:no-cache", 0,
                () -> { calls.incrementAndGet(); return new byte[]{3}; });
        cache.load("SFTP:1:no-cache", 0,
                () -> { calls.incrementAndGet(); return new byte[]{4}; });
        Assert.assertEquals("关闭缓存时每个规则都应实时读取", 3, calls.get());
    }

    @Test
    public void coalescesConcurrentMisses() throws Exception {
        ExternalCallProperties properties = new ExternalCallProperties();
        SftpFileCache cache = new SftpFileCache(properties, (SftpFileDistributedCache) null);
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();
        Thread first = new Thread(() -> load(cache, calls, started, release, failures));
        Thread second = new Thread(() -> load(cache, calls, started, release, failures));
        first.start();
        Assert.assertTrue(started.await(2, TimeUnit.SECONDS));
        second.start();
        release.countDown();
        first.join(2000);
        second.join(2000);

        Assert.assertEquals("同一文件并发未命中只能读取一次", 1, calls.get());
        Assert.assertEquals(0, failures.get());
    }

    @Test
    public void sharesSuccessfulContentThroughDistributedCache() throws Exception {
        ExternalCallProperties properties = new ExternalCallProperties();
        FakeDistributedCache distributed = new FakeDistributedCache();
        SftpFileCache firstNode = new SftpFileCache(properties, distributed);
        SftpFileCache secondNode = new SftpFileCache(properties, distributed);
        AtomicInteger remoteReads = new AtomicInteger();

        firstNode.load("SFTP:9:shared", 60,
                () -> { remoteReads.incrementAndGet(); return new byte[]{5, 6}; });
        SftpFileCache.LoadResult shared = secondNode.load("SFTP:9:shared", 60,
                () -> { remoteReads.incrementAndGet(); return new byte[]{0}; });

        Assert.assertArrayEquals(new byte[]{5, 6}, shared.content());
        Assert.assertTrue("第二节点应从分布式缓存命中", shared.cacheHit());
        Assert.assertEquals("跨节点命中不得再次读取远端", 1, remoteReads.get());
    }

    private void load(SftpFileCache cache, AtomicInteger calls, CountDownLatch started,
                      CountDownLatch release, AtomicInteger failures) {
        try {
            cache.load("SFTP:1:concurrent", 60, () -> {
                calls.incrementAndGet();
                started.countDown();
                release.await(2, TimeUnit.SECONDS);
                return new byte[]{8};
            });
        } catch (Exception e) {
            failures.incrementAndGet();
        }
    }

    private static class FakeDistributedCache implements SftpFileDistributedCache {
        private final Map<String, CachedFile> values = new ConcurrentHashMap<>();

        @Override
        public CachedFile get(String cacheKey) { return values.get(cacheKey); }

        @Override
        public void put(String cacheKey, CachedFile value) { values.put(cacheKey, value); }

        @Override
        public void invalidateDatasource(Long datasourceId) {
            values.keySet().removeIf(key -> key.startsWith("SFTP:" + datasourceId + ":"));
        }
    }
}
