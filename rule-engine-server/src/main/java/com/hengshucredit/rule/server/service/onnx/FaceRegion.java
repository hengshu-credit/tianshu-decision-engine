package com.hengshucredit.rule.server.service.onnx;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FaceRegion {

    private final double x;
    private final double y;
    private final double width;
    private final double height;
    private final double confidence;
    private final List<List<Double>> landmarks;

    public FaceRegion(double x, double y, double width, double height, double confidence,
                      List<List<Double>> landmarks) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.confidence = confidence;
        this.landmarks = landmarks == null ? new ArrayList<>() : new ArrayList<>(landmarks);
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getWidth() { return width; }
    public double getHeight() { return height; }
    public double getConfidence() { return confidence; }
    public List<List<Double>> getLandmarks() { return new ArrayList<>(landmarks); }

    public Map<String, Object> toMap() {
        Map<String, Object> bbox = new LinkedHashMap<>();
        bbox.put("x", x);
        bbox.put("y", y);
        bbox.put("width", width);
        bbox.put("height", height);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bbox", bbox);
        result.put("landmarks", getLandmarks());
        result.put("confidence", confidence);
        return result;
    }

    public static FaceRegion from(Object value) {
        if (!(value instanceof Map)) throw new IllegalArgumentException("人脸信息必须为对象");
        Map<?, ?> face = (Map<?, ?>) value;
        Object bboxValue = face.get("bbox");
        if (!(bboxValue instanceof Map)) throw new IllegalArgumentException("人脸信息缺少 bbox 对象");
        Map<?, ?> bbox = (Map<?, ?>) bboxValue;
        return new FaceRegion(number(bbox.get("x")), number(bbox.get("y")),
                number(bbox.get("width")), number(bbox.get("height")),
                number(face.containsKey("confidence") ? face.get("confidence") : face.get("score")),
                landmarks(face.get("landmarks")));
    }

    private static double number(Object value) {
        if (value == null) return 0d;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("人脸坐标必须为数值: " + value, e);
        }
    }

    private static List<List<Double>> landmarks(Object value) {
        List<List<Double>> result = new ArrayList<>();
        if (!(value instanceof Iterable)) return result;
        for (Object pointValue : (Iterable<?>) value) {
            if (!(pointValue instanceof Iterable)) continue;
            List<Double> point = new ArrayList<>();
            for (Object coordinate : (Iterable<?>) pointValue) point.add(number(coordinate));
            result.add(point);
        }
        return result;
    }
}
