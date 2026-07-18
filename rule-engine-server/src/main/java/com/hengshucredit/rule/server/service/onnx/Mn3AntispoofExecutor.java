package com.hengshucredit.rule.server.service.onnx;

import org.opencv.core.Mat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Mn3AntispoofExecutor {

    private final OnnxInferenceRunner runner;

    public Mn3AntispoofExecutor(OnnxRuntimeSessionManager sessionManager) {
        this(sessionManager, OnnxRuntimeConfig.cpu());
    }

    public Mn3AntispoofExecutor(OnnxRuntimeSessionManager sessionManager, OnnxRuntimeConfig runtimeConfig) {
        this.runner = new OnnxInferenceRunner(sessionManager, runtimeConfig);
    }

    public Map<String, Object> execute(byte[] modelBytes, String imageBase64, List<Map<String, Object>> faces) {
        List<FaceRegion> regions = FacenoxAntispoofExecutor.regions(faces);
        Mat image = ImageTensorUtils.decodeBase64(imageBase64);
        Map<String, List<Object>> aggregated = new LinkedHashMap<>();
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            for (int i = 0; i < regions.size(); i++) {
                Map<String, Object> raw = runner.run(modelBytes, ImageTensorUtils.mn3Batch(image, regions.get(i)));
                Map<String, Object> itemRaw = new LinkedHashMap<>();
                Object probabilities = null;
                for (Map.Entry<String, Object> entry : raw.entrySet()) {
                    List<?> batch = FacenoxAntispoofExecutor.asList(entry.getValue(), entry.getKey());
                    if (batch.size() != 1) throw new IllegalArgumentException("MN3 单脸输出批次必须为 1");
                    Object row = batch.get(0);
                    aggregated.computeIfAbsent(entry.getKey(), key -> new ArrayList<>()).add(row);
                    itemRaw.put(entry.getKey(), row);
                    if (probabilities == null) probabilities = row;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("faceIndex", i);
                item.put("bbox", regions.get(i).toMap().get("bbox"));
                item.put("probabilities", probabilities);
                item.put("rawOutputs", itemRaw);
                results.add(item);
            }
            Map<String, Object> rawOutputs = new LinkedHashMap<>();
            rawOutputs.putAll(aggregated);
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("results", results);
            output.put("rawOutputs", rawOutputs);
            return output;
        } finally {
            image.release();
        }
    }
}
