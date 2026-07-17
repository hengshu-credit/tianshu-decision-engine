package com.hengshucredit.rule.server.service.onnx;

import org.opencv.core.Mat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FacenoxAntispoofExecutor {

    private final OnnxInferenceRunner runner;

    public FacenoxAntispoofExecutor(OnnxRuntimeSessionManager sessionManager) {
        this.runner = new OnnxInferenceRunner(sessionManager);
    }

    public Map<String, Object> execute(byte[] modelBytes, String imageBase64, List<Map<String, Object>> faces) {
        List<FaceRegion> regions = regions(faces);
        Mat image = ImageTensorUtils.decodeBase64(imageBase64);
        try {
            Map<String, Object> rawOutputs = runner.run(modelBytes, ImageTensorUtils.facenoxBatch(image, regions));
            List<Map<String, Object>> results = perFaceResults(regions, rawOutputs, "logits");
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("results", results);
            output.put("rawOutputs", rawOutputs);
            return output;
        } finally {
            image.release();
        }
    }

    static List<FaceRegion> regions(List<Map<String, Object>> faces) {
        if (faces == null || faces.isEmpty()) throw new IllegalArgumentException("人脸列表不能为空");
        List<FaceRegion> result = new ArrayList<>();
        for (Map<String, Object> face : faces) result.add(FaceRegion.from(face));
        return result;
    }

    static List<Map<String, Object>> perFaceResults(List<FaceRegion> regions,
                                                     Map<String, Object> rawOutputs, String semanticName) {
        List<Map<String, Object>> results = new ArrayList<>();
        String primaryNode = rawOutputs.keySet().iterator().next();
        List<?> primaryRows = asList(rawOutputs.get(primaryNode), primaryNode);
        if (primaryRows.size() != regions.size()) {
            throw new IllegalArgumentException("ONNX 输出批次与人脸数量不一致");
        }
        for (int i = 0; i < regions.size(); i++) {
            Map<String, Object> itemRaw = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : rawOutputs.entrySet()) {
                List<?> rows = asList(entry.getValue(), entry.getKey());
                itemRaw.put(entry.getKey(), rows.get(i));
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("faceIndex", i);
            item.put("bbox", regions.get(i).toMap().get("bbox"));
            item.put(semanticName, primaryRows.get(i));
            item.put("rawOutputs", itemRaw);
            results.add(item);
        }
        return results;
    }

    static List<?> asList(Object value, String nodeName) {
        if (!(value instanceof List)) throw new IllegalArgumentException("ONNX 节点 " + nodeName + " 输出不是数组");
        return (List<?>) value;
    }
}
