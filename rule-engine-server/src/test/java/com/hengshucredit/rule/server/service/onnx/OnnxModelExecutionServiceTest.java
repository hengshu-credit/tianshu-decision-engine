package com.hengshucredit.rule.server.service.onnx;

import ai.onnxruntime.OrtSession;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OnnxModelExecutionServiceTest {

    @Test
    public void preloadUsesRuntimeConfigurationFromModelConfig() {
        AtomicReference<OnnxRuntimeConfig> captured = new AtomicReference<>();
        OnnxRuntimeSessionManager manager = new OnnxRuntimeSessionManager() {
            @Override
            public OrtSession session(byte[] modelBytes) {
                captured.set(OnnxRuntimeConfig.cpu());
                return null;
            }

            @Override
            public OrtSession session(byte[] modelBytes, OnnxRuntimeConfig runtimeConfig) {
                captured.set(runtimeConfig);
                return null;
            }
        };
        try {
            new OnnxModelExecutionService(manager).preload(new byte[]{1},
                    "{\"onnxTaskType\":\"MN3_ANTISPOOF\",\"executionProvider\":\"CUDA\","
                            + "\"cudaDeviceId\":1}");

            assertTrue(captured.get().isCuda());
            assertEquals(1, captured.get().getCudaDeviceId());
        } finally {
            manager.close();
        }
    }

    @Test
    public void dispatchesYunetTaskUsingLogicalImageInput() throws Exception {
        byte[] model = OnnxTestAssets.read("onnx/yunet/detector.onnx");
        OnnxRuntimeSessionManager manager = new OnnxRuntimeSessionManager();
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("image", OnnxTestAssets.imageBase64());

            Map<String, Object> output = new OnnxModelExecutionService(manager).execute(
                    model, "{\"onnxTaskType\":\"YUNET_FACE_DETECTION\"}", params);

            assertEquals(1, ((List<?>) output.get("faces")).size());
        } finally {
            manager.close();
        }
    }
}
