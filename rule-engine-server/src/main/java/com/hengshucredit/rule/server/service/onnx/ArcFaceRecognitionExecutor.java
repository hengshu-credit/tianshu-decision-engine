package com.hengshucredit.rule.server.service.onnx;

import org.opencv.core.Mat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ArcFaceRecognitionExecutor {

    private final OnnxInferenceRunner runner;

    public ArcFaceRecognitionExecutor(OnnxRuntimeSessionManager sessionManager) {
        this(sessionManager, OnnxRuntimeConfig.cpu());
    }

    public ArcFaceRecognitionExecutor(OnnxRuntimeSessionManager sessionManager, OnnxRuntimeConfig runtimeConfig) {
        this.runner = new OnnxInferenceRunner(sessionManager, runtimeConfig);
    }

    public Map<String, Object> execute(byte[] modelBytes, String imageBase64,
                                       List<Map<String, Object>> faces) {
        List<FaceRegion> regions = FacenoxAntispoofExecutor.regions(faces);
        Mat image = ImageTensorUtils.decodeBase64(imageBase64);
        Map<String, List<Object>> aggregated = new LinkedHashMap<>();
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            for (int index = 0; index < regions.size(); index++) {
                Map<String, Object> raw = runner.run(modelBytes,
                        InsightFaceImageUtils.arcFaceTensor(image, regions.get(index)));
                List<?> embedding = InsightFaceExecutorSupport.firstRow(raw);
                List<Double> normalized = normalize(embedding);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("faceIndex", index);
                item.put("bbox", regions.get(index).toMap().get("bbox"));
                item.put("embedding", embedding);
                item.put("normalizedEmbedding", normalized);
                item.put("rawOutputs", InsightFaceExecutorSupport.itemRaw(raw, aggregated));
                results.add(item);
            }
            return InsightFaceExecutorSupport.output(results, aggregated);
        } finally {
            image.release();
        }
    }

    private static List<Double> normalize(List<?> embedding) {
        double norm = 0d;
        for (Object value : embedding) {
            double number = ((Number) value).doubleValue();
            norm += number * number;
        }
        norm = Math.sqrt(norm);
        if (norm == 0d) throw new IllegalArgumentException("ArcFace 特征向量范数不能为 0");
        List<Double> result = new ArrayList<>();
        for (Object value : embedding) result.add(((Number) value).doubleValue() / norm);
        return result;
    }
}
