package com.hengshucredit.rule.server.service.onnx;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class YunetFaceDetectionExecutor {

    private static final int DIVISOR = 32;
    private static final int[] STRIDES = {8, 16, 32};
    private final OnnxInferenceRunner runner;

    public YunetFaceDetectionExecutor(OnnxRuntimeSessionManager sessionManager) {
        this(sessionManager, OnnxRuntimeConfig.cpu());
    }

    public YunetFaceDetectionExecutor(OnnxRuntimeSessionManager sessionManager,
                                      OnnxRuntimeConfig runtimeConfig) {
        this.runner = new OnnxInferenceRunner(sessionManager, runtimeConfig);
    }

    public List<Map<String, Object>> detect(byte[] modelBytes, String imageBase64, OnnxTaskConfig config) {
        Mat bgr = ImageTensorUtils.decodeBase64(imageBase64);
        try {
            PreparedInput prepared = prepare(bgr, runner.firstInputShape(modelBytes));
            Map<String, Object> outputs = runner.runNative(modelBytes, prepared.tensor);
            return decode(outputs, prepared.imageWidth, prepared.imageHeight,
                    prepared.padWidth, prepared.padHeight,
                    config.getDouble("confidenceThreshold"), config.getDouble("nmsThreshold"),
                    config.getInt("topK"), config.getInt("minFaceSize"), 5,
                    prepared.scaleX, prepared.scaleY);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("YuNet 人脸检测失败: " + e.getMessage(), e);
        } finally {
            bgr.release();
        }
    }

    private static PreparedInput prepare(Mat bgr, long[] inputShape) {
        int imageWidth = bgr.cols();
        int imageHeight = bgr.rows();
        boolean fixedShape = inputShape != null && inputShape.length == 4
                && inputShape[2] > 0 && inputShape[3] > 0;
        int padWidth = fixedShape ? Math.toIntExact(inputShape[3])
                : ((imageWidth - 1) / DIVISOR + 1) * DIVISOR;
        int padHeight = fixedShape ? Math.toIntExact(inputShape[2])
                : ((imageHeight - 1) / DIVISOR + 1) * DIVISOR;
        Mat source = bgr;
        Mat resized = null;
        Mat rgb = new Mat();
        Mat padded = Mat.zeros(padHeight, padWidth, CvType.CV_8UC3);
        if (fixedShape && (imageWidth != padWidth || imageHeight != padHeight)) {
            resized = new Mat();
            Imgproc.resize(bgr, resized, new Size(padWidth, padHeight), 0d, 0d, Imgproc.INTER_LINEAR);
            source = resized;
        }
        Mat roi = padded.submat(new Rect(0, 0, source.cols(), source.rows()));
        try {
            Imgproc.cvtColor(source, rgb, Imgproc.COLOR_BGR2RGB);
            rgb.copyTo(roi);
            float[][][][] tensor = new float[1][3][padHeight][padWidth];
            ImageTensorUtils.copyRgbChw(padded, tensor[0], null, null, 1d);
            double scaleX = fixedShape ? (double) imageWidth / padWidth : 1d;
            double scaleY = fixedShape ? (double) imageHeight / padHeight : 1d;
            return new PreparedInput(tensor, imageWidth, imageHeight, padWidth, padHeight, scaleX, scaleY);
        } finally {
            roi.release();
            padded.release();
            rgb.release();
            if (resized != null) resized.release();
        }
    }

    static List<Map<String, Object>> decode(Map<String, Object> outputs, int imageWidth, int imageHeight,
                                             int padWidth, int padHeight, double confidenceThreshold,
                                             double nmsThreshold, int topK, int minFaceSize, int margin,
                                             double scaleX, double scaleY) {
        List<Candidate> candidates = new ArrayList<>();
        for (int level = 0; level < STRIDES.length; level++) {
            int stride = STRIDES[level];
            float[][] cls = matrix(outputs, "cls_" + stride);
            float[][] obj = matrix(outputs, "obj_" + stride);
            float[][] bbox = matrix(outputs, "bbox_" + stride);
            float[][] kps = matrix(outputs, "kps_" + stride);
            int cols = padWidth / stride;
            int rows = padHeight / stride;
            int expected = rows * cols;
            if (cls.length != expected || obj.length != expected
                    || bbox.length != expected || kps.length != expected) {
                throw new IllegalArgumentException("YuNet stride " + stride + " 输出尺寸与输入尺寸不匹配");
            }
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    int index = row * cols + col;
                    double score = Math.sqrt(clamp(cls[index][0]) * clamp(obj[index][0]));
                    if (score < confidenceThreshold) continue;
                    double centerX = (col + bbox[index][0]) * stride;
                    double centerY = (row + bbox[index][1]) * stride;
                    double width = Math.exp(bbox[index][2]) * stride;
                    double height = Math.exp(bbox[index][3]) * stride;
                    List<List<Double>> landmarks = new ArrayList<>();
                    for (int point = 0; point < 5; point++) {
                        landmarks.add(Arrays.asList(
                                (double) (kps[index][point * 2] + col) * stride * scaleX,
                                (double) (kps[index][point * 2 + 1] + row) * stride * scaleY));
                    }
                    candidates.add(new Candidate((centerX - width / 2d) * scaleX,
                            (centerY - height / 2d) * scaleY,
                            width * scaleX, height * scaleY, score, landmarks));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::getScore).reversed());
        if (topK > 0 && candidates.size() > topK) {
            candidates = new ArrayList<>(candidates.subList(0, topK));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Candidate candidate : nms(candidates, nmsThreshold)) {
            int x = (int) candidate.x;
            int y = (int) candidate.y;
            int width = (int) candidate.width;
            int height = (int) candidate.height;
            if (x < 0 || y < 0 || x + width > imageWidth || y + height > imageHeight) continue;
            if (Math.min(Math.min(x, imageWidth - x - width),
                    Math.min(y, imageHeight - y - height)) < margin) continue;
            if (width < minFaceSize || height < minFaceSize) continue;
            result.add(new FaceRegion(x, y, width, height, candidate.score, candidate.landmarks).toMap());
        }
        return result;
    }

    private static double clamp(double value) {
        return Math.max(0d, Math.min(1d, value));
    }

    private static float[][] matrix(Map<String, Object> outputs, String name) {
        Object value = outputs.get(name);
        if (value instanceof float[][]) return (float[][]) value;
        if (value instanceof float[][][]) {
            float[][][] batched = (float[][][]) value;
            if (batched.length == 1) return batched[0];
        }
        throw new IllegalArgumentException("YuNet 缺少或无法解析输出节点 " + name);
    }

    private static List<Candidate> nms(List<Candidate> candidates, double threshold) {
        List<Candidate> kept = new ArrayList<>();
        for (Candidate candidate : candidates) {
            boolean suppressed = false;
            for (Candidate selected : kept) {
                if (iou(candidate, selected) > threshold) {
                    suppressed = true;
                    break;
                }
            }
            if (!suppressed) kept.add(candidate);
        }
        return kept;
    }

    private static double iou(Candidate first, Candidate second) {
        int firstX = (int) first.x;
        int firstY = (int) first.y;
        int firstWidth = (int) first.width;
        int firstHeight = (int) first.height;
        int secondX = (int) second.x;
        int secondY = (int) second.y;
        int secondWidth = (int) second.width;
        int secondHeight = (int) second.height;
        int overlapWidth = Math.max(0, Math.min(firstX + firstWidth, secondX + secondWidth)
                - Math.max(firstX, secondX));
        int overlapHeight = Math.max(0, Math.min(firstY + firstHeight, secondY + secondHeight)
                - Math.max(firstY, secondY));
        double intersection = (double) overlapWidth * overlapHeight;
        return intersection / ((double) firstWidth * firstHeight + (double) secondWidth * secondHeight - intersection);
    }

    private static final class PreparedInput {
        private final float[][][][] tensor;
        private final int imageWidth;
        private final int imageHeight;
        private final int padWidth;
        private final int padHeight;
        private final double scaleX;
        private final double scaleY;

        private PreparedInput(float[][][][] tensor, int imageWidth, int imageHeight,
                              int padWidth, int padHeight, double scaleX, double scaleY) {
            this.tensor = tensor;
            this.imageWidth = imageWidth;
            this.imageHeight = imageHeight;
            this.padWidth = padWidth;
            this.padHeight = padHeight;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
        }
    }

    private static final class Candidate {
        private final double x;
        private final double y;
        private final double width;
        private final double height;
        private final double score;
        private final List<List<Double>> landmarks;

        private Candidate(double x, double y, double width, double height, double score,
                          List<List<Double>> landmarks) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.score = score;
            this.landmarks = landmarks;
        }

        private double getScore() {
            return score;
        }
    }
}
