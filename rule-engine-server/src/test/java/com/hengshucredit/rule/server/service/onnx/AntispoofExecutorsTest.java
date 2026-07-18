package com.hengshucredit.rule.server.service.onnx;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AntispoofExecutorsTest {

    @Test
    public void facenoxReturnsPerFaceOriginalLogitsAndRawNode() throws Exception {
        byte[] detector = OnnxTestAssets.read("onnx/yunet/detector.onnx");
        byte[] model = OnnxTestAssets.read("onnx/facenox/best_model.onnx");
        String image = imageBase64();
        List<Map<String, Object>> faces = faces(detector, image);
        OnnxRuntimeSessionManager manager = new OnnxRuntimeSessionManager();
        try {
            Map<String, Object> output = new FacenoxAntispoofExecutor(manager)
                    .execute(model, image, faces);

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
        byte[] detector = OnnxTestAssets.read("onnx/yunet/detector.onnx");
        byte[] model = OnnxTestAssets.read("onnx/mn3/anti-spoof-mn3.onnx");
        String image = imageBase64();
        List<Map<String, Object>> faces = faces(detector, image);
        OnnxRuntimeSessionManager manager = new OnnxRuntimeSessionManager();
        try {
            Map<String, Object> output = new Mn3AntispoofExecutor(manager)
                    .execute(model, image, faces);

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
        return OnnxTestAssets.imageBase64();
    }

    private static List<Map<String, Object>> faces(byte[] detector, String image) throws Exception {
        OnnxRuntimeSessionManager manager = new OnnxRuntimeSessionManager();
        try {
            return new YunetFaceDetectionExecutor(manager).detect(detector, image,
                    OnnxTaskConfig.parse("{\"onnxTaskType\":\"YUNET_FACE_DETECTION\"}"));
        } finally {
            manager.close();
        }
    }
}
