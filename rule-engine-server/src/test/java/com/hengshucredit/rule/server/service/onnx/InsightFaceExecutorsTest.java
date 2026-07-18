package com.hengshucredit.rule.server.service.onnx;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class InsightFaceExecutorsTest {

    @Test
    public void runsBuffaloRecognitionLandmarksAndGenderAgePerFace() throws Exception {
        String image = OnnxTestAssets.imageBase64();
        OnnxRuntimeSessionManager manager = new OnnxRuntimeSessionManager();
        try {
            List<Map<String, Object>> faces = faces(manager, image);

            Map<String, Object> recognition = new ArcFaceRecognitionExecutor(manager).execute(
                    OnnxTestAssets.read("onnx/buffalo_l/w600k_r50.onnx"), image, faces);
            Map<?, ?> recognitionItem = first(recognition);
            List<?> embedding = (List<?>) recognitionItem.get("embedding");
            List<?> normalized = (List<?>) recognitionItem.get("normalizedEmbedding");
            assertEquals(512, embedding.size());
            assertEquals(512, normalized.size());
            assertEquals(1d, norm(normalized), 0.0001d);
            assertTrue(((Map<?, ?>) recognition.get("rawOutputs")).containsKey("683"));

            Map<String, Object> landmark2d = new LandmarkExecutor(manager, false).execute(
                    OnnxTestAssets.read("onnx/buffalo_l/2d106det.onnx"), image, faces);
            assertEquals(106, ((List<?>) first(landmark2d).get("landmarks")).size());
            assertTrue(((Map<?, ?>) landmark2d.get("rawOutputs")).containsKey("fc1"));

            Map<String, Object> landmark3d = new LandmarkExecutor(manager, true).execute(
                    OnnxTestAssets.read("onnx/buffalo_l/1k3d68.onnx"), image, faces);
            assertEquals(68, ((List<?>) first(landmark3d).get("landmarks")).size());
            assertEquals(3, ((List<?>) ((List<?>) first(landmark3d).get("landmarks")).get(0)).size());

            Map<String, Object> genderAge = new GenderAgeExecutor(manager).execute(
                    OnnxTestAssets.read("onnx/buffalo_l/genderage.onnx"), image, faces);
            Map<?, ?> genderAgeItem = first(genderAge);
            assertTrue(((Number) genderAgeItem.get("gender")).intValue() == 0
                    || ((Number) genderAgeItem.get("gender")).intValue() == 1);
            assertTrue(genderAgeItem.get("age") instanceof Number);
        } finally {
            manager.close();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> faces(OnnxRuntimeSessionManager manager,
                                                    String image) throws Exception {
        return (List<Map<String, Object>>) (List<?>) new ScrfdFaceDetectionExecutor(manager).execute(
                OnnxTestAssets.read("onnx/buffalo_l/det_10g.onnx"), image,
                OnnxTaskConfig.parse("{\"onnxTaskType\":\"SCRFD_FACE_DETECTION\"}"))
                .get("faces");
    }

    private static Map<?, ?> first(Map<String, Object> output) {
        return (Map<?, ?>) ((List<?>) output.get("results")).get(0);
    }

    private static double norm(List<?> values) {
        double sum = 0d;
        for (Object value : values) {
            double number = ((Number) value).doubleValue();
            sum += number * number;
        }
        return Math.sqrt(sum);
    }
}
