package com.hengshucredit.rule.server.service.onnx;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ScrfdFaceDetectionExecutorTest {

    @Test
    public void decodesDistancesLandmarksAndSuppressesOverlappingBoxes() {
        Map<String, Object> outputs = emptyOutputs();
        ((float[][]) outputs.get("score8"))[0][0] = 0.95f;
        ((float[][]) outputs.get("score8"))[1][0] = 0.90f;
        ((float[][]) outputs.get("bbox8"))[0] = new float[]{1f, 1f, 3f, 3f};
        ((float[][]) outputs.get("bbox8"))[1] = new float[]{1f, 1f, 3f, 3f};
        ((float[][]) outputs.get("kps8"))[0] = new float[]{0f, 0f, 1f, 0f, 0.5f, 1f, 0f, 2f, 1f, 2f};
        ((float[][]) outputs.get("kps8"))[1] = ((float[][]) outputs.get("kps8"))[0].clone();

        List<FaceRegion> faces = ScrfdFaceDetectionExecutor.decode(outputs, 32, 32, 1d, 0.5d, 0.4d);

        assertEquals(1, faces.size());
        FaceRegion face = faces.get(0);
        assertEquals(-8d, face.getX(), 0.0001d);
        assertEquals(-8d, face.getY(), 0.0001d);
        assertEquals(32d, face.getWidth(), 0.0001d);
        assertEquals(32d, face.getHeight(), 0.0001d);
        assertEquals(5, face.getLandmarks().size());
        assertEquals(0.95d, face.getConfidence(), 0.0001d);
    }

    @Test
    public void detectsFaceWithRealBuffaloDetectorAndPreservesRawNodes() throws Exception {
        byte[] model = OnnxTestAssets.read("onnx/buffalo_l/det_10g.onnx");
        OnnxRuntimeSessionManager manager = new OnnxRuntimeSessionManager();
        try {
            Map<String, Object> output = new ScrfdFaceDetectionExecutor(manager).execute(
                    model, OnnxTestAssets.imageBase64(),
                    OnnxTaskConfig.parse("{\"onnxTaskType\":\"SCRFD_FACE_DETECTION\"}"));

            List<?> faces = (List<?>) output.get("faces");
            assertEquals(1, faces.size());
            assertEquals(5, ((List<?>) ((Map<?, ?>) faces.get(0)).get("landmarks")).size());
            Map<?, ?> rawOutputs = (Map<?, ?>) output.get("rawOutputs");
            assertEquals(9, rawOutputs.size());
            assertTrue(rawOutputs.containsKey("448"));
        } finally {
            manager.close();
        }
    }

    private static Map<String, Object> emptyOutputs() {
        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put("score8", new float[32][1]);
        outputs.put("score16", new float[8][1]);
        outputs.put("score32", new float[2][1]);
        outputs.put("bbox8", new float[32][4]);
        outputs.put("bbox16", new float[8][4]);
        outputs.put("bbox32", new float[2][4]);
        outputs.put("kps8", new float[32][10]);
        outputs.put("kps16", new float[8][10]);
        outputs.put("kps32", new float[2][10]);
        return outputs;
    }
}
