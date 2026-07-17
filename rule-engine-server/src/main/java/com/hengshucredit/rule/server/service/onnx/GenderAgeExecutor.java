package com.hengshucredit.rule.server.service.onnx;

import org.opencv.core.Mat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GenderAgeExecutor {

    private final OnnxInferenceRunner runner;

    public GenderAgeExecutor(OnnxRuntimeSessionManager sessionManager) {
        this.runner = new OnnxInferenceRunner(sessionManager);
    }

    public Map<String, Object> execute(byte[] modelBytes, String imageBase64,
                                       List<Map<String, Object>> faces) {
        List<FaceRegion> regions = FacenoxAntispoofExecutor.regions(faces);
        Mat image = ImageTensorUtils.decodeBase64(imageBase64);
        Map<String, List<Object>> aggregated = new LinkedHashMap<>();
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            for (int index = 0; index < regions.size(); index++) {
                InsightFaceImageUtils.CenteredInput input =
                        InsightFaceImageUtils.centeredTensor(image, regions.get(index), 96);
                Map<String, Object> raw = runner.run(modelBytes, input.getTensor());
                List<?> prediction = InsightFaceExecutorSupport.firstRow(raw);
                if (prediction.size() != 3) throw new IllegalArgumentException("性别年龄模型输出必须为 3 维");
                int gender = number(prediction.get(0)) > number(prediction.get(1)) ? 0 : 1;
                long age = Math.round(number(prediction.get(2)) * 100d);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("faceIndex", index);
                item.put("bbox", regions.get(index).toMap().get("bbox"));
                item.put("gender", gender);
                item.put("age", age);
                item.put("prediction", prediction);
                item.put("rawOutputs", InsightFaceExecutorSupport.itemRaw(raw, aggregated));
                results.add(item);
            }
            return InsightFaceExecutorSupport.output(results, aggregated);
        } finally {
            image.release();
        }
    }

    private static double number(Object value) {
        if (!(value instanceof Number)) throw new IllegalArgumentException("性别年龄输出必须为数值");
        return ((Number) value).doubleValue();
    }
}
