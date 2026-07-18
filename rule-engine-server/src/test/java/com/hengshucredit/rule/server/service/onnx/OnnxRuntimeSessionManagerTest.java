package com.hengshucredit.rule.server.service.onnx;

import com.alibaba.fastjson.JSON;
import org.junit.Assume;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class OnnxRuntimeSessionManagerTest {

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
        } finally {
            manager.close();
        }
    }

    @Test
    public void inspectsRealMn3ModelAndReusesSessionByContentHash() throws Exception {
        Path modelPath = Paths.get("C:/Users/Administrator/Downloads/anti-spoof-mn3.onnx");
        Assume.assumeTrue(Files.isRegularFile(modelPath));
        byte[] model = Files.readAllBytes(modelPath);
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
                Path path = Paths.get("C:/Users/Administrator/Downloads/buffalo_l", name);
                Assume.assumeTrue(Files.isRegularFile(path));
                Map<String, Object> metadata = manager.inspect(Files.readAllBytes(path));
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
            for (Path path : Arrays.asList(
                    Paths.get("C:/Users/Administrator/Downloads/anti-spoof-mn3.onnx"),
                    Paths.get("../.tmp/facenox-face-antispoof-onnx/models/best/98.20/best_model.onnx"))) {
                Assume.assumeTrue(Files.isRegularFile(path));
                Map<String, Object> metadata = manager.inspect(Files.readAllBytes(path));
                assertFalse(((Map<?, ?>) metadata.get("inputs")).isEmpty());
                assertFalse(((Map<?, ?>) metadata.get("outputs")).isEmpty());
            }
        } finally {
            manager.close();
        }
    }
}
