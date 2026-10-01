package com.hengshucredit.rule.client.sync;

import com.hengshucredit.rule.client.cache.L1MemoryCache;
import com.hengshucredit.rule.client.cache.CachedRule;
import com.hengshucredit.rule.client.function.ClientFunctionRegistrar;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import org.junit.Test;
import org.springframework.data.redis.listener.Topic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.context.support.StaticApplicationContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class RedisSubscriberTest {

    @Test
    public void subscribesProjectPushChannelAndBroadcastChannel() throws Exception {
        RedisSubscriber subscriber = new RedisSubscriber(new L1MemoryCache(10), null, "credit_project");

        assertEquals("rule:push:credit_project", getField(subscriber, "channel"));
        List<?> topics = (List<?>) getField(subscriber, "topics");
        assertEquals(2, topics.size());
        assertEquals("rule:push:credit_project", ((Topic) topics.get(0)).getTopic());
        assertEquals("rule:push:broadcast", ((Topic) topics.get(1)).getTopic());
    }

    @Test
    public void storesRootOutputScriptNamesFromPublishMessage() throws Exception {
        L1MemoryCache cache = new L1MemoryCache(10);
        RedisSubscriber subscriber = new RedisSubscriber(cache, null, "credit_project");
        Method handleMessage = RedisSubscriber.class.getDeclaredMethod("handleMessage", String.class);
        handleMessage.setAccessible(true);

        handleMessage.invoke(subscriber, "{"
                + "\"action\":\"PUBLISH\","
                + "\"ruleCode\":\"ROOT\","
                + "\"version\":1,"
                + "\"outputScriptNames\":[\"decision\",\"notAssigned\"]"
                + "}");

        assertEquals(Arrays.asList("decision", "notAssigned"),
                cache.get("ROOT").getOutputScriptNames());
    }

    @Test
    public void withoutProjectCodeSubscribesOnlyToGlobalBroadcastChannel() throws Exception {
        RedisSubscriber subscriber = new RedisSubscriber(new L1MemoryCache(10), null, null);

        assertNull(getField(subscriber, "channel"));
        List<?> topics = (List<?>) getField(subscriber, "topics");
        assertEquals(1, topics.size());
        assertEquals("rule:push:broadcast", ((Topic) topics.get(0)).getTopic());
    }

    @Test
    public void ignoresProjectFunctionUpdatesForAnotherProject() throws Exception {
        QLExpressEngine engine = new QLExpressEngine();
        RedisSubscriber subscriber = new RedisSubscriber(new L1MemoryCache(10), null, "project-a");
        subscriber.setFunctionRegistrar(new ClientFunctionRegistrar(engine, null));

        handle(subscriber, "{\"action\":\"FUNC_UPDATE\",\"scope\":\"PROJECT\","
                + "\"projectCode\":\"project-b\",\"funcCode\":\"foreignFn\","
                + "\"funcImplType\":\"SCRIPT\",\"funcImplScript\":\"7\"}");

        assertNull(engine.getRunner().getFunction("foreignFn"));
    }

    @Test
    public void deletesMatchingProjectFunctionThenAllowsScopedReaddition() throws Exception {
        QLExpressEngine engine = new QLExpressEngine();
        RedisSubscriber subscriber = new RedisSubscriber(new L1MemoryCache(10), null, "project-a");
        ClientFunctionRegistrar registrar = new ClientFunctionRegistrar(engine, null, "project-a");
        subscriber.setFunctionRegistrar(registrar);
        handle(subscriber, "{\"action\":\"FUNC_UPDATE\",\"scope\":\"PROJECT\","
                + "\"projectCode\":\"project-a\",\"funcCode\":\"localFn\","
                + "\"funcImplType\":\"SCRIPT\",\"funcImplScript\":\"7\"}");

        handle(subscriber, "{\"action\":\"FUNC_DELETE\",\"scope\":\"PROJECT\","
                + "\"projectCode\":\"project-a\",\"funcCode\":\"localFn\"}");
        assertFalse(registrar.hasRemoteFunction("PROJECT", "project-a", "localFn"));
        handle(subscriber, "{\"action\":\"FUNC_UPDATE\",\"scope\":\"PROJECT\","
                + "\"projectCode\":\"project-a\",\"funcCode\":\"localFn\","
                + "\"funcImplType\":\"SCRIPT\",\"funcImplScript\":\"8\"}");

        assertEquals(8, ((Number) engine.execute("localFn()", java.util.Collections.emptyMap(), false)
                .getResult()).intValue());
    }

    @Test
    public void ignoresDuplicateOperationIdForFunctionPush() throws Exception {
        QLExpressEngine engine = new QLExpressEngine();
        RedisSubscriber subscriber = new RedisSubscriber(new L1MemoryCache(10), null, "project-a");
        subscriber.setFunctionRegistrar(new ClientFunctionRegistrar(engine, null, "project-a"));
        String first = "{\"operationId\":\"op-1\",\"action\":\"FUNC_UPDATE\",\"scope\":\"PROJECT\","
                + "\"projectCode\":\"project-a\",\"funcCode\":\"dedupFn\",\"funcImplType\":\"SCRIPT\",\"funcImplScript\":\"7\"}";
        String duplicate = first.replace("\"funcImplScript\":\"7\"", "\"funcImplScript\":\"8\"");
        handle(subscriber, first);
        handle(subscriber, duplicate);

        assertEquals(7, ((Number) engine.execute("dedupFn()", java.util.Collections.emptyMap(), false)
                .getResult()).intValue());
    }

    @Test
    public void failedFetchCanBeRetriedWithSameOperation() throws Exception {
        AtomicInteger fetches = new AtomicInteger();
        L1MemoryCache cache = new L1MemoryCache(10);
        RedisSubscriber subscriber = new RedisSubscriber(cache, null, "project-a", code -> {
            if (fetches.incrementAndGet() == 1) throw new IllegalStateException("HTTP unavailable");
            return rule(code);
        });
        String message = publish("op-retry");
        handle(subscriber, message);
        assertNull(cache.get("ROOT"));
        handle(subscriber, message);
        handle(subscriber, message);
        assertEquals(2, fetches.get());
        assertEquals("return 7;", cache.get("ROOT").getCompiledScript());
    }

    @Test
    public void unavailableRuleDoesNotConsumeOperation() throws Exception {
        AtomicInteger fetches = new AtomicInteger();
        L1MemoryCache cache = new L1MemoryCache(10);
        RedisSubscriber subscriber = new RedisSubscriber(cache, null, "project-a", code ->
                fetches.incrementAndGet() == 1 ? null : rule(code));
        handle(subscriber, publish("op-unavailable"));
        handle(subscriber, publish("op-unavailable"));
        assertEquals(2, fetches.get());
        assertEquals("ROOT", cache.get("ROOT").getRuleCode());
    }

    @Test
    public void failedPreparationCanBeRetriedWithoutMarkingSuccess() throws Exception {
        AtomicInteger prepares = new AtomicInteger();
        L1MemoryCache cache = new L1MemoryCache(10, rule -> {
            if (prepares.incrementAndGet() == 1) throw new IllegalStateException("prepare failed");
        });
        RedisSubscriber subscriber = new RedisSubscriber(cache, null, "project-a", RedisSubscriberTest::rule);
        handle(subscriber, publish("op-prepare"));
        handle(subscriber, publish("op-prepare"));
        assertEquals(2, prepares.get());
        assertEquals("ROOT", cache.get("ROOT").getRuleCode());
    }

    @Test
    public void functionRegistrationFailureCanRetryAfterBeanBecomesAvailable() throws Exception {
        try (StaticApplicationContext context = new StaticApplicationContext()) {
            QLExpressEngine engine = new QLExpressEngine();
            RedisSubscriber subscriber = new RedisSubscriber(new L1MemoryCache(10), null, "project-a");
            ClientFunctionRegistrar registrar = new ClientFunctionRegistrar(engine, context, "project-a");
            subscriber.setFunctionRegistrar(registrar);
            String message = "{\"operationId\":\"op-bean\",\"action\":\"FUNC_UPDATE\",\"scope\":\"PROJECT\","
                    + "\"projectCode\":\"project-a\",\"funcCode\":\"lateFn\",\"funcImplType\":\"BEAN\","
                    + "\"funcImplBeanName\":\"lateBean\",\"funcImplMethod\":\"value\"}";
            handle(subscriber, message);
            assertFalse(registrar.hasRemoteFunction("PROJECT", "project-a", "lateFn"));
            context.getBeanFactory().registerSingleton("lateBean", new FunctionBean());
            handle(subscriber, message);
            assertTrue(registrar.hasRemoteFunction("PROJECT", "project-a", "lateFn"));
            assertEquals(9, ((Number) engine.execute("lateFn()", java.util.Collections.emptyMap(), false)
                    .getResult()).intValue());
        }
    }

    @Test
    public void missingFunctionRegistrarDoesNotConsumeOperation() throws Exception {
        QLExpressEngine engine = new QLExpressEngine();
        RedisSubscriber subscriber = new RedisSubscriber(new L1MemoryCache(10), null, "project-a");
        String message = "{\"operationId\":\"op-registrar\",\"action\":\"FUNC_UPDATE\",\"scope\":\"PROJECT\","
                + "\"projectCode\":\"project-a\",\"funcCode\":\"laterFn\",\"funcImplType\":\"SCRIPT\",\"funcImplScript\":\"7\"}";
        handle(subscriber, message);
        ClientFunctionRegistrar registrar = new ClientFunctionRegistrar(engine, null, "project-a");
        subscriber.setFunctionRegistrar(registrar);
        handle(subscriber, message);
        assertTrue(registrar.hasRemoteFunction("PROJECT", "project-a", "laterFn"));
    }

    @Test
    public void capacityEvictsOnlyOldestSuccessfulOperation() throws Exception {
        AtomicInteger removals = new AtomicInteger();
        L1MemoryCache cache = new L1MemoryCache(10) {
            @Override public void remove(String code) { removals.incrementAndGet(); }
        };
        RedisSubscriber subscriber = new RedisSubscriber(cache, null, "project-a");
        for (int i = 0; i < 8193; i++) handle(subscriber, removal(i));
        handle(subscriber, removal(8192));
        assertEquals("recent operation must survive capacity eviction", 8193, removals.get());
        handle(subscriber, removal(8191));
        assertEquals("only the oldest operation may be evicted", 8193, removals.get());
        handle(subscriber, removal(0));
        assertEquals("oldest operation may be applied again after eviction", 8194, removals.get());
    }

    @Test
    public void concurrentDuplicateCanRetryWhenFirstHandlerFails() throws Exception {
        AtomicInteger fetches = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        L1MemoryCache cache = new L1MemoryCache(10);
        RedisSubscriber subscriber = new RedisSubscriber(cache, null, "project-a", code -> {
            if (fetches.incrementAndGet() == 1) {
                entered.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                throw new IllegalStateException("temporary failure");
            }
            return rule(code);
        });
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> { handle(subscriber, publish("op-concurrent")); return null; });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var second = executor.submit(() -> {
                secondStarted.countDown();
                handle(subscriber, publish("op-concurrent")); return null;
            });
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertEquals(2, fetches.get());
            assertEquals("ROOT", cache.get("ROOT").getRuleCode());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void sameOperationNeverRunsConcurrentlyWhenAWaitingDeliveryExists() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch firstRelease = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);
        CountDownLatch secondRelease = new CountDownLatch(1);
        L1MemoryCache cache = new L1MemoryCache(10);
        RedisSubscriber subscriber = new RedisSubscriber(cache, null, "project-a", code -> {
            int attempt = attempts.incrementAndGet();
            int current = active.incrementAndGet();
            maxActive.accumulateAndGet(current, Math::max);
            try {
                if (attempt == 1) {
                    firstEntered.countDown();
                    await(firstRelease);
                    throw new IllegalStateException("temporary failure");
                }
                if (attempt == 2) {
                    secondEntered.countDown();
                    await(secondRelease);
                }
                return rule(code);
            } finally {
                active.decrementAndGet();
            }
        });
        ExecutorService executor = Executors.newFixedThreadPool(16);
        try {
            Future<?> first = executor.submit(() -> handleUnchecked(subscriber, publish("op-lock-race")));
            assertTrue(firstEntered.await(5, TimeUnit.SECONDS));
            Object initialOperationLock = operationLock(subscriber, "op-lock-race");
            Future<?> second = executor.submit(() -> handleUnchecked(subscriber, publish("op-lock-race")));
            awaitOperationLockReferences(subscriber, "op-lock-race", 2);
            firstRelease.countDown();
            assertTrue(secondEntered.await(5, TimeUnit.SECONDS));
            Thread.sleep(100);
            assertSame("a waiting delivery must keep the operation lock registered",
                    initialOperationLock, operationLock(subscriber, "op-lock-race"));

            List<Future<?>> waitingDeliveries = new java.util.ArrayList<>();
            for (int i = 0; i < 12; i++) {
                waitingDeliveries.add(executor.submit(() -> handleUnchecked(subscriber, publish("op-lock-race"))));
            }
            Thread.sleep(100);
            assertEquals("same operation must have one active handler", 1, maxActive.get());
            secondRelease.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            for (Future<?> delivery : waitingDeliveries) delivery.get(5, TimeUnit.SECONDS);
            assertEquals(2, attempts.get());
        } finally {
            firstRelease.countDown();
            secondRelease.countDown();
            executor.shutdownNow();
        }
    }

    public static class FunctionBean { public int value() { return 9; } }

    private static CachedRule rule(String code) {
        CachedRule rule = new CachedRule();
        rule.setRuleCode(code);
        rule.setCompiledScript("return 7;");
        return rule;
    }

    private static String publish(String operationId) {
        return "{\"operationId\":\"" + operationId + "\",\"action\":\"PUBLISH\",\"projectCode\":\"project-a\",\"ruleCode\":\"ROOT\"}";
    }

    private static String removal(int id) {
        return "{\"operationId\":\"remove-" + id + "\",\"action\":\"DELETE\",\"projectCode\":\"project-a\",\"ruleCode\":\"ROOT\"}";
    }

    private Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private void handle(RedisSubscriber subscriber, String message) throws Exception {
        Method handleMessage = RedisSubscriber.class.getDeclaredMethod("handleMessage", String.class);
        handleMessage.setAccessible(true);
        handleMessage.invoke(subscriber, message);
    }

    private static void handleUnchecked(RedisSubscriber subscriber, String message) {
        try {
            Method handleMessage = RedisSubscriber.class.getDeclaredMethod("handleMessage", String.class);
            handleMessage.setAccessible(true);
            handleMessage.invoke(subscriber, message);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object operationLock(RedisSubscriber subscriber, String operationId) {
        try {
            Field field = RedisSubscriber.class.getDeclaredField("operationLocks");
            field.setAccessible(true);
            @SuppressWarnings("rawtypes")
            java.util.Map<String, ?> locks = (java.util.Map<String, ?>) field.get(subscriber);
            return locks.get(operationId);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void awaitOperationLockReferences(RedisSubscriber subscriber, String operationId,
                                                      int expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            try {
                Object lock = operationLock(subscriber, operationId);
                Field references = lock.getClass().getDeclaredField("references");
                references.setAccessible(true);
                AtomicInteger count = (AtomicInteger) references.get(lock);
                if (count.get() >= expected) return;
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
            Thread.yield();
        }
        throw new AssertionError("operation lock did not register waiting delivery");
    }
}
