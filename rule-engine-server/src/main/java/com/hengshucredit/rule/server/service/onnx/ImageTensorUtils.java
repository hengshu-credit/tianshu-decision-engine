package com.hengshucredit.rule.server.service.onnx;

import nu.pattern.OpenCV;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.util.Base64;
import java.util.List;

public final class ImageTensorUtils {

    static {
        OpenCV.loadLocally();
    }

    private ImageTensorUtils() {
    }

    public static Mat decodeBase64(String encoded) {
        if (encoded == null || encoded.trim().isEmpty()) {
            throw new IllegalArgumentException("图片 Base64 不能为空");
        }
        String value = encoded.trim();
        int comma = value.indexOf(',');
        if (value.startsWith("data:") && comma >= 0) value = value.substring(comma + 1);
        final byte[] bytes;
        try {
            bytes = Base64.getMimeDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("图片 Base64 格式无效", e);
        }
        MatOfByte buffer = new MatOfByte(bytes);
        try {
            Mat image = Imgcodecs.imdecode(buffer, Imgcodecs.IMREAD_COLOR);
            if (image.empty()) {
                image.release();
                throw new IllegalArgumentException("Base64 内容不是有效图片");
            }
            return image;
        } finally {
            buffer.release();
        }
    }

    public static Mat expandedSquareCrop(Mat image, FaceRegion face, double expansionFactor) {
        if (image == null || image.empty()) throw new IllegalArgumentException("图片不能为空");
        if (face == null || face.getWidth() <= 0d || face.getHeight() <= 0d) {
            throw new IllegalArgumentException("人脸 bbox 尺寸无效");
        }
        int cropSize = (int) (Math.max(face.getWidth(), face.getHeight()) * expansionFactor);
        if (cropSize <= 0) throw new IllegalArgumentException("扩展后人脸尺寸无效");
        double centerX = face.getX() + face.getWidth() / 2d;
        double centerY = face.getY() + face.getHeight() / 2d;
        int x = (int) (centerX - cropSize / 2d);
        int y = (int) (centerY - cropSize / 2d);
        int x1 = Math.max(0, x);
        int y1 = Math.max(0, y);
        int x2 = Math.min(image.cols(), x + cropSize);
        int y2 = Math.min(image.rows(), y + cropSize);
        if (x2 <= x1 || y2 <= y1) throw new IllegalArgumentException("人脸 bbox 不在图片范围内");

        Mat region = image.submat(new Rect(x1, y1, x2 - x1, y2 - y1));
        Mat result = new Mat();
        try {
            Core.copyMakeBorder(region, result,
                    Math.max(0, -y), Math.max(0, y + cropSize - image.rows()),
                    Math.max(0, -x), Math.max(0, x + cropSize - image.cols()),
                    Core.BORDER_REFLECT_101);
        } finally {
            region.release();
        }
        if (result.rows() != cropSize || result.cols() != cropSize) {
            result.release();
            throw new IllegalArgumentException("扩展人脸裁剪尺寸不一致");
        }
        return result;
    }

    public static float[][][][] facenoxBatch(Mat bgrImage, List<FaceRegion> faces) {
        if (faces == null || faces.isEmpty()) throw new IllegalArgumentException("人脸列表不能为空");
        Mat rgbImage = new Mat();
        Imgproc.cvtColor(bgrImage, rgbImage, Imgproc.COLOR_BGR2RGB);
        try {
            float[][][][] batch = new float[faces.size()][3][128][128];
            for (int i = 0; i < faces.size(); i++) {
                Mat crop = expandedSquareCrop(rgbImage, faces.get(i), 1.5d);
                Mat resized = letterbox(crop, 128);
                crop.release();
                try {
                    copyRgbChw(resized, batch[i], null, null, 1d / 255d);
                } finally {
                    resized.release();
                }
            }
            return batch;
        } finally {
            rgbImage.release();
        }
    }

    public static float[][][][] mn3Batch(Mat bgrImage, FaceRegion face) {
        Mat crop = directCrop(bgrImage, face);
        Mat resized = new Mat();
        Mat rgb = new Mat();
        try {
            Imgproc.resize(crop, resized, new Size(128, 128), 0d, 0d, Imgproc.INTER_CUBIC);
            Imgproc.cvtColor(resized, rgb, Imgproc.COLOR_BGR2RGB);
            float[][][][] batch = new float[1][3][128][128];
            copyRgbChw(rgb, batch[0],
                    new double[]{0.5931d, 0.4690d, 0.4229d},
                    new double[]{0.2471d, 0.2214d, 0.2157d}, 1d / 255d);
            return batch;
        } finally {
            rgb.release();
            resized.release();
            crop.release();
        }
    }

    public static Mat directCrop(Mat image, FaceRegion face) {
        int x1 = Math.max(0, (int) face.getX());
        int y1 = Math.max(0, (int) face.getY());
        int x2 = Math.min(image.cols(), (int) (face.getX() + face.getWidth()));
        int y2 = Math.min(image.rows(), (int) (face.getY() + face.getHeight()));
        if (x2 <= x1 || y2 <= y1) throw new IllegalArgumentException("人脸 bbox 不在图片范围内");
        Mat view = image.submat(new Rect(x1, y1, x2 - x1, y2 - y1));
        try {
            return view.clone();
        } finally {
            view.release();
        }
    }

    private static Mat letterbox(Mat image, int size) {
        double ratio = (double) size / Math.max(image.rows(), image.cols());
        int height = (int) (image.rows() * ratio);
        int width = (int) (image.cols() * ratio);
        Mat resized = new Mat();
        Imgproc.resize(image, resized, new Size(width, height), 0d, 0d,
                ratio > 1d ? Imgproc.INTER_LANCZOS4 : Imgproc.INTER_AREA);
        int top = (size - height) / 2;
        int bottom = size - height - top;
        int left = (size - width) / 2;
        int right = size - width - left;
        Mat result = new Mat();
        Core.copyMakeBorder(resized, result, top, bottom, left, right, Core.BORDER_REFLECT_101);
        resized.release();
        return result;
    }

    public static void copyRgbChw(Mat rgb, float[][][] output, double[] mean, double[] std, double scale) {
        Mat floatImage = new Mat();
        rgb.convertTo(floatImage, CvType.CV_32FC3, scale);
        try {
            float[] pixel = new float[3];
            for (int y = 0; y < rgb.rows(); y++) {
                for (int x = 0; x < rgb.cols(); x++) {
                    floatImage.get(y, x, pixel);
                    for (int c = 0; c < 3; c++) {
                        double value = pixel[c];
                        if (mean != null) value -= mean[c];
                        if (std != null) value /= std[c];
                        output[c][y][x] = (float) value;
                    }
                }
            }
        } finally {
            floatImage.release();
        }
    }
}
