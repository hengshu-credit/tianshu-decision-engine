package com.hengshucredit.rule.server.service.onnx;

import org.opencv.core.Mat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LandmarkExecutor {

    private final OnnxInferenceRunner runner;
    private final boolean threeDimensional;

    public LandmarkExecutor(OnnxRuntimeSessionManager sessionManager, boolean threeDimensional) {
        this(sessionManager, threeDimensional, OnnxRuntimeConfig.cpu());
    }

    public LandmarkExecutor(OnnxRuntimeSessionManager sessionManager, boolean threeDimensional,
                            OnnxRuntimeConfig runtimeConfig) {
        this.runner = new OnnxInferenceRunner(sessionManager, runtimeConfig);
        this.threeDimensional = threeDimensional;
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
                        InsightFaceImageUtils.centeredTensor(image, regions.get(index), 192);
                Map<String, Object> raw = runner.run(modelBytes, input.getTensor());
                List<List<Double>> landmarks = decode(InsightFaceExecutorSupport.firstRow(raw),
                        input.getInverse(), threeDimensional ? 3 : 2,
                        threeDimensional ? 68 : 106, 192);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("faceIndex", index);
                item.put("bbox", regions.get(index).toMap().get("bbox"));
                item.put("landmarks", landmarks);
                item.put("rawOutputs", InsightFaceExecutorSupport.itemRaw(raw, aggregated));
                results.add(item);
            }
            return InsightFaceExecutorSupport.output(results, aggregated);
        } finally {
            image.release();
        }
    }

    static List<List<Double>> decode(List<?> values, double[] inverse, int dimension,
                                      int count, int inputSize) {
        if (values.size() % dimension != 0 || values.size() / dimension < count) {
            throw new IllegalArgumentException("关键点模型输出尺寸无效: " + values.size());
        }
        int total = values.size() / dimension;
        int start = total - count;
        double inverseScale = Math.sqrt(inverse[0] * inverse[0] + inverse[1] * inverse[1]);
        List<List<Double>> result = new ArrayList<>();
        for (int point = start; point < total; point++) {
            double x = (number(values.get(point * dimension)) + 1d) * (inputSize / 2d);
            double y = (number(values.get(point * dimension + 1)) + 1d) * (inputSize / 2d);
            double imageX = inverse[0] * x + inverse[1] * y + inverse[2];
            double imageY = inverse[3] * x + inverse[4] * y + inverse[5];
            List<Double> decoded = new ArrayList<>();
            decoded.add(imageX);
            decoded.add(imageY);
            if (dimension == 3) {
                decoded.add(number(values.get(point * dimension + 2)) * (inputSize / 2d) * inverseScale);
            }
            result.add(decoded);
        }
        return result;
    }

    private static double number(Object value) {
        if (!(value instanceof Number)) throw new IllegalArgumentException("关键点输出必须为数值");
        return ((Number) value).doubleValue();
    }
}
