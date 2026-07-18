package com.hengshucredit.rule.server.service.onnx;

import ai.onnxruntime.OrtSession;
import com.alibaba.fastjson.JSON;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OnnxInferenceRunnerTest {

    @Test
    public void runNativeWrapsSessionRunInsideCpuFallbackBoundary() {
        AtomicReference<OnnxRuntimeConfig> requestedConfig = new AtomicReference<>();
        OnnxRuntimeSessionManager manager = new OnnxRuntimeSessionManager() {
            @Override
            public OrtSession session(byte[] modelBytes, OnnxRuntimeConfig runtimeConfig) {
                throw new AssertionError("runner must wrap the complete inference operation");
            }

            @Override
            @SuppressWarnings("unchecked")
            <T> T withCpuFallback(byte[] modelBytes, OnnxRuntimeConfig runtimeConfig,
                                  RuntimeOperation<T> operation) {
                requestedConfig.set(runtimeConfig);
                return (T) Collections.singletonMap("output", new float[]{1f});
            }
        };
        OnnxRuntimeConfig cuda = OnnxRuntimeConfig.from(JSON.parseObject(
                "{\"executionProvider\":\"CUDA\",\"cudaDeviceId\":0}"));
        try {
            Map<String, Object> outputs = new OnnxInferenceRunner(manager, cuda)
                    .runNative(new byte[]{1}, new float[][]{{1f}});

            assertTrue(requestedConfig.get().isCuda());
            assertEquals(1f, ((float[]) outputs.get("output"))[0], 0f);
        } finally {
            manager.close();
        }
    }

    @Test
    public void firstInputShapeUsesCpuFallbackBoundary() {
        AtomicReference<OnnxRuntimeConfig> requestedConfig = new AtomicReference<>();
        OnnxRuntimeSessionManager manager = new OnnxRuntimeSessionManager() {
            @Override
            @SuppressWarnings("unchecked")
            <T> T withCpuFallback(byte[] modelBytes, OnnxRuntimeConfig runtimeConfig,
                                  RuntimeOperation<T> operation) {
                requestedConfig.set(runtimeConfig);
                return (T) new long[]{1, 3, 80, 80};
            }
        };
        OnnxRuntimeConfig cuda = OnnxRuntimeConfig.from(JSON.parseObject(
                "{\"executionProvider\":\"CUDA\",\"cudaDeviceId\":0}"));
        try {
            long[] shape = new OnnxInferenceRunner(manager, cuda).firstInputShape(new byte[]{1});

            assertTrue(requestedConfig.get().isCuda());
            assertEquals(80L, shape[2]);
        } finally {
            manager.close();
        }
    }
}
