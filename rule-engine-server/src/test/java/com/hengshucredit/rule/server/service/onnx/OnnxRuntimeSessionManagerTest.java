package com.hengshucredit.rule.server.service.onnx;

import org.junit.Assume;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OnnxRuntimeSessionManagerTest {

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
