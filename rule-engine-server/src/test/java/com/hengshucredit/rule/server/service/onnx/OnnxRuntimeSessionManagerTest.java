package com.hengshucredit.rule.server.service.onnx;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.core.engine.ExecutionCapacityException;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class OnnxRuntimeSessionManagerTest {

    @Test
    public void slowColdLoadDoesNotBlockUnrelatedHotSessionLease() throws Exception {
        BlockingSessionManager manager = new BlockingSessionManager(2);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            manager.acquireSession(new byte[]{1}, OnnxRuntimeConfig.cpu()).close();
            Future<?> cold = callers.submit(() -> manager.acquireSession(new byte[]{9}, OnnxRuntimeConfig.cpu()).close());
            assertTrue(manager.entered.await(1, TimeUnit.SECONDS));
            CountDownLatch hotFinished = new CountDownLatch(1);
            Future<?> hot = callers.submit(() -> {
                manager.acquireSession(new byte[]{1}, OnnxRuntimeConfig.cpu()).close();
                hotFinished.countDown();
            });
            assertTrue("无关热模型不能等待慢模型完成 native 创建", hotFinished.await(1, TimeUnit.SECONDS));
            hot.get(1, TimeUnit.SECONDS);
            manager.release.countDown();
            cold.get(1, TimeUnit.SECONDS);
            assertEquals(2, manager.created.get());
        } finally {
            manager.release.countDown();
            callers.shutdownNow();
            callers.awaitTermination(2, TimeUnit.SECONDS);
            manager.close(false);
        }
    }

    @Test
    public void concurrentSameKeySharesOneNativeCreation() throws Exception {
        BlockingSessionManager manager = new BlockingSessionManager(1);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = callers.submit(() -> manager.acquireSession(new byte[]{9}, OnnxRuntimeConfig.cpu()).close());
            assertTrue(manager.entered.await(1, TimeUnit.SECONDS));
            Future<?> second = callers.submit(() -> manager.acquireSession(new byte[]{9}, OnnxRuntimeConfig.cpu()).close());
            manager.release.countDown();
            first.get(1, TimeUnit.SECONDS);
            second.get(1, TimeUnit.SECONDS);
            assertEquals(1, manager.created.get());
            assertEquals(1, manager.getCachedSessionCount());
        } finally {
            manager.release.countDown();
            callers.shutdownNow();
            callers.awaitTermination(2, TimeUnit.SECONDS);
            manager.close(false);
        }
        assertEquals(1, manager.closeCount.get());
    }

    @Test
    public void closeDoesNotWaitForCreationAndClosesLateSessionWithoutRefilling() throws Exception {
        BlockingSessionManager manager = new BlockingSessionManager(1);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Future<?> loading = callers.submit(() -> manager.acquireSession(new byte[]{9}, OnnxRuntimeConfig.cpu()).close());
            assertTrue(manager.entered.await(1, TimeUnit.SECONDS));
            CountDownLatch closed = new CountDownLatch(1);
            Future<?> closing = callers.submit(() -> {
                manager.close(false);
                closed.countDown();
            });
            assertTrue("关闭管理器不能持锁等待 native 创建", closed.await(1, TimeUnit.SECONDS));
            closing.get(1, TimeUnit.SECONDS);
            manager.release.countDown();
            ExecutionException failure = assertThrows(ExecutionException.class, () -> loading.get(1, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof IllegalStateException);
            assertEquals(1, manager.closeCount.get());
            assertEquals(0, manager.getCachedSessionCount());
        } finally {
            manager.release.countDown();
            callers.shutdownNow();
            callers.awaitTermination(2, TimeUnit.SECONDS);
            manager.close(false);
        }
    }

    @Test
    public void nativeCreationReservationRejectsAnotherKeyWithoutWaiting() throws Exception {
        BlockingSessionManager manager = new BlockingSessionManager(1);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Future<?> loading = callers.submit(() -> manager.acquireSession(new byte[]{9}, OnnxRuntimeConfig.cpu()).close());
            assertTrue(manager.entered.await(1, TimeUnit.SECONDS));
            CountDownLatch rejected = new CountDownLatch(1);
            Future<?> competing = callers.submit(() -> {
                assertThrows(OnnxRuntimeSessionManager.SessionCapacityException.class,
                        () -> manager.acquireSession(new byte[]{2}, OnnxRuntimeConfig.cpu()));
                rejected.countDown();
            });
            assertTrue("创建中的会话也必须占容量并快速拒绝另一 key", rejected.await(1, TimeUnit.SECONDS));
            competing.get(1, TimeUnit.SECONDS);
            manager.release.countDown();
            loading.get(1, TimeUnit.SECONDS);
            assertEquals(1, manager.created.get());
        } finally {
            manager.release.countDown();
            callers.shutdownNow();
            callers.awaitTermination(2, TimeUnit.SECONDS);
            manager.close(false);
        }
    }

    @Test
    public void interruptedCudaWaiterExitsWithoutCpuFallbackOrCancellingSharedLoad() throws Exception {
        BlockingSessionManager manager = new BlockingSessionManager(1);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        OnnxRuntimeConfig cuda = OnnxRuntimeConfig.from(JSON.parseObject("{\"executionProvider\":\"CUDA\"}"));
        CountDownLatch waiterEntered = new CountDownLatch(1);
        CountDownLatch waiterExited = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Boolean> interruptPreserved = new AtomicReference<>();
        AtomicInteger cpuAttempts = new AtomicInteger();
        Thread waiter = new Thread(() -> {
            try {
                manager.withCpuFallback(new byte[]{9}, cuda, config -> {
                    if (!config.isCuda()) cpuAttempts.incrementAndGet();
                    waiterEntered.countDown();
                    try (OnnxRuntimeSessionManager.SessionLease ignored = manager.acquireSession(new byte[]{9}, config)) {
                        return "done";
                    }
                });
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                interruptPreserved.set(Thread.currentThread().isInterrupted());
                waiterExited.countDown();
            }
        });
        try {
            Future<?> loading = callers.submit(() -> manager.acquireSession(new byte[]{9}, cuda).close());
            assertTrue(manager.entered.await(1, TimeUnit.SECONDS));
            Future<?> other = callers.submit(() -> manager.acquireSession(new byte[]{9}, cuda).close());
            waiter.start();
            assertTrue(waiterEntered.await(1, TimeUnit.SECONDS));
            waiter.interrupt();
            assertTrue("被中断的等待者必须立即退出", waiterExited.await(1, TimeUnit.SECONDS));
            assertTrue(failure.get() instanceof IllegalStateException);
            assertTrue(failure.get().getCause() instanceof InterruptedException);
            assertEquals(Boolean.TRUE, interruptPreserved.get());
            assertEquals(0, cpuAttempts.get());
            manager.release.countDown();
            loading.get(1, TimeUnit.SECONDS);
            other.get(1, TimeUnit.SECONDS);
            assertEquals(1, manager.created.get());
            assertEquals(0, manager.closeCount.get());
        } finally {
            manager.release.countDown();
            waiter.join(2000);
            callers.shutdownNow();
            callers.awaitTermination(2, TimeUnit.SECONDS);
            manager.close(false);
        }
        assertEquals(1, manager.closeCount.get());
    }

    private static final class BlockingSessionManager extends OnnxRuntimeSessionManager {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicInteger created = new AtomicInteger();
        private final AtomicInteger closeCount = new AtomicInteger();

        private BlockingSessionManager(int capacity) { super(capacity); }

        @Override
        CachedSession createCachedSession(byte[] modelBytes, OnnxRuntimeConfig config) {
            created.incrementAndGet();
            if (modelBytes[0] == 9) {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test load was not released");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("test load interrupted", interrupted);
                }
            }
            return new CachedSession(closeCount::incrementAndGet);
        }
    }

    @Test
    public void retiredPendingLoadKeepsReservationUntilLateSessionIsClosed() throws Exception {
        BlockingSessionManager manager = new BlockingSessionManager(1);
        ExecutorService caller = Executors.newSingleThreadExecutor();
        OnnxRuntimeConfig cuda = OnnxRuntimeConfig.from(JSON.parseObject("{\"executionProvider\":\"CUDA\"}"));
        try {
            Future<?> loading = caller.submit(() -> manager.acquireSession(new byte[]{9}, cuda).close());
            assertTrue(manager.entered.await(1, TimeUnit.SECONDS));
            manager.retireSession(new byte[]{9}, cuda);
            assertEquals(1, manager.sessionCacheMetrics().get("loadingSessions"));
            assertThrows(OnnxRuntimeSessionManager.SessionCapacityException.class,
                    () -> manager.acquireSession(new byte[]{2}, OnnxRuntimeConfig.cpu()));
            manager.release.countDown();
            ExecutionException failure = assertThrows(ExecutionException.class, () -> loading.get(1, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof IllegalStateException);
            assertEquals(1, manager.closeCount.get());
            assertEquals(0, manager.getCachedSessionCount());
            manager.acquireSession(new byte[]{2}, OnnxRuntimeConfig.cpu()).close();
            assertEquals(1, manager.sessionCacheMetrics().get("loadedSessions"));
        } finally {
            manager.release.countDown();
            caller.shutdownNow();
            caller.awaitTermination(2, TimeUnit.SECONDS);
            manager.close(false);
        }
    }

    @Test
    public void nativeCloseDoesNotBlockHotLeaseOrReleaseCapacityEarly() throws Exception {
        CountDownLatch closingStarted = new CountDownLatch(1);
        CountDownLatch releaseClose = new CountDownLatch(1);
        AtomicInteger closed = new AtomicInteger();
        OnnxRuntimeSessionManager manager = new OnnxRuntimeSessionManager(2) {
            @Override
            CachedSession createCachedSession(byte[] bytes, OnnxRuntimeConfig config) {
                return new CachedSession(() -> {
                    if (bytes[0] == 1) {
                        closingStarted.countDown();
                        try {
                            if (!releaseClose.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test close was not released");
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(interrupted);
                        }
                    }
                    closed.incrementAndGet();
                });
            }
        };
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try (OnnxRuntimeSessionManager.SessionLease hotLease = manager.acquireSession(new byte[]{2}, OnnxRuntimeConfig.cpu())) {
            manager.acquireSession(new byte[]{1}, OnnxRuntimeConfig.cpu()).close();
            Future<?> cold = callers.submit(() -> manager.acquireSession(new byte[]{3}, OnnxRuntimeConfig.cpu()).close());
            assertTrue(closingStarted.await(1, TimeUnit.SECONDS));
            CountDownLatch hotFinished = new CountDownLatch(1);
            Future<?> hot = callers.submit(() -> {
                manager.acquireSession(new byte[]{2}, OnnxRuntimeConfig.cpu()).close();
                hotFinished.countDown();
            });
            assertTrue("native关闭期间无关热模型必须可用", hotFinished.await(1, TimeUnit.SECONDS));
            hot.get(1, TimeUnit.SECONDS);
            assertEquals(2, manager.sessionCacheMetrics().get("loadedSessions"));
            assertEquals(1, manager.sessionCacheMetrics().get("retiredSessions"));
            assertThrows(OnnxRuntimeSessionManager.SessionCapacityException.class,
                    () -> manager.acquireSession(new byte[]{4}, OnnxRuntimeConfig.cpu()));
            releaseClose.countDown();
            cold.get(1, TimeUnit.SECONDS);
            assertEquals(1, closed.get());
        } finally {
            releaseClose.countDown();
            callers.shutdownNow();
            callers.awaitTermination(2, TimeUnit.SECONDS);
            manager.close(false);
        }
        assertEquals(3, closed.get());
    }

    @Test
    public void evictsLeastRecentlyUsedIdleSessionAndReleasesResources() {
        TestSessionManager manager = new TestSessionManager(2);
        try {
            manager.acquireSession(new byte[]{1}, OnnxRuntimeConfig.cpu()).close();
            manager.acquireSession(new byte[]{2}, OnnxRuntimeConfig.cpu()).close();
            manager.acquireSession(new byte[]{1}, OnnxRuntimeConfig.cpu()).close();
            manager.acquireSession(new byte[]{3}, OnnxRuntimeConfig.cpu()).close();

            assertEquals(2, manager.getCachedSessionCount());
            assertFalse(manager.created.get(0).isClosed());
            assertTrue(manager.created.get(1).isClosed());
            assertFalse(manager.created.get(2).isClosed());
            assertEquals(1, manager.closeCount.get());
            assertEquals(1L, manager.sessionCacheMetrics().get("evictions"));
        } finally {
            manager.close(false);
        }
        assertEquals(3, manager.closeCount.get());
    }

    @Test
    public void fullCacheDoesNotCloseActiveLeasesAndAdmitsAfterRelease() {
        TestSessionManager manager = new TestSessionManager(2);
        try (OnnxRuntimeSessionManager.SessionLease first = manager.acquireSession(new byte[]{1}, OnnxRuntimeConfig.cpu());
             OnnxRuntimeSessionManager.SessionLease second = manager.acquireSession(new byte[]{2}, OnnxRuntimeConfig.cpu())) {
            RuntimeException failure = assertThrows(OnnxRuntimeSessionManager.SessionCapacityException.class,
                    () -> manager.acquireSession(new byte[]{3}, OnnxRuntimeConfig.cpu()));
            assertTrue(failure.getMessage().contains("上限"));
            assertTrue(failure instanceof ExecutionCapacityException);
            assertEquals(ExecutionCapacityException.Resource.ONNX_SESSION_CACHE,
                    ((ExecutionCapacityException) failure).getResource());
            assertEquals(2, manager.created.size());
            assertEquals(0, manager.closeCount.get());
            assertEquals(2, manager.sessionCacheMetrics().get("loadedSessions"));
            assertEquals(1L, manager.sessionCacheMetrics().get("capacityRejections"));

            first.close();
            manager.acquireSession(new byte[]{3}, OnnxRuntimeConfig.cpu()).close();
            assertEquals(1, manager.closeCount.get());
            assertFalse(manager.created.get(1).isClosed());
        } finally {
            manager.close(false);
        }
    }

    @Test
    public void retiredButLeasedSessionStillConsumesCapacity() {
        TestSessionManager manager = new TestSessionManager(1);
        OnnxRuntimeConfig cuda = OnnxRuntimeConfig.from(JSON.parseObject("{\"executionProvider\":\"CUDA\"}"));
        try (OnnxRuntimeSessionManager.SessionLease active = manager.acquireSession(new byte[]{1}, cuda)) {
            manager.retireSession(new byte[]{1}, cuda);
            assertEquals(0, manager.getCachedSessionCount());
            assertEquals(0, manager.closeCount.get());
            assertEquals(1, manager.sessionCacheMetrics().get("retiredSessions"));
            assertEquals(1, manager.sessionCacheMetrics().get("loadedSessions"));
            assertThrows(OnnxRuntimeSessionManager.SessionCapacityException.class,
                    () -> manager.acquireSession(new byte[]{2}, OnnxRuntimeConfig.cpu()));
            assertEquals(1, manager.created.size());

            active.close();
            assertEquals(1, manager.closeCount.get());
            assertEquals(0, manager.sessionCacheMetrics().get("loadedSessions"));
            manager.acquireSession(new byte[]{2}, OnnxRuntimeConfig.cpu()).close();
            assertEquals(1, manager.getCachedSessionCount());
        } finally {
            manager.close(false);
        }
    }

    @Test
    public void capacityRejectionDoesNotRetryOrRememberCpuFallback() {
        TestSessionManager manager = new TestSessionManager(1);
        OnnxRuntimeConfig cuda = OnnxRuntimeConfig.from(JSON.parseObject("{\"executionProvider\":\"CUDA\"}"));
        AtomicInteger attempts = new AtomicInteger();
        try (OnnxRuntimeSessionManager.SessionLease active = manager.acquireSession(new byte[]{1}, OnnxRuntimeConfig.cpu())) {
            assertThrows(OnnxRuntimeSessionManager.SessionCapacityException.class, () -> manager.withCpuFallback(new byte[]{2}, cuda, config -> {
                attempts.incrementAndGet();
                try (OnnxRuntimeSessionManager.SessionLease ignored = manager.acquireSession(new byte[]{2}, config)) {
                    return "loaded";
                }
            }));
            assertEquals(1, attempts.get());
            assertEquals(0, manager.getActiveCpuFallbackCount());
            assertEquals(1, manager.created.size());
        } finally {
            manager.close(false);
        }
    }

    @Test
    public void cpuFallbackHistoryDoesNotAccumulateAcrossUnlimitedModelVersions() {
        TestSessionManager manager = new TestSessionManager(2);
        OnnxRuntimeConfig cuda = OnnxRuntimeConfig.from(JSON.parseObject("{\"executionProvider\":\"CUDA\"}"));
        try {
            for (int i = 1; i <= 3; i++) {
                assertEquals("cpu", manager.withCpuFallback(new byte[]{(byte) i}, cuda, config -> {
                    if (config.isCuda()) throw new IllegalArgumentException("CUDA unavailable");
                    return "cpu";
                }));
            }
            assertEquals(2, manager.getActiveCpuFallbackCount());
            assertTrue(manager.usesCpuFallback(new byte[]{3}, cuda));
        } finally {
            manager.close(false);
        }
    }

    @Test
    public void closedManagerCannotRecreateNativeSessions() {
        TestSessionManager manager = new TestSessionManager(1);
        manager.close(false);
        assertThrows(IllegalStateException.class,
                () -> manager.acquireSession(new byte[]{1}, OnnxRuntimeConfig.cpu()));
        assertTrue(manager.created.isEmpty());
    }

    @Test
    public void completingCpuFallbackAfterCloseDoesNotRepopulateState() {
        TestSessionManager manager = new TestSessionManager(1);
        OnnxRuntimeConfig cuda = OnnxRuntimeConfig.from(JSON.parseObject("{\"executionProvider\":\"CUDA\"}"));
        assertEquals("cpu", manager.withCpuFallback(new byte[]{1}, cuda, config -> {
            if (config.isCuda()) throw new IllegalArgumentException("CUDA unavailable");
            manager.close(false);
            return "cpu";
        }));
        assertEquals(0, manager.getActiveCpuFallbackCount());
        assertEquals(0, manager.getCachedSessionCount());
    }

    @Test
    public void rejectsNonPositiveCacheCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new OnnxRuntimeSessionManager(0));
        assertThrows(IllegalArgumentException.class, () -> new OnnxRuntimeSessionManager(-1));
    }

    private static final class TestSessionManager extends OnnxRuntimeSessionManager {
        private final AtomicInteger closeCount = new AtomicInteger();
        private final List<CachedSession> created = new ArrayList<>();

        private TestSessionManager(int capacity) {
            super(capacity);
        }

        @Override
        CachedSession createCachedSession(byte[] modelBytes, OnnxRuntimeConfig config) {
            CachedSession session = new CachedSession(closeCount::incrementAndGet);
            created.add(session);
            return session;
        }
    }

    @Test
    public void reportsConfiguredCpuWithoutCreatingSession() throws Exception {
        OnnxRuntimeSessionManager manager = new OnnxRuntimeSessionManager();

        Map<String, Object> state = manager.runtimeState(digest(new byte[]{1}), OnnxRuntimeConfig.cpu());

        assertEquals("CPU", state.get("state"));
        assertEquals("CPU", state.get("effectiveProvider"));
        assertNull(state.get("reason"));
        assertEquals(0, manager.getCachedSessionCount());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void reportsOnlyTheExactlyConfiguredCudaSession() throws Exception {
        byte[] model = new byte[]{2};
        OnnxRuntimeConfig cuda0 = OnnxRuntimeConfig.from(JSON.parseObject(
                "{\"executionProvider\":\"CUDA\",\"cudaDeviceId\":0}"));
        OnnxRuntimeConfig cuda1 = OnnxRuntimeConfig.from(JSON.parseObject(
                "{\"executionProvider\":\"CUDA\",\"cudaDeviceId\":1}"));
        OnnxRuntimeSessionManager manager = new OnnxRuntimeSessionManager();
        Map<String, OnnxRuntimeSessionManager.CachedSession> sessions =
                (Map<String, OnnxRuntimeSessionManager.CachedSession>)
                        ReflectionTestUtils.getField(manager, "sessions");
        assertNotNull(sessions);
        sessions.put(digest(model) + ':' + cuda0.cacheKey(),
                new OnnxRuntimeSessionManager.CachedSession(() -> { }));

        assertEquals("CUDA", manager.runtimeState(digest(model), cuda0).get("state"));
        assertEquals("CUDA", manager.runtimeState(digest(model), cuda0).get("effectiveProvider"));
        assertEquals("NOT_INITIALIZED", manager.runtimeState(digest(model), cuda1).get("state"));
        assertNull(manager.runtimeState(digest(model), cuda1).get("effectiveProvider"));
    }

    @Test
    public void reportsCpuFallbackReasonForTheExactCudaConfiguration() throws Exception {
        byte[] model = new byte[]{3};
        OnnxRuntimeConfig cuda = OnnxRuntimeConfig.from(JSON.parseObject(
                "{\"executionProvider\":\"CUDA\",\"cudaDeviceId\":0}"));
        OnnxRuntimeSessionManager manager = new OnnxRuntimeSessionManager();
        manager.withCpuFallback(model, cuda, config -> {
            if (config.isCuda()) throw new IllegalArgumentException("missing CUDA runtime");
            return "cpu";
        });

        Map<String, Object> state = manager.runtimeState(digest(model), cuda);

        assertEquals("CPU_FALLBACK", state.get("state"));
        assertEquals("CPU", state.get("effectiveProvider"));
        assertEquals("missing CUDA runtime", state.get("reason"));
    }

    @Test
    public void reportsUnknownForMissingDigestOrConfiguration() {
        OnnxRuntimeSessionManager manager = new OnnxRuntimeSessionManager();

        assertEquals("UNKNOWN", manager.runtimeState("", OnnxRuntimeConfig.cpu()).get("state"));
        assertEquals("UNKNOWN", manager.runtimeState("digest", null).get("state"));
    }

    @Test
    public void constructionDoesNotRequireNativeRuntime() {
        AtomicInteger loads = new AtomicInteger();
        OnnxRuntimeSessionManager manager = new OnnxRuntimeSessionManager(() -> {
            loads.incrementAndGet();
            throw new UnsatisfiedLinkError("native runtime unavailable");
        });

        assertEquals(0, loads.get());
        Map<String, Object> capabilities = manager.runtimeCapabilities();

        assertEquals(1, loads.get());
        assertEquals(16, ((Map<?, ?>) capabilities.get("sessionCache")).get("maxEntries"));
        assertNull(capabilities.get("onnxRuntimeVersion"));
        assertEquals(Boolean.FALSE, capabilities.get("cudaAvailable"));
        assertTrue(String.valueOf(capabilities.get("cudaError")).contains("native runtime unavailable"));
    }

    private static String digest(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    @Test
    public void jvmShutdownDoesNotRaceEnvironmentAndSessionCleanup() throws Exception {
        OnnxTestAssets.requireIntegrationAssets();
        String javaExecutable = new File(System.getProperty("java.home"), "bin/java").getAbsolutePath();
        String classPath = System.getProperty("surefire.test.class.path",
                System.getProperty("java.class.path"));
        Process process = new ProcessBuilder(
                javaExecutable,
                "-XX:ErrorFile=target/onnx-shutdown-hs-err-pid%p.log",
                "-Dtianshu.onnx.integration=true",
                "-cp",
                classPath,
                OnnxJvmShutdownProbe.class.getName())
                .redirectErrorStream(true)
                .start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        }
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) process.destroyForcibly();

        assertTrue("ONNX shutdown probe timed out\n" + output, finished);
        assertEquals("ONNX shutdown probe crashed\n" + output, 0, process.exitValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void jvmShutdownSkipsNativeSessionCloseAndClearsCache() {
        AtomicInteger closeCount = new AtomicInteger();
        OnnxRuntimeSessionManager.CachedSession cached =
                new OnnxRuntimeSessionManager.CachedSession(closeCount::incrementAndGet);
        OnnxRuntimeSessionManager manager = new OnnxRuntimeSessionManager();
        Map<String, OnnxRuntimeSessionManager.CachedSession> sessions =
                (Map<String, OnnxRuntimeSessionManager.CachedSession>)
                        ReflectionTestUtils.getField(manager, "sessions");
        assertNotNull(sessions);
        sessions.put("cuda-session", cached);

        manager.close(true);

        assertEquals(0, closeCount.get());
        assertFalse(cached.isClosed());
        assertEquals(0, manager.getCachedSessionCount());
    }

    @Test
    public void cudaFailureFallsBackToCpuAndCachesSuccessfulFallback() {
        OnnxRuntimeSessionManager manager = new OnnxRuntimeSessionManager();
        OnnxRuntimeConfig cuda = OnnxRuntimeConfig.from(JSON.parseObject(
                "{\"executionProvider\":\"CUDA\",\"cudaDeviceId\":0}"));
        List<String> attempts = new ArrayList<>();
        try {
            String first = manager.withCpuFallback(new byte[]{9}, cuda, config -> {
                attempts.add(config.getExecutionProvider());
                if (config.isCuda()) throw new IllegalArgumentException("missing cublasLt64_12.dll");
                return "cpu-result";
            });
            String second = manager.withCpuFallback(new byte[]{9}, cuda, config -> {
                attempts.add(config.getExecutionProvider());
                if (config.isCuda()) throw new IllegalArgumentException("CUDA should stay bypassed");
                return "cpu-result";
            });

            assertEquals("cpu-result", first);
            assertEquals("cpu-result", second);
            assertEquals(Arrays.asList("CUDA", "CPU", "CPU"), attempts);
            assertEquals(1, manager.getActiveCpuFallbackCount());
            assertEquals(Boolean.TRUE, manager.runtimeCapabilities().get("cpuFallbackEnabled"));
        } finally {
            manager.close();
        }
    }

    @Test
    public void successfulCpuFallbackRetiresFailedCudaSession() {
        AtomicReference<OnnxRuntimeConfig> retiredConfig = new AtomicReference<>();
        OnnxRuntimeSessionManager manager = new OnnxRuntimeSessionManager() {
            @Override
            void retireSession(byte[] modelBytes, OnnxRuntimeConfig runtimeConfig) {
                retiredConfig.set(runtimeConfig);
            }
        };
        OnnxRuntimeConfig cuda = OnnxRuntimeConfig.from(JSON.parseObject(
                "{\"executionProvider\":\"CUDA\",\"cudaDeviceId\":0}"));
        try {
            String result = manager.withCpuFallback(new byte[]{6}, cuda, config -> {
                if (config.isCuda()) throw new IllegalArgumentException("CUDA runtime failed");
                return "cpu-result";
            });

            assertEquals("cpu-result", result);
            assertTrue(retiredConfig.get().isCuda());
        } finally {
            manager.close();
        }
    }

    @Test
    public void retiredSessionClosesOnlyAfterLastLeaseIsReleased() {
        AtomicInteger closeCount = new AtomicInteger();
        OnnxRuntimeSessionManager.CachedSession cached =
                new OnnxRuntimeSessionManager.CachedSession(closeCount::incrementAndGet);
        OnnxRuntimeSessionManager.SessionLease first = cached.acquire();
        OnnxRuntimeSessionManager.SessionLease second = cached.acquire();

        cached.retire();
        assertFalse(cached.isClosed());
        first.close();
        assertFalse(cached.isClosed());
        second.close();
        second.close();

        assertTrue(cached.isClosed());
        assertEquals(1, closeCount.get());
    }

    @Test
    public void failedCpuRetryDoesNotCacheFallbackAndReportsBothFailures() {
        OnnxRuntimeSessionManager manager = new OnnxRuntimeSessionManager();
        OnnxRuntimeConfig cuda = OnnxRuntimeConfig.from(JSON.parseObject(
                "{\"executionProvider\":\"CUDA\",\"cudaDeviceId\":0}"));
        List<String> attempts = new ArrayList<>();
        try {
            try {
                manager.withCpuFallback(new byte[]{8}, cuda, config -> {
                    attempts.add(config.getExecutionProvider());
                    throw new IllegalArgumentException(config.isCuda() ? "CUDA failed" : "CPU failed");
                });
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("CUDA failed"));
                assertTrue(expected.getMessage().contains("CPU failed"));
                assertEquals(Arrays.asList("CUDA", "CPU"), attempts);
                assertEquals(0, manager.getActiveCpuFallbackCount());
                return;
            }
            throw new AssertionError("Expected combined CUDA and CPU failure");
        } finally {
            manager.close();
        }
    }

    @Test
    public void explicitCpuConfigurationDoesNotRetry() {
        OnnxRuntimeSessionManager manager = new OnnxRuntimeSessionManager();
        List<String> attempts = new ArrayList<>();
        try {
            try {
                manager.withCpuFallback(new byte[]{7}, OnnxRuntimeConfig.cpu(), config -> {
                    attempts.add(config.getExecutionProvider());
                    throw new IllegalArgumentException("CPU failed");
                });
            } catch (IllegalArgumentException expected) {
                assertEquals("CPU failed", expected.getMessage());
                assertEquals(Arrays.asList("CPU"), attempts);
                return;
            }
            throw new AssertionError("Expected CPU failure");
        } finally {
            manager.close();
        }
    }

    @Test
    public void exposesRuntimeCapabilitiesIncludingCpuProvider() {
        OnnxRuntimeSessionManager manager = new OnnxRuntimeSessionManager();
        try {
            Map<String, Object> capabilities = manager.runtimeCapabilities();

            assertNotNull(capabilities.get("onnxRuntimeVersion"));
            assertTrue(((Iterable<?>) capabilities.get("availableProviders"))
                    .iterator().hasNext());
            assertTrue(String.valueOf(capabilities.get("availableProviders")).contains("CPU"));
            assertNotNull(capabilities.get("cudaAvailable"));
            if (Boolean.TRUE.equals(capabilities.get("cudaAvailable"))) {
                assertTrue(String.valueOf(capabilities.get("availableProviders")).contains("CUDA"));
                assertNull(capabilities.get("cudaError"));
            } else {
                assertFalse(String.valueOf(capabilities.get("cudaError")).trim().isEmpty());
            }
        } finally {
            manager.close();
        }
    }

    @Test
    public void inspectsRealMn3ModelAndReusesSessionByContentHash() throws Exception {
        byte[] model = OnnxTestAssets.read("onnx/mn3/anti-spoof-mn3.onnx");
        OnnxRuntimeSessionManager manager = new OnnxRuntimeSessionManager();
        try {
            Map<String, Object> metadata = manager.inspect(model);
            Map<?, ?> inputs = (Map<?, ?>) metadata.get("inputs");
            Map<?, ?> outputs = (Map<?, ?>) metadata.get("outputs");

            assertFalse(inputs.isEmpty());
            assertTrue(outputs.toString(), outputs.containsKey("output1"));
            manager.inspect(model);
            assertEquals(1, manager.getCachedSessionCount());
        } finally {
            manager.close();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidOnnxBytes() {
        OnnxRuntimeSessionManager manager = new OnnxRuntimeSessionManager();
        try {
            manager.inspect(new byte[]{1, 2, 3});
        } finally {
            manager.close();
        }
    }

    @Test
    public void inspectAvailableBuffaloModels() throws Exception {
        OnnxRuntimeSessionManager manager = new OnnxRuntimeSessionManager();
        try {
            for (String name : Arrays.asList("det_10g.onnx", "w600k_r50.onnx", "2d106det.onnx", "1k3d68.onnx", "genderage.onnx")) {
                Map<String, Object> metadata = manager.inspect(
                        OnnxTestAssets.read("onnx/buffalo_l/" + name));
                assertFalse(((Map<?, ?>) metadata.get("inputs")).isEmpty());
                assertFalse(((Map<?, ?>) metadata.get("outputs")).isEmpty());
            }
        } finally {
            manager.close();
        }
    }

    @Test
    public void inspectAvailableAntispoofModels() throws Exception {
        OnnxRuntimeSessionManager manager = new OnnxRuntimeSessionManager();
        try {
            for (String path : Arrays.asList(
                    "onnx/mn3/anti-spoof-mn3.onnx",
                    "onnx/facenox/best_model.onnx")) {
                Map<String, Object> metadata = manager.inspect(OnnxTestAssets.read(path));
                assertFalse(((Map<?, ?>) metadata.get("inputs")).isEmpty());
                assertFalse(((Map<?, ?>) metadata.get("outputs")).isEmpty());
            }
        } finally {
            manager.close();
        }
    }
}
