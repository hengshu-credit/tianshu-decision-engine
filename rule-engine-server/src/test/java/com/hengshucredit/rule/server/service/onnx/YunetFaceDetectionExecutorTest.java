package com.hengshucredit.rule.server.service.onnx;

import org.junit.Assume;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class YunetFaceDetectionExecutorTest {

    @Test
    public void detectsAllFacesAndKeepsFiveLandmarks() throws Exception {
        Path model = Paths.get("C:/Users/Administrator/Downloads/face-antispoof-onnx-main/models/detector.onnx");
        Assume.assumeTrue(Files.isRegularFile(model));
        String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(Paths.get("../assets/docs/face.jpg")));
        OnnxTaskConfig config = OnnxTaskConfig.parse("{\"onnxTaskType\":\"YUNET_FACE_DETECTION\"}");
        OnnxRuntimeSessionManager manager = new OnnxRuntimeSessionManager();

        try {
            List<Map<String, Object>> faces = new YunetFaceDetectionExecutor(manager)
                    .detect(Files.readAllBytes(model), base64, config);

            assertEquals(1, faces.size());
            Map<String, Object> face = faces.get(0);
            assertTrue(((Number) face.get("confidence")).doubleValue() > 0.8d);
            assertEquals(5, ((List<?>) face.get("landmarks")).size());
            Map<?, ?> bbox = (Map<?, ?>) face.get("bbox");
            assertEquals(297d, ((Number) bbox.get("x")).doubleValue(), 25d);
            assertEquals(70d, ((Number) bbox.get("y")).doubleValue(), 25d);
            assertEquals(152d, ((Number) bbox.get("width")).doubleValue(), 25d);
            assertEquals(209d, ((Number) bbox.get("height")).doubleValue(), 25d);
            assertEquals(1, manager.getCachedSessionCount());
        } finally {
            manager.close();
        }
    }
}
