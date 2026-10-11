package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.core.engine.RuntimeContextBridge;
import org.junit.Test;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class AsyncListActionServiceTest {
    private static JSONObject config() { return JSON.parseObject("{\"listIds\":[8],\"operation\":\"MERGE\",\"expiryMode\":\"PERMANENT\",\"itemTypes\":[\"MOBILE\"]}"); }

    @Test public void concurrentSubmissionsRemainDurableWhileWriterIsBlocked() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var complete = new CountDownLatch(65);
        var ids = java.util.concurrent.ConcurrentHashMap.<String>newKeySet();
        var writer = new RuleListActionWriter(null, null) {
            @Override int write(String id, Long project, JSONObject config, List<?> values, LocalDateTime time) {
                entered.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("writer gate timeout"); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
                ids.add(id); complete.countDown(); return 1;
            }
        };
        var callers = java.util.concurrent.Executors.newFixedThreadPool(16);
        try (var service = new AsyncListActionService(writer, Files.createTempDirectory("list-action-concurrent-").toString())) {
            service.start(); service.submit(1L, config(), List.of("first"), LocalDateTime.now());
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            var results = new ArrayList<java.util.concurrent.Future<Object>>();
            for (int i = 0; i < 64; i++) {
                int index = i;
                results.add(callers.submit(() -> service.submit(1L, config(), List.of("value-" + index), LocalDateTime.now())));
            }
            for (var result : results) assertEquals("QUEUED", ((Map<?, ?>) result.get(8, TimeUnit.SECONDS)).get("status"));
            assertEquals(65, ((Number) service.snapshot().get("pending")).intValue());
            assertEquals(1, release.getCount());
            release.countDown(); assertTrue(complete.await(8, TimeUnit.SECONDS));
            assertEquals(65, ids.size());
        } finally { release.countDown(); callers.shutdownNow(); RuntimeContextBridge.clear(); }
    }

    @Test public void blockedDatabaseDoesNotBlockRuleThreadAndValuesAreCopied() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var completed = new CountDownLatch(1);
        var copied = new AtomicReference<List<?>>(); var thread = new AtomicReference<String>();
        var writer = new RuleListActionWriter(null, null) {
            @Override int write(String id, Long project, JSONObject config, List<?> values, LocalDateTime time) {
                thread.set(Thread.currentThread().getName()); entered.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("未释放写入"); }
                catch (InterruptedException error) { throw new AssertionError(error); }
                copied.set(values); completed.countDown(); return 1;
            }
        };
        try (var service = new AsyncListActionService(writer, Files.createTempDirectory("list-action-isolation-").toString())) {
            service.start();
            var values = new ArrayList<>(List.of("13800138000"));
            assertEquals("QUEUED", ((Map<?, ?>) service.submit(1L, config(), values, LocalDateTime.now())).get("status"));
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            values.set(0, "changed-after-action");
            // 工作者仍卡在数据库时，后续规则值可正常更新；不使用调用线程退回写库。
            RuntimeContextBridge.setValue("continued", true);
            assertEquals("rule-list-actions", thread.get());
            assertEquals(1, release.getCount());
            release.countDown(); assertTrue(completed.await(3, TimeUnit.SECONDS));
            assertEquals(List.of("13800138000"), copied.get());
        } finally { release.countDown(); RuntimeContextBridge.clear(); }
    }

    @Test public void failedTaskSurvivesRestartWithSameIdentityAndExpirationOrigin() throws Exception {
        String directory = Files.createTempDirectory("list-action-recovery-").toString();
        var attempted = new CountDownLatch(1); var recovered = new CountDownLatch(1);
        var originalId = new AtomicReference<String>(); var resumedId = new AtomicReference<String>();
        LocalDateTime time = LocalDateTime.of(2026, 10, 10, 10, 0);
        var failing = new RuleListActionWriter(null, null) {
            @Override int write(String id, Long project, JSONObject config, List<?> values, LocalDateTime at) {
                originalId.set(id); attempted.countDown(); throw new IllegalStateException("数据库暂不可用");
            }
        };
        try (var service = new AsyncListActionService(failing, directory)) {
            service.start(); service.submit(1L, config(), List.of("13800138000"), time);
            assertTrue(attempted.await(3, TimeUnit.SECONDS));
        }
        var healthy = new RuleListActionWriter(null, null) {
            @Override int write(String id, Long project, JSONObject config, List<?> values, LocalDateTime at) {
                assertEquals(time, at); assertEquals(List.of("13800138000"), values);
                resumedId.set(id); recovered.countDown(); return 1;
            }
        };
        try (var service = new AsyncListActionService(healthy, directory)) {
            service.start(); assertTrue(recovered.await(3, TimeUnit.SECONDS));
            assertEquals(originalId.get(), resumedId.get());
        }
    }
}
