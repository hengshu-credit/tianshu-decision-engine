package com.hengshucredit.rule.server.service.onnx;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.List;

final class InsightFaceImageUtils {

    private static final double[][] ARCFACE_DESTINATION = {
            {38.2946d, 51.6963d}, {73.5318d, 51.5014d}, {56.0252d, 71.7366d},
            {41.5493d, 92.3655d}, {70.7299d, 92.2041d}
    };

    private InsightFaceImageUtils() {
    }

    static float[][][][] arcFaceTensor(Mat image, FaceRegion face) {
        List<List<Double>> landmarks = face.getLandmarks();
        if (landmarks.size() != 5) throw new IllegalArgumentException("ArcFace 识别必须提供 5 个关键点");
        Mat transform = similarityTransform(landmarks, ARCFACE_DESTINATION);
        Mat aligned = new Mat();
        Mat rgb = new Mat();
        try {
            Imgproc.warpAffine(image, aligned, transform, new Size(112, 112),
                    Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT);
            Imgproc.cvtColor(aligned, rgb, Imgproc.COLOR_BGR2RGB);
            float[][][][] tensor = new float[1][3][112][112];
            ImageTensorUtils.copyRgbChw(rgb, tensor[0],
                    new double[]{127.5d, 127.5d, 127.5d},
                    new double[]{127.5d, 127.5d, 127.5d}, 1d);
            return tensor;
        } finally {
            rgb.release();
            aligned.release();
            transform.release();
        }
    }

    static CenteredInput centeredTensor(Mat image, FaceRegion face, int size) {
        double scale = size / (Math.max(face.getWidth(), face.getHeight()) * 1.5d);
        double centerX = face.getX() + face.getWidth() / 2d;
        double centerY = face.getY() + face.getHeight() / 2d;
        Mat transform = new Mat(2, 3, CvType.CV_64F);
        transform.put(0, 0, scale, 0d, size / 2d - centerX * scale,
                0d, scale, size / 2d - centerY * scale);
        Mat inverse = new Mat();
        Imgproc.invertAffineTransform(transform, inverse);
        Mat aligned = new Mat();
        Mat rgb = new Mat();
        try {
            Imgproc.warpAffine(image, aligned, transform, new Size(size, size),
                    Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT);
            Imgproc.cvtColor(aligned, rgb, Imgproc.COLOR_BGR2RGB);
            float[][][][] tensor = new float[1][3][size][size];
            ImageTensorUtils.copyRgbChw(rgb, tensor[0], null, null, 1d);
            double[] inverseValues = new double[6];
            inverse.get(0, 0, inverseValues);
            return new CenteredInput(tensor, inverseValues);
        } finally {
            rgb.release();
            aligned.release();
            inverse.release();
            transform.release();
        }
    }

    private static Mat similarityTransform(List<List<Double>> source, double[][] destination) {
        Mat equations = new Mat(10, 4, CvType.CV_64F);
        Mat targets = new Mat(10, 1, CvType.CV_64F);
        for (int i = 0; i < 5; i++) {
            if (source.get(i).size() < 2) throw new IllegalArgumentException("人脸关键点坐标无效");
            double x = source.get(i).get(0);
            double y = source.get(i).get(1);
            equations.put(i * 2, 0, x, -y, 1d, 0d);
            equations.put(i * 2 + 1, 0, y, x, 0d, 1d);
            targets.put(i * 2, 0, destination[i][0]);
            targets.put(i * 2 + 1, 0, destination[i][1]);
        }
        Mat solution = new Mat();
        try {
            if (!Core.solve(equations, targets, solution, Core.DECOMP_SVD)) {
                throw new IllegalArgumentException("无法根据 5 个关键点完成人脸对齐");
            }
            double[] values = new double[4];
            solution.get(0, 0, values);
            Mat transform = new Mat(2, 3, CvType.CV_64F);
            transform.put(0, 0, values[0], -values[1], values[2],
                    values[1], values[0], values[3]);
            return transform;
        } finally {
            solution.release();
            targets.release();
            equations.release();
        }
    }

    static final class CenteredInput {
        private final float[][][][] tensor;
        private final double[] inverse;

        private CenteredInput(float[][][][] tensor, double[] inverse) {
            this.tensor = tensor;
            this.inverse = inverse;
        }

        float[][][][] getTensor() {
            return tensor;
        }

        double[] getInverse() {
            return inverse.clone();
        }
    }
}
