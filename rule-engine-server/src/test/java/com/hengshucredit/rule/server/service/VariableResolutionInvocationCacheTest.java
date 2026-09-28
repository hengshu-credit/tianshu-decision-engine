package com.hengshucredit.rule.server.service;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class VariableResolutionInvocationCacheTest {

    @Test
    public void concurrentCallersForSameKeyShareOneInvocationAndReceiveCopies() throws Exception {
        VariableResolutionInvocationCache cache = new VariableResolutionInvocationCache();
        CountDownLatch supplierEntered = new CountDownLatch(1);
        CountDownLatch releaseSupplier = new CountDownLatch(1);
        AtomicInteger invocations = new AtomicInteger();

        CompletableFuture<Map<String, Object>> first = CompletableFuture.supplyAsync(() ->
                cache.resolve("7:{requestId=R1}", () -> {
                    invocations.incrementAndGet();
                    supplierEntered.countDown();
                    await(releaseSupplier);
                    return singletonMap("score", 88);
                }));
        assertTrue(supplierEntered.await(2, TimeUnit.SECONDS));
        CompletableFuture<Map<String, Object>> second = CompletableFuture.supplyAsync(() ->
                cache.resolve("7:{requestId=R1}", () -> {
                    invocations.incrementAndGet();
                    return singletonMap("score", 99);
                }));

        releaseSupplier.countDown();
        Map<String, Object> firstResult = first.join();
        Map<String, Object> secondResult = second.join();

        assertEquals(1, invocations.get());
        assertEquals(88, firstResult.get("score"));
        assertEquals(88, secondResult.get("score"));
        assertNotSame(firstResult, secondResult);
        firstResult.put("score", 0);
        assertEquals(88, cache.resolve("7:{requestId=R1}", LinkedHashMap::new).get("score"));
    }

    @Test
    public void differentKeysCanInvokeConcurrently() throws Exception {
        VariableResolutionInvocationCache cache = new VariableResolutionInvocationCache();
        CountDownLatch bothEntered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<Map<String, Object>> first = CompletableFuture.supplyAsync(() ->
                cache.resolve("first", () -> blockingResponse(bothEntered, release, 1)));
        CompletableFuture<Map<String, Object>> second = CompletableFuture.supplyAsync(() ->
                cache.resolve("second", () -> blockingResponse(bothEntered, release, 2)));

        assertTrue(bothEntered.await(2, TimeUnit.SECONDS));
        release.countDown();

        assertEquals(1, first.join().get("value"));
        assertEquals(2, second.join().get("value"));
    }

    @Test
    public void historySnapshotExtractsApiIdFromParameterizedInvocationKey() {
        VariableResolutionInvocationCache cache = new VariableResolutionInvocationCache();
        cache.resolve("API:7:PARAMS:abc123", () -> singletonMap("score", 88));

        Map<String, Object> snapshot = cache.historySnapshot(
                List.of(), Map.of(), Map.of());

        assertEquals(List.of(7L), snapshot.get("apiIds"));
    }

    @Test
    public void completedSourceStepRoundTripsAndChecksAllDigests() {
        VariableResolutionInvocationCache cache = new VariableResolutionInvocationCache();
        AtomicInteger persisted = new AtomicInteger();
        cache.setSourceStepListener(step -> persisted.incrementAndGet());
        VariableResolutionInvocationCache.SourceStep step =
                new VariableResolutionInvocationCache.SourceStep(
                        "VARIABLE:7", "VARIABLE", "cfg-1", "input-1", "deps-1", "SUCCESS",
                        Map.of("score", 88), null,
                        Map.of("VARIABLE:7", Map.of("OUTCOME", "SUCCESS")),
                        Map.of("scriptName", "score"));
        cache.completeStep(step);
        assertEquals(1, persisted.get());
        assertTrue(cache.hasReusableStep("VARIABLE:7", "cfg-1", "input-1", "deps-1"));
        assertFalse(cache.hasReusableStep("VARIABLE:7", "cfg-2", "input-1", "deps-1"));

        VariableResolutionInvocationCache restored = new VariableResolutionInvocationCache();
        restored.restoreCompletedSteps(cache.snapshotCompletedSteps());
        assertTrue(restored.hasReusableStep("VARIABLE:7", "cfg-1", "input-1", "deps-1"));
        assertEquals("score", restored.completedStep("VARIABLE:7").getMetadata().get("scriptName"));
        assertEquals("SUCCESS", restored.completedStep("VARIABLE:7")
                .getSourceStates().get("VARIABLE:7").get("OUTCOME"));
    }

    @Test
    public void invalidatingModelStepAlsoRemovesItsResponse() {
        VariableResolutionInvocationCache cache = new VariableResolutionInvocationCache();
        cache.restoreResponse("MODEL:9", singletonMap("outputs", singletonMap("score", 1)));
        cache.associateResponse("MODEL:9", "MODEL:9");
        cache.completeStep(new VariableResolutionInvocationCache.SourceStep(
                "MODEL:9", "MODEL", "cfg", "input", "deps", "SUCCESS",
                singletonMap("score", 1), singletonMap("outputs", singletonMap("score", 1)),
                Map.of(), Map.of("modelCode", "riskModel")));
        cache.invalidateStep("MODEL:9");
        assertFalse(cache.hasResponse("MODEL:9"));
        assertEquals(null, cache.completedStep("MODEL:9"));
    }

    private static Map<String, Object> blockingResponse(
            CountDownLatch entered, CountDownLatch release, int value) {
        entered.countDown();
        await(release);
        return singletonMap("value", value);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("latch timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static Map<String, Object> singletonMap(String key, Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(key, value);
        return result;
    }
}
