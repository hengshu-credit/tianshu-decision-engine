package com.hengshucredit.rule.server.service.onnx;

import org.junit.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

/** Real native inference without external assets, CUDA or a mocked session. */
public class RepositoryOnnxCpuTest {

    @Test
    public void repositoryModelExecutesExactOutputsAndReusesSession() throws Exception {
        byte[] model = modelBytes();
        OnnxRuntimeSessionManager manager = new OnnxRuntimeSessionManager();
        try {
            OnnxInferenceRunner runner = new OnnxInferenceRunner(manager);
            Map<String, Object> metadata = manager.inspect(model);
            assertMetadata(metadata, "inputs", "Input_Tensor");
            assertMetadata(metadata, "outputs", "Risk_Score");
            assertArrayEquals(new long[]{1, 3}, runner.firstInputShape(model));
            assertEquals(Map.of("Risk_Score", List.of(List.of(-1f, 1f, 7f))),
                    runner.run(model, new float[][]{{-1f, 0f, 3f}}));
            assertEquals(Map.of("Risk_Score", List.of(List.of(2f, 5f, 21f))),
                    runner.run(model, new float[][]{{0.5f, 2f, 10f}}));
            assertEquals(1, manager.getCachedSessionCount());
        } finally {
            manager.close();
        }
        assertEquals(0, manager.getCachedSessionCount());
    }

    @Test
    public void invalidInputShapeFailsAndNextValidInferenceStillWorks() throws Exception {
        byte[] model = modelBytes();
        OnnxRuntimeSessionManager manager = new OnnxRuntimeSessionManager();
        try {
            OnnxInferenceRunner runner = new OnnxInferenceRunner(manager);
            assertThrows(IllegalArgumentException.class,
                    () -> runner.run(model, new float[][]{{1f, 2f}}));
            assertEquals(Map.of("Risk_Score", List.of(List.of(3f, 5f, 7f))),
                    runner.run(model, new float[][]{{1f, 2f, 3f}}));
        } finally {
            manager.close();
        }
    }

    private void assertMetadata(Map<String, Object> metadata, String direction, String name) {
        assertEquals(Map.of(name, Map.of("type", "FLOAT", "shape", List.of(1L, 3L))),
                metadata.get(direction));
    }

    private byte[] modelBytes() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/onnx-smoke/linear.onnx")) {
            assertNotNull("repository ONNX smoke fixture must be packaged", stream);
            return stream.readAllBytes();
        }
    }
}
