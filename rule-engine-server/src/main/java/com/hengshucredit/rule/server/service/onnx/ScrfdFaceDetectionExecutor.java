package com.hengshucredit.rule.server.service.onnx;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ScrfdFaceDetectionExecutor {

    private static final int[] STRIDES = {8, 16, 32};
    private final OnnxInferenceRunner runner;

    public ScrfdFaceDetectionExecutor(OnnxRuntimeSessionManager sessionManager) {
        this.runner = new OnnxInferenceRunner(sessionManager);
    }

    public Map<String, Object> execute(byte[] modelBytes, String imageBase64, OnnxTaskConfig config) {
        Mat image = ImageTensorUtils.decodeBase64(imageBase64);
        try {
            int inputWidth = config.getInt("inputWidth");
            int inputHeight = config.getInt("inputHeight");
            PreparedInput prepared = prepare(image, inputWidth, inputHeight);
            Map<String, Object> nativeOutputs = runner.runNative(modelBytes, prepared.tensor);
            List<FaceRegion> faces = decode(nativeOutputs, inputWidth, inputHeight, prepared.scale,
                    config.getDouble("confidenceThreshold"), config.getDouble("nmsThreshold"));
            Map<String, Object> output = new LinkedHashMap<>();
            List<Map<String, Object>> faceValues = new ArrayList<>();
            for (FaceRegion face : faces) faceValues.add(face.toMap());
            output.put("faces", faceValues);
            output.put("rawOutputs", jsonOutputs(nativeOutputs));
            return output;
        } finally {
            image.release();
        }
    }

    static List<FaceRegion> decode(Map<String, Object> outputs, int inputWidth, int inputHeight,
                                   double scale, double threshold, double nmsThreshold) {
        if (outputs.size() != 9) {
            throw new IllegalArgumentException("SCRFD 模型必须包含 9 个输出节点，实际为 " + outputs.size());
        }
        List<Object> values = new ArrayList<>(outputs.values());
        List<Candidate> candidates = new ArrayList<>();
        for (int level = 0; level < STRIDES.length; level++) {
            int stride = STRIDES[level];
            float[][] scores = matrix(values.get(level));
            float[][] boxes = matrix(values.get(level + 3));
            float[][] landmarks = matrix(values.get(level + 6));
            int gridWidth = inputWidth / stride;
            int expected = (inputHeight / stride) * gridWidth * 2;
            if (scores.length != expected || boxes.length != expected || landmarks.length != expected) {
                throw new IllegalArgumentException("SCRFD stride " + stride + " 输出尺寸与输入尺寸不匹配");
            }
            for (int index = 0; index < scores.length; index++) {
                double score = scores[index][0];
                if (score < threshold) continue;
                int location = index / 2;
                double centerX = (location % gridWidth) * stride;
                double centerY = (location / gridWidth) * stride;
                double x1 = (centerX - boxes[index][0] * stride) / scale;
                double y1 = (centerY - boxes[index][1] * stride) / scale;
                double x2 = (centerX + boxes[index][2] * stride) / scale;
                double y2 = (centerY + boxes[index][3] * stride) / scale;
                List<List<Double>> points = new ArrayList<>();
                for (int point = 0; point < 5; point++) {
                    points.add(Arrays.asList(
                            (centerX + landmarks[index][point * 2] * stride) / scale,
                            (centerY + landmarks[index][point * 2 + 1] * stride) / scale));
                }
                candidates.add(new Candidate(x1, y1, x2, y2, score, points));
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::getScore).reversed());
        List<Candidate> kept = nms(candidates, nmsThreshold);
        List<FaceRegion> faces = new ArrayList<>();
        for (Candidate candidate : kept) {
            faces.add(new FaceRegion(candidate.x1, candidate.y1,
                    candidate.x2 - candidate.x1, candidate.y2 - candidate.y1,
                    candidate.score, candidate.landmarks));
        }
        return faces;
    }

    private static PreparedInput prepare(Mat image, int inputWidth, int inputHeight) {
        double imageRatio = (double) image.rows() / image.cols();
        double modelRatio = (double) inputHeight / inputWidth;
        int resizedWidth;
        int resizedHeight;
        if (imageRatio > modelRatio) {
            resizedHeight = inputHeight;
            resizedWidth = (int) (resizedHeight / imageRatio);
        } else {
            resizedWidth = inputWidth;
            resizedHeight = (int) (resizedWidth * imageRatio);
        }
        double scale = (double) resizedHeight / image.rows();
        Mat resized = new Mat();
        Mat padded = Mat.zeros(inputHeight, inputWidth, CvType.CV_8UC3);
        Mat roi = padded.submat(new Rect(0, 0, resizedWidth, resizedHeight));
        Mat rgb = new Mat();
        try {
            Imgproc.resize(image, resized, new Size(resizedWidth, resizedHeight));
            resized.copyTo(roi);
            Imgproc.cvtColor(padded, rgb, Imgproc.COLOR_BGR2RGB);
            float[][][][] tensor = new float[1][3][inputHeight][inputWidth];
            ImageTensorUtils.copyRgbChw(rgb, tensor[0],
                    new double[]{127.5d, 127.5d, 127.5d},
                    new double[]{128d, 128d, 128d}, 1d);
            return new PreparedInput(tensor, scale);
        } finally {
            rgb.release();
            roi.release();
            padded.release();
            resized.release();
        }
    }

    private static List<Candidate> nms(List<Candidate> values, double threshold) {
        List<Candidate> kept = new ArrayList<>();
        for (Candidate value : values) {
            boolean suppressed = false;
            for (Candidate selected : kept) {
                if (iou(value, selected) > threshold) {
                    suppressed = true;
                    break;
                }
            }
            if (!suppressed) kept.add(value);
        }
        return kept;
    }

    private static double iou(Candidate first, Candidate second) {
        double width = Math.max(0d, Math.min(first.x2, second.x2) - Math.max(first.x1, second.x1) + 1d);
        double height = Math.max(0d, Math.min(first.y2, second.y2) - Math.max(first.y1, second.y1) + 1d);
        double intersection = width * height;
        double firstArea = (first.x2 - first.x1 + 1d) * (first.y2 - first.y1 + 1d);
        double secondArea = (second.x2 - second.x1 + 1d) * (second.y2 - second.y1 + 1d);
        return intersection / (firstArea + secondArea - intersection);
    }

    private static float[][] matrix(Object value) {
        if (value instanceof float[][]) return (float[][]) value;
        if (value instanceof float[][][]) {
            float[][][] batched = (float[][][]) value;
            if (batched.length == 1) return batched[0];
        }
        throw new IllegalArgumentException("SCRFD 输出节点必须为二维浮点数组");
    }

    private static Map<String, Object> jsonOutputs(Map<String, Object> nativeOutputs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : nativeOutputs.entrySet()) {
            values.put(entry.getKey(), OnnxValueConverter.toJava(entry.getValue()));
        }
        return values;
    }

    private static final class PreparedInput {
        private final float[][][][] tensor;
        private final double scale;

        private PreparedInput(float[][][][] tensor, double scale) {
            this.tensor = tensor;
            this.scale = scale;
        }
    }

    private static final class Candidate {
        private final double x1;
        private final double y1;
        private final double x2;
        private final double y2;
        private final double score;
        private final List<List<Double>> landmarks;

        private Candidate(double x1, double y1, double x2, double y2, double score,
                          List<List<Double>> landmarks) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.score = score;
            this.landmarks = landmarks;
        }

        private double getScore() {
            return score;
        }
    }
}
