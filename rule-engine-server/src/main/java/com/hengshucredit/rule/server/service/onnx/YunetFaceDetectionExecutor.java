package com.hengshucredit.rule.server.service.onnx;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class YunetFaceDetectionExecutor {

    private final YunetDetectorCache detectorCache;

    public YunetFaceDetectionExecutor() {
        this(new YunetDetectorCache());
    }

    public YunetFaceDetectionExecutor(YunetDetectorCache detectorCache) {
        this.detectorCache = detectorCache;
    }

    public List<Map<String, Object>> detect(byte[] modelBytes, String imageBase64, OnnxTaskConfig config) {
        Mat bgr = ImageTensorUtils.decodeBase64(imageBase64);
        Mat rgb = new Mat();
        Mat faces = new Mat();
        try {
            Imgproc.cvtColor(bgr, rgb, Imgproc.COLOR_BGR2RGB);
            return detectorCache.withDetector(modelBytes, config, detector -> {
                detector.setInputSize(new Size(rgb.cols(), rgb.rows()));
                detector.detect(rgb, faces);
                return toFaces(faces, rgb.cols(), rgb.rows(), config.getInt("minFaceSize"), 5);
            });
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("YuNet 人脸检测失败: " + e.getMessage(), e);
        } finally {
            faces.release();
            rgb.release();
            bgr.release();
        }
    }

    private List<Map<String, Object>> toFaces(Mat detected, int imageWidth, int imageHeight,
                                               int minFaceSize, int margin) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < detected.rows(); rowIndex++) {
            float[] row = new float[15];
            detected.get(rowIndex, 0, row);
            int x = (int) row[0];
            int y = (int) row[1];
            int width = (int) row[2];
            int height = (int) row[3];
            if (x < 0 || y < 0 || x + width > imageWidth || y + height > imageHeight) continue;
            if (Math.min(Math.min(x, imageWidth - x - width), Math.min(y, imageHeight - y - height)) < margin) continue;
            if (width < minFaceSize || height < minFaceSize) continue;
            List<List<Double>> landmarks = new ArrayList<>();
            for (int point = 0; point < 5; point++) {
                landmarks.add(Arrays.asList((double) row[4 + point * 2], (double) row[5 + point * 2]));
            }
            result.add(new FaceRegion(x, y, width, height, row[14], landmarks).toMap());
        }
        return result;
    }
}
