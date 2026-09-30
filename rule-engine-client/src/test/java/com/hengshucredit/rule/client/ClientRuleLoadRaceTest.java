package com.hengshucredit.rule.client;

import com.hengshucredit.rule.client.cache.CachedRule;
import com.hengshucredit.rule.client.cache.L1MemoryCache;
import com.hengshucredit.rule.client.sync.HttpSyncClient;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.dto.RuleTraceFrame;
import org.junit.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ClientRuleLoadRaceTest {

    @Test
    public void delayedCacheMissRechecksAfterPreviousLoaderCompletes() throws Exception {
        RuleEngineClient client = client();
        CountDownLatch missed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean delayFirstRead = new AtomicBoolean(true);
        AtomicInteger fetches = new AtomicInteger();
        L1MemoryCache cache = new L1MemoryCache(10,
                item -> item.setPreparedScript(client.getEngine().prepare(item.getCompiledScript()))) {
            @Override public CachedRule get(String code) {
                CachedRule found = super.get(code);
                if (delayFirstRead.compareAndSet(true, false)) {
                    missed.countDown();
                    await(release);
                }
                return found;
            }
        };
        set(client, "l1Cache", cache);
        set(client, "httpSyncClient", new HttpSyncClient("http://localhost", 1000) {
            @Override public CachedRule fetchRule(String code) {
                fetches.incrementAndGet();
                return rule(code, 1, 1L, 10);
            }
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<RuleResult> late = executor.submit(() -> client.execute("ROOT", new LinkedHashMap<>()));
            assertTrue(missed.await(3, TimeUnit.SECONDS));
            assertResult(10, client.execute("ROOT", new LinkedHashMap<>()));
            release.countDown();
            assertResult(10, late.get(3, TimeUnit.SECONDS));
            assertEquals("迟到的缓存未命中不能在已加载完成后再次回源", 1, fetches.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
            client.close();
        }
    }

    @Test
    public void rootUsesNewerPublishedRuleWhenOldHttpResponseArrivesLate() throws Exception {
        for (boolean pojo : new boolean[]{false, true}) {
            RuleEngineClient client = client();
            L1MemoryCache cache = (L1MemoryCache) get(client, "l1Cache");
            CachedRule fresh = rule("ROOT", 2, 1L, 20);
            CachedRule stale = rule("ROOT", 1, 1L, 10);
            set(client, "httpSyncClient", new HttpSyncClient("http://localhost", 1000) {
                @Override public CachedRule fetchRule(String code) {
                    // HTTP 获取旧修订后，在响应返回执行线程前，发布推送已将新修订写入缓存。
                    cache.put(fresh);
                    return stale;
                }
            });
            try {
                RuleResult result = pojo ? client.execute("ROOT", new Query())
                        : client.execute("ROOT", new LinkedHashMap<>());
                assertResult(20, result);
                RuleTraceFrame trace = (RuleTraceFrame) result.getTraces().get(0);
                assertEquals(fresh.getRevisionId(), trace.getRevisionId());
                assertSame(fresh, cache.get("ROOT"));
            } finally {
                client.close();
            }
        }
    }

    @Test
    public void childUsesAdmittedRevisionForLatestAndFixedBindingLoads() {
        for (String mode : new String[]{"CODE", "LATEST_ID", "FIXED_ID"}) {
            boolean fixed = "FIXED_ID".equals(mode);
            L1MemoryCache cache = new L1MemoryCache(10);
            CachedRule fresh = rule("CHILD", fixed ? 1 : 2, 2L, 20);
            CachedRule stale = rule("CHILD", 1, 1L, 10);
            fresh.setFixedVersion(fixed);
            stale.setFixedVersion(fixed);
            if (fixed) cache.put(rule("CHILD", 3, 1L, 30));
            HttpSyncClient sync = new HttpSyncClient("http://localhost", 1000) {
                @Override public CachedRule fetchRule(String code) {
                    cache.put(fresh);
                    return stale;
                }
                @Override public CachedRule fetchRuleById(Long id, Long binding) {
                    cache.put(fresh);
                    return stale;
                }
            };
            RuleEngineClientConfig config = new RuleEngineClientConfig();
            config.setProjectId(1L);
            QLExpressEngine engine = new QLExpressEngine();
            ClientRuleRuntimeInvoker invoker = new ClientRuleRuntimeInvoker(cache, sync, engine, config);
            invoker.register(engine.getRunner());
            invoker.enter(rule("ROOT", 1, 1L, 0), new LinkedHashMap<>());
            try {
                Object result = switch (mode) {
                    case "CODE" -> invoker.executeRule("CHILD");
                    case "LATEST_ID" -> invoker.executeRuleById("22");
                    default -> invoker.executeRuleVersionById("22", "81");
                };
                assertEquals(mode, 20, ((Number) result).intValue());
                if (fixed) {
                    assertEquals(3, cache.getById(22L, null).getVersion());
                    assertEquals(Long.valueOf(81L), cache.getById(22L, 81L).getVersionBindingId());
                }
            } finally {
                invoker.exit();
            }
        }
    }

    @Test
    public void failedAndMissingLoadsReleaseSlotForNextAttempt() throws Exception {
        RuleEngineClient client = client();
        AtomicInteger fetches = new AtomicInteger();
        set(client, "httpSyncClient", new HttpSyncClient("http://localhost", 1000) {
            @Override public CachedRule fetchRule(String code) {
                int attempt = fetches.incrementAndGet();
                if (attempt == 1) throw new IllegalStateException("temporary load failure");
                return attempt == 2 ? null : rule(code, 1, 1L, 10);
            }
        });
        try {
            assertThrows(IllegalStateException.class, () -> client.execute("ROOT", new LinkedHashMap<>()));
            assertFalse(client.execute("ROOT", new LinkedHashMap<>()).isSuccess());
            assertResult(10, client.execute("ROOT", new LinkedHashMap<>()));
            assertEquals(3, fetches.get());
            assertTrue(((Map<?, ?>) get(client, "ruleLoads")).isEmpty());
        } finally {
            client.close();
        }
    }

    @Test
    public void uncachedFixedBindingStillExecutesItsExactVersion() {
        L1MemoryCache cache = new L1MemoryCache(1);
        CachedRule latest = rule("CHILD", 3, 1L, 30);
        CachedRule fixed = rule("CHILD", 1, 2L, 20);
        fixed.setFixedVersion(true);
        cache.put(latest);
        AtomicInteger fetches = new AtomicInteger();
        HttpSyncClient sync = new HttpSyncClient("http://localhost", 1000) {
            @Override public CachedRule fetchRuleById(Long id, Long binding) {
                assertEquals(Long.valueOf(22L), id);
                assertEquals(Long.valueOf(81L), binding);
                fetches.incrementAndGet();
                return fixed;
            }
        };
        RuleEngineClientConfig config = new RuleEngineClientConfig();
        config.setProjectId(1L);
        QLExpressEngine engine = new QLExpressEngine();
        ClientRuleRuntimeInvoker invoker = new ClientRuleRuntimeInvoker(cache, sync, engine, config);
        invoker.register(engine.getRunner());
        invoker.enter(rule("ROOT", 1, 1L, 0), new LinkedHashMap<>());
        try {
            assertEquals(20, ((Number) invoker.executeRuleVersionById("22", "81")).intValue());
            assertEquals(20, ((Number) invoker.executeRuleVersionById("22", "81")).intValue());
            assertEquals(1, fetches.get());
            assertEquals(1, cache.size());
            assertSame(latest, cache.getById(22L, null));
            assertNull(cache.getById(22L, 81L));
        } finally {
            invoker.exit();
        }
    }

    @Test
    public void concurrentChildMissesUseOneSharedLoader() throws Exception {
        RuleEngineClient client = client();
        L1MemoryCache cache = (L1MemoryCache) get(client, "l1Cache");
        CachedRule root = rule("ROOT", 1, 1L, 0);
        root.setCompiledScript("executeRule(\"CHILD\");");
        cache.put(root);
        AtomicInteger fetches = new AtomicInteger();
        set(client, "httpSyncClient", new HttpSyncClient("http://localhost", 1000) {
            @Override public CachedRule fetchRule(String code) {
                fetches.incrementAndGet();
                return rule(code, 1, 1L, 42);
            }
        });
        int workers = 8;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var futures = new java.util.ArrayList<Future<RuleResult>>();
            for (int i = 0; i < workers; i++) futures.add(executor.submit(() -> {
                ready.countDown();
                start.await(2, TimeUnit.SECONDS);
                return client.execute("ROOT", new LinkedHashMap<>());
            }));
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            start.countDown();
            for (Future<RuleResult> future : futures) assertResult(42, future.get(3, TimeUnit.SECONDS));
            assertEquals(1, fetches.get());
        } finally {
            start.countDown();
            executor.shutdownNow();
            client.close();
        }
    }

    public static class Query {
        public int getAge() { return 18; }
    }

    private static void assertResult(int expected, RuleResult result) {
        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertEquals(expected, ((Number) result.getResult()).intValue());
    }

    private static RuleEngineClient client() {
        RedisConnectionFactory connection = (RedisConnectionFactory) Proxy.newProxyInstance(
                RedisConnectionFactory.class.getClassLoader(), new Class<?>[]{RedisConnectionFactory.class},
                (proxy, method, args) -> null);
        return RuleEngineClient.builder().connectionFactory(connection).projectId(1L)
                .logReportEnabled(false).build();
    }

    private static CachedRule rule(String code, int version, Long generation, int result) {
        CachedRule rule = new CachedRule();
        rule.setRuleCode(code);
        rule.setDefinitionId("ROOT".equals(code) ? 1L : 22L);
        rule.setVersion(version);
        rule.setVersionBindingId(80L + version);
        rule.setBindingGeneration(generation);
        rule.setRevisionId((long) result);
        rule.setProjectCode("P001");
        rule.setModelType("SCRIPT");
        rule.setCompiledScript("return " + result + ";");
        return rule;
    }

    private static void await(CountDownLatch release) {
        try {
            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Rule load release timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
