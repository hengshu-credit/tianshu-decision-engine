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

public class InsightFaceExecutorsTest {

    @Test
    public void runsBuffaloRecognitionLandmarksAndGenderAgePerFace() throws Exception {
        Path directory = Paths.get("C:/Users/Administrator/Downloads/buffalo_l");
        Path imagePath = Paths.get("../assets/docs/face.jpg");
        Assume.assumeTrue(Files.isRegularFile(directory.resolve("det_10g.onnx"))
                && Files.isRegularFile(directory.resolve("w600k_r50.onnx"))
                && Files.isRegularFile(directory.resolve("2d106det.onnx"))
                && Files.isRegularFile(directory.resolve("1k3d68.onnx"))
                && Files.isRegularFile(directory.resolve("genderage.onnx"))
                && Files.isRegularFile(imagePath));
        String image = Base64.getEncoder().encodeToString(Files.readAllBytes(imagePath));
        OnnxRuntimeSessionManager manager = new OnnxRuntimeSessionManager();
        try {
            List<Map<String, Object>> faces = faces(manager, directory, image);

            Map<String, Object> recognition = new ArcFaceRecognitionExecutor(manager).execute(
                    Files.readAllBytes(directory.resolve("w600k_r50.onnx")), image, faces);
            Map<?, ?> recognitionItem = first(recognition);
            List<?> embedding = (List<?>) recognitionItem.get("embedding");
            List<?> normalized = (List<?>) recognitionItem.get("normalizedEmbedding");
            assertEquals(512, embedding.size());
            assertEquals(512, normalized.size());
            assertEquals(1d, norm(normalized), 0.0001d);
            assertTrue(((Map<?, ?>) recognition.get("rawOutputs")).containsKey("683"));

            Map<String, Object> landmark2d = new LandmarkExecutor(manager, false).execute(
                    Files.readAllBytes(directory.resolve("2d106det.onnx")), image, faces);
            assertEquals(106, ((List<?>) first(landmark2d).get("landmarks")).size());
            assertTrue(((Map<?, ?>) landmark2d.get("rawOutputs")).containsKey("fc1"));

            Map<String, Object> landmark3d = new LandmarkExecutor(manager, true).execute(
                    Files.readAllBytes(directory.resolve("1k3d68.onnx")), image, faces);
            assertEquals(68, ((List<?>) first(landmark3d).get("landmarks")).size());
            assertEquals(3, ((List<?>) ((List<?>) first(landmark3d).get("landmarks")).get(0)).size());

            Map<String, Object> genderAge = new GenderAgeExecutor(manager).execute(
                    Files.readAllBytes(directory.resolve("genderage.onnx")), image, faces);
            Map<?, ?> genderAgeItem = first(genderAge);
            assertTrue(((Number) genderAgeItem.get("gender")).intValue() == 0
                    || ((Number) genderAgeItem.get("gender")).intValue() == 1);
            assertTrue(genderAgeItem.get("age") instanceof Number);
        } finally {
            manager.close();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> faces(OnnxRuntimeSessionManager manager, Path directory,
                                                    String image) throws Exception {
        return (List<Map<String, Object>>) (List<?>) new ScrfdFaceDetectionExecutor(manager).execute(
                Files.readAllBytes(directory.resolve("det_10g.onnx")), image,
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
