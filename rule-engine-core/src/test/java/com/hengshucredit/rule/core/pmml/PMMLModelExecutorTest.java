package com.hengshucredit.rule.core.pmml;

import com.hengshucredit.rule.core.engine.ExecutionCapacityException;

import org.junit.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PMMLModelExecutorTest {

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonPositiveCacheCapacity() {
        new PMMLModelExecutor(0);
    }

    @Test
    public void evaluatesPmmlWithExactFieldName() {
        PMMLModelExecutor executor = new PMMLModelExecutor();

        Map<String, Object> result = executor.evaluate(base64(pmml(1d)),
                Collections.singletonMap("exact_input_name", 3d));

        assertEquals(7d, prediction(result), 0d);
    }

    @Test
    public void doesNotTranslateCamelCaseInputAlias() {
        PMMLModelExecutor executor = new PMMLModelExecutor();

        try {
            executor.evaluate(base64(pmml(1d)),
                    Collections.singletonMap("exactInputName", 3d));
            throw new AssertionError("PMML 字段名不匹配时必须拒绝执行");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("must match exactly"));
        }
    }

    @Test
    public void rejectsUnexpectedInputFields() {
        PMMLModelExecutor executor = new PMMLModelExecutor();
        Map<String, Object> input = new java.util.LinkedHashMap<>();
        input.put("exact_input_name", 3d);
        input.put("unused", 1d);

        try {
            executor.evaluate(base64(pmml(1d)), input);
            throw new AssertionError("PMML 未声明字段必须被拒绝");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("unexpected [unused]"));
        }
    }

    @Test
    public void reloadsSameModelKeyWhenContentChanges() {
        PMMLModelExecutor executor = new PMMLModelExecutor();
        Map<String, Object> input = Collections.singletonMap("exact_input_name", 3d);

        assertEquals(7d, prediction(executor.evaluateWithKey(
                "model-id", base64(pmml(1d)), input)), 0d);
        assertEquals(16d, prediction(executor.evaluateWithKey(
                "model-id", base64(pmml(10d)), input)), 0d);
    }

    @Test(timeout = 30000)
    public void coldModelLoadingDoesNotBlockAnUnrelatedCachedModel() throws Exception {
        CountDownLatch coldLoading = new CountDownLatch(1);
        CountDownLatch releaseCold = new CountDownLatch(1);
        byte[] coldContent = pmml(10d).getBytes(StandardCharsets.UTF_8);
        PMMLModelExecutor executor = new PMMLModelExecutor(1) {
            @Override
            ModelContext loadModelContext(byte[] contentBytes) {
                if (java.util.Arrays.equals(coldContent, contentBytes)) {
                    coldLoading.countDown();
                    awaitRelease(releaseCold);
                }
                return super.loadModelContext(contentBytes);
            }
        };
        Map<String, Object> input = Collections.singletonMap("exact_input_name", 3d);
        assertEquals(7d, prediction(executor.evaluateWithKey("hot", base64(pmml(1d)), input)), 0d);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Map<String, Object>> cold = pool.submit(() -> executor.evaluateWithKey(
                    "cold", base64(pmml(10d)), input));
            assertTrue("冷模型应进入受控加载阶段", coldLoading.await(10, TimeUnit.SECONDS));
            Future<Map<String, Object>> hot = pool.submit(() -> executor.evaluateWithKey(
                    "hot", base64(pmml(1d)), input));
            // 冷模型的闩锁尚未释放；热命中必须独立完成，不比较机器性能耗时。
            assertEquals(7d, prediction(hot.get(10, TimeUnit.SECONDS)), 0d);
            assertFalse(cold.isDone());
            releaseCold.countDown();
            assertEquals(16d, prediction(cold.get(10, TimeUnit.SECONDS)), 0d);
        } finally {
            releaseCold.countDown();
            pool.shutdownNow();
        }
    }

    @Test(timeout = 30000)
    public void concurrentFirstRequestsShareOneModelLoad() throws Exception {
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch callersStarted = new CountDownLatch(8);
        AtomicInteger loads = new AtomicInteger();
        PMMLModelExecutor executor = new PMMLModelExecutor(1) {
            @Override
            ModelContext loadModelContext(byte[] contentBytes) {
                loads.incrementAndGet();
                loading.countDown();
                awaitRelease(release);
                return super.loadModelContext(contentBytes);
            }
        };
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<Map<String, Object>>> results = new ArrayList<>();
        try {
            for (int index = 0; index < 8; index++) {
                results.add(pool.submit(() -> {
                    callersStarted.countDown();
                    return executor.evaluateWithKey("same-model", base64(pmml(1d)),
                            Collections.singletonMap("exact_input_name", 3d));
                }));
            }
            assertTrue(loading.await(10, TimeUnit.SECONDS));
            assertTrue(callersStarted.await(10, TimeUnit.SECONDS));
            release.countDown();
            for (Future<Map<String, Object>> result : results) {
                assertEquals(7d, prediction(result.get(10, TimeUnit.SECONDS)), 0d);
            }
            assertEquals("相同模型的首次并发请求只能构建一个 Evaluator", 1, loads.get());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    public void failedLoadDoesNotPoisonSubsequentRequests() {
        AtomicInteger loads = new AtomicInteger();
        PMMLModelExecutor executor = new PMMLModelExecutor(1) {
            @Override
            ModelContext loadModelContext(byte[] contentBytes) {
                if (loads.incrementAndGet() == 1) {
                    throw new IllegalStateException("controlled load failure");
                }
                return super.loadModelContext(contentBytes);
            }
        };
        Map<String, Object> input = Collections.singletonMap("exact_input_name", 3d);
        try {
            executor.evaluateWithKey("same-model", base64(pmml(1d)), input);
            fail("首次加载应返回真实失败");
        } catch (IllegalStateException expected) {
            assertEquals("controlled load failure", expected.getMessage());
        }
        assertEquals(7d, prediction(executor.evaluateWithKey("same-model", base64(pmml(1d)), input)), 0d);
        assertEquals(2, loads.get());
    }

    @Test(timeout = 30000)
    public void clearCacheDetachesEarlierLoadsWithoutReplacingTheNewGeneration() throws Exception {
        CountDownLatch oldLoading = new CountDownLatch(1);
        CountDownLatch releaseOld = new CountDownLatch(1);
        AtomicInteger loads = new AtomicInteger();
        AtomicReference<PMMLModelExecutor.ModelContext> newContext = new AtomicReference<>();
        PMMLModelExecutor executor = new PMMLModelExecutor(2) {
            @Override
            ModelContext loadModelContext(byte[] contentBytes) {
                int load = loads.incrementAndGet();
                if (load == 1) {
                    oldLoading.countDown();
                    awaitRelease(releaseOld);
                }
                ModelContext context = super.loadModelContext(contentBytes);
                if (load == 2) newContext.set(context);
                return context;
            }
        };
        Map<String, Object> input = Collections.singletonMap("exact_input_name", 3d);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Map<String, Object>> old = pool.submit(() -> executor.evaluateWithKey(
                    "same-model", base64(pmml(1d)), input));
            assertTrue(oldLoading.await(10, TimeUnit.SECONDS));
            pool.submit(executor::clearCache).get(10, TimeUnit.SECONDS);
            assertEquals(7d, prediction(executor.evaluateWithKey("same-model", base64(pmml(1d)), input)), 0d);
            assertEquals(2, loads.get());
            releaseOld.countDown();
            assertEquals(7d, prediction(old.get(10, TimeUnit.SECONDS)), 0d);
            assertSame("清理之前的加载不能覆盖新一代缓存", newContext.get(), cachedModels(executor).values().iterator().next());
            executor.evaluateWithKey("same-model", base64(pmml(1d)), input);
            assertEquals(2, loads.get());
        } finally {
            releaseOld.countDown();
            pool.shutdownNow();
        }
    }

    @Test(timeout = 30000)
    public void interruptedWaiterLeavesSharedLoadAvailableForOtherRequests() throws Exception {
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch waiterStarted = new CountDownLatch(1);
        AtomicInteger loads = new AtomicInteger();
        AtomicReference<Thread> waiterThread = new AtomicReference<>();
        AtomicBoolean interruptPreserved = new AtomicBoolean();
        PMMLModelExecutor executor = new PMMLModelExecutor() {
            @Override
            ModelContext loadModelContext(byte[] contentBytes) {
                loads.incrementAndGet();
                loading.countDown();
                awaitRelease(release);
                return super.loadModelContext(contentBytes);
            }
        };
        Map<String, Object> input = Collections.singletonMap("exact_input_name", 3d);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Map<String, Object>> owner = pool.submit(() -> executor.evaluateWithKey(
                    "same-model", base64(pmml(1d)), input));
            assertTrue(loading.await(10, TimeUnit.SECONDS));
            Future<?> waiter = pool.submit(() -> {
                waiterThread.set(Thread.currentThread());
                waiterStarted.countDown();
                try {
                    executor.evaluateWithKey("same-model", base64(pmml(1d)), input);
                    fail("等待中的调用被中断后应及时退出");
                } catch (RuntimeException expected) {
                    interruptPreserved.set(Thread.currentThread().isInterrupted());
                    assertTrue(expected.getCause() instanceof InterruptedException);
                }
            });
            assertTrue(waiterStarted.await(10, TimeUnit.SECONDS));
            waiterThread.get().interrupt();
            waiter.get(10, TimeUnit.SECONDS);
            assertTrue("等待方必须恢复中断标志", interruptPreserved.get());
            assertFalse("等待方退出不能取消仍在执行的共享加载", owner.isDone());
            release.countDown();
            assertEquals(7d, prediction(owner.get(10, TimeUnit.SECONDS)), 0d);
            assertEquals(7d, prediction(executor.evaluateWithKey("same-model", base64(pmml(1d)), input)), 0d);
            assertEquals(1, loads.get());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test(timeout = 30000)
    public void keepsConcurrentCacheWithinConfiguredCapacity() throws Exception {
        PMMLModelExecutor executor = new PMMLModelExecutor(1);
        AtomicInteger completed = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            final int index = i;
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    Map<String, Object> result = executor.evaluateWithKey(
                            "model-" + index,
                            base64(pmml(index)),
                            Collections.singletonMap("exact_input_name", 3d));
                    assertEquals(index + 6d, prediction(result), 0d);
                    completed.incrementAndGet();
                } catch (RejectedExecutionException expected) {
                    assertTrue(expected.getMessage().contains("PMML 模型加载并发已达上限"));
                    rejected.incrementAndGet();
                }
                return null;
            }));
        }

        start.countDown();
        try {
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }

        Map<?, ?> cache = cachedModels(executor);
        assertTrue("并发加载后缓存不能超过配置容量", cache.size() <= 1);
        assertTrue("容量内必须有模型完成加载", completed.get() > 0);
        assertEquals("所有请求只能成功或明确容量拒绝", 64, completed.get() + rejected.get());
    }

    @Test(timeout = 30000)
    public void rejectsExcessColdLoadsAndClearDoesNotBypassTheLoadingLimit() throws Exception {
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger loads = new AtomicInteger();
        PMMLModelExecutor executor = new PMMLModelExecutor(1) {
            @Override
            ModelContext loadModelContext(byte[] contentBytes) {
                if (loads.incrementAndGet() == 1) {
                    loading.countDown();
                    awaitRelease(release);
                }
                return super.loadModelContext(contentBytes);
            }
        };
        Map<String, Object> input = Collections.singletonMap("exact_input_name", 3d);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Map<String, Object>> owner = pool.submit(() -> executor.evaluateWithKey(
                    "first-model", base64(pmml(1d)), input));
            assertTrue(loading.await(10, TimeUnit.SECONDS));
            Future<?> rejected = pool.submit(() -> {
                assertLoadingRejected(executor, "second-model", input);
                executor.clearCache();
                assertLoadingRejected(executor, "first-model", input);
            });
            rejected.get(10, TimeUnit.SECONDS);
            assertEquals("满载和清缓存后的新请求不能启动额外构建", 1, loads.get());
            release.countDown();
            assertEquals(7d, prediction(owner.get(10, TimeUnit.SECONDS)), 0d);
            assertEquals(16d, prediction(executor.evaluateWithKey("second-model", base64(pmml(10d)), input)), 0d);
            assertEquals("旧加载结束后应释放并发名额", 2, loads.get());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private static void assertLoadingRejected(PMMLModelExecutor executor, String key, Map<String, Object> input) {
        try {
            executor.evaluateWithKey(key, base64(pmml(1d)), input);
            fail("加载并发已满时应明确拒绝新模型，不能排队占用更多内存");
        } catch (RejectedExecutionException expected) {
            assertTrue(expected.getMessage().contains("PMML 模型加载并发已达上限"));
            assertTrue(expected instanceof ExecutionCapacityException);
            assertEquals(ExecutionCapacityException.Resource.PMML_MODEL_LOADING,
                    ((ExecutionCapacityException) expected).getResource());
        }
    }

    private static Map<?, ?> cachedModels(PMMLModelExecutor executor) throws Exception {
        Field cacheField = PMMLModelExecutor.class.getDeclaredField("modelCache");
        cacheField.setAccessible(true);
        return (Map<?, ?>) cacheField.get(executor);
    }

    private static void awaitRelease(CountDownLatch release) {
        try {
            if (!release.await(20, TimeUnit.SECONDS)) {
                throw new AssertionError("测试未释放模型加载闩锁");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(interrupted);
        }
    }

    private static double prediction(Map<String, Object> result) {
        return ((Number) result.get("prediction")).doubleValue();
    }

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String pmml(double intercept) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<PMML xmlns=\"http://www.dmg.org/PMML-4_4\" version=\"4.4\">\n"
                + "  <Header/>\n"
                + "  <DataDictionary numberOfFields=\"2\">\n"
                + "    <DataField name=\"exact_input_name\" optype=\"continuous\" dataType=\"double\"/>\n"
                + "    <DataField name=\"prediction\" optype=\"continuous\" dataType=\"double\"/>\n"
                + "  </DataDictionary>\n"
                + "  <RegressionModel modelName=\"linear\" functionName=\"regression\">\n"
                + "    <MiningSchema>\n"
                + "      <MiningField name=\"exact_input_name\"/>\n"
                + "      <MiningField name=\"prediction\" usageType=\"target\"/>\n"
                + "    </MiningSchema>\n"
                + "    <RegressionTable intercept=\"" + intercept + "\">\n"
                + "      <NumericPredictor name=\"exact_input_name\" coefficient=\"2.0\"/>\n"
                + "    </RegressionTable>\n"
                + "  </RegressionModel>\n"
                + "</PMML>\n";
    }
}
