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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AntispoofExecutorsTest {

    @Test
    public void facenoxReturnsPerFaceOriginalLogitsAndRawNode() throws Exception {
        Path detector = Paths.get("../.tmp/facenox-face-antispoof-onnx/models/detector.onnx");
        Path model = Paths.get("../.tmp/facenox-face-antispoof-onnx/models/best/98.20/best_model.onnx");
        Assume.assumeTrue(Files.isRegularFile(detector) && Files.isRegularFile(model));
        String image = imageBase64();
        List<Map<String, Object>> faces = faces(detector, image);
        OnnxRuntimeSessionManager manager = new OnnxRuntimeSessionManager();
        try {
            Map<String, Object> output = new FacenoxAntispoofExecutor(manager)
                    .execute(Files.readAllBytes(model), image, faces);

            assertTrue(((Map<?, ?>) output.get("rawOutputs")).containsKey("output"));
            List<?> results = (List<?>) output.get("results");
            assertEquals(faces.size(), results.size());
            Map<?, ?> first = (Map<?, ?>) results.get(0);
            assertEquals(0, first.get("faceIndex"));
            assertEquals(2, ((List<?>) first.get("logits")).size());
            assertFalse(first.containsKey("isReal"));
            assertFalse(first.containsKey("threshold"));
        } finally {
            manager.close();
        }
    }

    @Test
    public void mn3ReturnsProbabilitiesWithoutRelabelingThemAsLogits() throws Exception {
        Path detector = Paths.get("../.tmp/facenox-face-antispoof-onnx/models/detector.onnx");
        Path model = Paths.get("C:/Users/Administrator/Downloads/anti-spoof-mn3.onnx");
        Assume.assumeTrue(Files.isRegularFile(detector) && Files.isRegularFile(model));
        String image = imageBase64();
        List<Map<String, Object>> faces = faces(detector, image);
        OnnxRuntimeSessionManager manager = new OnnxRuntimeSessionManager();
        try {
            Map<String, Object> output = new Mn3AntispoofExecutor(manager)
                    .execute(Files.readAllBytes(model), image, faces);

            assertTrue(((Map<?, ?>) output.get("rawOutputs")).containsKey("output1"));
            Map<?, ?> first = (Map<?, ?>) ((List<?>) output.get("results")).get(0);
            assertEquals(2, ((List<?>) first.get("probabilities")).size());
            assertFalse(first.containsKey("logits"));
            assertFalse(first.containsKey("isSpoof"));
        } finally {
            manager.close();
        }
    }

    private static String imageBase64() throws Exception {
        return Base64.getEncoder().encodeToString(Files.readAllBytes(Paths.get("../assets/docs/face.jpg")));
    }

    private static List<Map<String, Object>> faces(Path detector, String image) throws Exception {
        OnnxRuntimeSessionManager manager = new OnnxRuntimeSessionManager();
        try {
            return new YunetFaceDetectionExecutor(manager).detect(Files.readAllBytes(detector), image,
                    OnnxTaskConfig.parse("{\"onnxTaskType\":\"YUNET_FACE_DETECTION\"}"));
        } finally {
            manager.close();
        }
    }
}
