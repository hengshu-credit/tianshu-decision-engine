package com.hengshucredit.rule.core.function;

import nu.pattern.OpenCV;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/** 图像坐标和通道算子。模型模板、裁剪策略及概率阈值由调用脚本传入。 */
public final class ImageGeometryFunctions {
    private static final long MAX_PIXELS = 16_000_000L;
    private static final long MAX_TENSOR_PIXELS = 1_048_576L;

    public static Mat decodeBase64(String encoded) {
        if (encoded == null || encoded.isBlank()) throw new IllegalArgumentException("图片 Base64 不能为空");
        String value = encoded.trim();
        int comma = value.indexOf(',');
        if (value.startsWith("data:") && comma >= 0) value = value.substring(comma + 1);
        final byte[] bytes;
        try {
            bytes = Base64.getMimeDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("图片 Base64 格式无效", e);
        }
        OpenCV.loadLocally();
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

    public Map<String,Integer> imageDimensions(String image) {
        Mat decoded = decodeBase64(image);
        try {
            return Map.of("width", decoded.cols(), "height", decoded.rows());
        } finally {
            decoded.release();
        }
    }

    public List<Double> pointSimilarityAffine(Object source, Object target) {
        if (!(source instanceof List<?> src) || !(target instanceof List<?> dst)
                || src.size() < 2 || src.size() != dst.size()) throw new IllegalArgumentException("源点和目标点必须成对且至少有两对");
        double sx = 0, sy = 0, tx = 0, ty = 0;
        for (int i = 0; i < src.size(); i++) {
            sx += coord(src.get(i), 0) / src.size();
            sy += coord(src.get(i), 1) / src.size();
            tx += coord(dst.get(i), 0) / dst.size();
            ty += coord(dst.get(i), 1) / dst.size();
        }
        double dot = 0, cross = 0, norm = 0;
        for (int i = 0; i < src.size(); i++) {
            double x = coord(src.get(i), 0) - sx, y = coord(src.get(i), 1) - sy;
            double u = coord(dst.get(i), 0) - tx, v = coord(dst.get(i), 1) - ty;
            dot += x * u + y * v;
            cross += x * v - y * u;
            norm += x * x + y * y;
        }
        double a = dot / norm, b = cross / norm;
        if (!Double.isFinite(a * a + b * b) || !(a * a + b * b > 0)) throw new IllegalArgumentException("对应点退化，无法求相似变换");
        return List.of(a, -b, tx - a * sx + b * sy, b, a, ty - b * sx - a * sy);
    }

    private double coord(Object point, int index) {
        if (!(point instanceof List<?> xy) || xy.size() != 2) throw new IllegalArgumentException("坐标必须是二维列表");
        return number(xy.get(index));
    }

    private static double number(Object value) {
        if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue())) throw new IllegalArgumentException("需要有限数值");
        return n.doubleValue();
    }

    private static int integer(Object value) {
        double n = number(value);
        if (n < 0 || n > Integer.MAX_VALUE || n != Math.floor(n)) throw new IllegalArgumentException("图像尺寸或位置应为非负整数");
        return (int)n;
    }

    private static Size size(Object value, long maxPixels) {
        if (!(value instanceof Map<?,?> map)) throw new IllegalArgumentException("尺寸需要 width、height");
        int width = integer(map.get("width")), height = integer(map.get("height"));
        if (width == 0 || height == 0 || (long)width * height > maxPixels) throw new IllegalArgumentException("图像尺寸超出算子范围");
        return new Size(width, height);
    }

    private static Mat matrix(Object value) {
        if (!(value instanceof List<?> values) || values.size() != 6) throw new IllegalArgumentException("仿射矩阵必须包含六个有限数值");
        double[] coefficients = values.stream().mapToDouble(ImageGeometryFunctions::number).toArray();
        Mat result = new Mat(2, 3, CvType.CV_64F);
        result.put(0, 0, coefficients);
        return result;
    }

    public double imageAffineCoverage(Object inputSize, Object affine, Object outputSize) {
        Size source = size(inputSize, MAX_PIXELS), target = size(outputSize, MAX_TENSOR_PIXELS);
        OpenCV.loadLocally();
        Mat transform = matrix(affine);
        Mat mask = Mat.ones(source, CvType.CV_32F), result = new Mat();
        try {
            Imgproc.warpAffine(mask, result, transform, target, Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, Scalar.all(0));
            return Core.mean(result).val[0];
        } finally {
            result.release();
            mask.release();
            transform.release();
        }
    }

    public List<Object> imageTensorChw(String image, Object options) {
        Size output = size(options, MAX_TENSOR_PIXELS);
        Map<?,?> config = (Map<?,?>) options;
        Object order = config.get("order");
        if (!"RGB".equals(order) && !"BGR".equals(order)) throw new IllegalArgumentException("通道顺序应为 RGB 或 BGR");
        double offset = number(config.get("offset")), divisor = number(config.get("divisor"));
        if (divisor == 0) throw new IllegalArgumentException("像素归一化除数不能为零");
        if (config.containsKey("crop") && config.containsKey("affine")) throw new IllegalArgumentException("请选择裁剪或仿射变换");
        Mat decoded = decodeBase64(image), result = new Mat();
        try {
            if (config.containsKey("affine")) {
                Mat transform = matrix(config.get("affine"));
                try {
                    int border = Boolean.TRUE.equals(config.get("replicate")) ? Core.BORDER_REPLICATE : Core.BORDER_CONSTANT;
                    Imgproc.warpAffine(decoded, result, transform, output, Imgproc.INTER_LINEAR, border, Scalar.all(0));
                } finally { transform.release(); }
            } else if (config.get("crop") instanceof Map<?,?> box) {
                Size extent = size(box, MAX_PIXELS);
                int x = integer(box.get("x")), y = integer(box.get("y"));
                if (x + extent.width > decoded.cols() || y + extent.height > decoded.rows()) throw new IllegalArgumentException("裁剪框超出原图");
                Mat region = decoded.submat(new Rect(x, y, (int)extent.width, (int)extent.height));
                try { Imgproc.resize(region, result, output); } finally { region.release(); }
            } else {
                if (config.containsKey("crop")) throw new IllegalArgumentException("裁剪框需要 x、y、width、height");
                Imgproc.resize(decoded, result, output);
            }
            return channels(result, "RGB".equals(order), offset, divisor);
        } finally {
            result.release();
            decoded.release();
        }
    }

    private List<Object> channels(Mat image, boolean rgb, double offset, double divisor) {
        int width = image.cols(), height = image.rows();
        byte[] pixels = new byte[width * height * 3];
        image.get(0, 0, pixels);
        List<Object> channels = new ArrayList<>(3);
        for (int c = 0; c < 3; c++) {
            int channel = rgb ? 2 - c : c;
            List<Object> rows = new ArrayList<>(height);
            for (int y = 0; y < height; y++) {
                List<Number> row = new ArrayList<>(width);
                for (int x = 0; x < width; x++) {
                    int pixel = Byte.toUnsignedInt(pixels[(y * width + x) * 3 + channel]);
                    if (offset == 0 && divisor == 1) row.add(pixel);
                    else row.add((float)((pixel - offset) / divisor));
                }
                rows.add(row);
            }
            channels.add(rows);
        }
        return channels;
    }
}
