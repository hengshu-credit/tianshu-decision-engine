package com.hengshucredit.rule.server.service.onnx;

import com.alibaba.fastjson.JSON;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class OnnxRuntimeSessionManagerTest {

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
        assertNull(capabilities.get("onnxRuntimeVersion"));
        assertEquals(Boolean.FALSE, capabilities.get("cudaAvailable"));
        assertTrue(String.valueOf(capabilities.get("cudaError")).contains("native runtime unavailable"));
    }

    @Test
    public void jvmShutdownDoesNotRaceEnvironmentAndSessionCleanup() throws Exception {
        String javaExecutable = new File(System.getProperty("java.home"), "bin/java").getAbsolutePath();
        String classPath = System.getProperty("surefire.test.class.path",
                System.getProperty("java.class.path"));
        Process process = new ProcessBuilder(
                javaExecutable,
                "-XX:ErrorFile=target/onnx-shutdown-hs-err-pid%p.log",
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
