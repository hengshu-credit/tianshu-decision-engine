package com.hengshucredit.rule.core.pmml;

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
    public void keepsConcurrentCacheWithinConfiguredCapacity() throws Exception {
        PMMLModelExecutor executor = new PMMLModelExecutor(1);
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            final int index = i;
            futures.add(pool.submit(() -> {
                start.await();
                Map<String, Object> result = executor.evaluateWithKey(
                        "model-" + index,
                        base64(pmml(index)),
                        Collections.singletonMap("exact_input_name", 3d));
                assertEquals(index + 6d, prediction(result), 0d);
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

        Field cacheField = PMMLModelExecutor.class.getDeclaredField("modelCache");
        cacheField.setAccessible(true);
        Map<?, ?> cache = (Map<?, ?>) cacheField.get(executor);
        assertTrue("并发加载后缓存不能超过配置容量", cache.size() <= 1);
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
