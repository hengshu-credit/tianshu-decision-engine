package com.hengshucredit.rule.core.function;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

/** 模型图片入参函数：统一将 Base64、Data URI 或 HTTP(S) URL 转为规范 Base64。 */
public class ImageInputFunctions {

    public static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private static final String USER_AGENT = "Tianshu-Decision-Engine/1.0";
    private final HttpConnectionFactory connectionFactory;

    public ImageInputFunctions() {
        this(url -> (HttpURLConnection) url.openConnection());
    }

    ImageInputFunctions(HttpConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory");
    }

    public String imageToBase64(String image, double timeoutMs) {
        if (image == null || image.trim().isEmpty()) {
            throw new IllegalArgumentException("图片内容不能为空");
        }
        int timeout = timeout(timeoutMs);
        String value = image.trim();
        if (isHttpUrl(value)) {
            return Base64.getEncoder().encodeToString(download(value, timeout));
        }
        if (value.regionMatches(true, 0, "data:", 0, 5)) {
            int comma = value.indexOf(',');
            if (comma < 0 || !value.substring(0, comma).toLowerCase().contains(";base64")) {
                throw new IllegalArgumentException("图片 Data URI 必须使用 base64 编码");
            }
            value = value.substring(comma + 1);
        } else if (value.contains("://")) {
            throw new IllegalArgumentException("图片地址仅支持 HTTP(S) URL，不支持本地路径或其他协议");
        }
        return canonicalBase64(value);
    }

    /**
     * 可选的文件处理入口。options 支持 format、maxBytes、maxDimension、width、height、zip、zipName；
     * options 为空时保持原始文件内容，不做预处理。
     */
    public String imageToBase64WithOptions(String image, double timeoutMs, Object options) {
        byte[] bytes = Base64.getDecoder().decode(imageToBase64(image, timeoutMs));
        Map<?, ?> config = options instanceof Map<?, ?> map ? map : Map.of();
        int maxBytes = positiveInt(config.get("maxBytes"), MAX_IMAGE_BYTES, "文件大小上限");
        int maxDimension = positiveInt(config.get("maxDimension"), 0, "图片分辨率上限");
        int width = positiveInt(config.get("width"), 0, "图片宽度");
        int height = positiveInt(config.get("height"), 0, "图片高度");
        String format = text(config.get("format"));
        if (maxDimension > 0 || width > 0 || height > 0 || !format.isBlank()) {
            bytes = transformImage(bytes, format, maxDimension, width, height);
        }
        if (Boolean.TRUE.equals(config.get("zip")) || "ZIP_BASE64".equalsIgnoreCase(format)) {
            bytes = zip(bytes, text(config.get("zipName")).isBlank() ? "document.bin" : text(config.get("zipName")));
        }
        if (bytes.length > maxBytes) throw new IllegalArgumentException("处理后文件超过 " + maxBytes + " 字节");
        return Base64.getEncoder().encodeToString(bytes);
    }

    private byte[] transformImage(byte[] bytes, String format, int maxDimension, int width, int height) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
            if (source == null) throw new IllegalArgumentException("文件内容不是支持的图片格式");
            if (maxDimension > 0 && (source.getWidth() > maxDimension || source.getHeight() > maxDimension)) {
                throw new IllegalArgumentException("图片宽高不能超过 " + maxDimension + " 像素");
            }
            if (width <= 0 && height <= 0 && format.isBlank()) return bytes;
            int targetWidth = width > 0 ? width : Math.max(1, (int) Math.round(source.getWidth() * (height / (double) source.getHeight())));
            int targetHeight = height > 0 ? height : Math.max(1, (int) Math.round(source.getHeight() * (width / (double) source.getWidth())));
            if (width <= 0 && height <= 0) { targetWidth = source.getWidth(); targetHeight = source.getHeight(); }
            String targetFormat = format.isBlank() || "ORIGINAL".equalsIgnoreCase(format) ? "png" : format.toLowerCase(java.util.Locale.ROOT);
            if (!Set.of("png", "jpg", "jpeg", "webp").contains(targetFormat)) throw new IllegalArgumentException("图片格式仅支持 png、jpg、jpeg 或 webp");
            BufferedImage target = new BufferedImage(targetWidth, targetHeight, targetFormat.equals("jpg") || targetFormat.equals("jpeg") ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = target.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
            graphics.dispose();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(target, targetFormat, output)) throw new IllegalArgumentException("当前运行环境不支持图片格式 " + targetFormat);
            return output.toByteArray();
        } catch (IOException error) {
            throw new IllegalArgumentException("图片预处理失败: " + error.getMessage(), error);
        }
    }

    private static int positiveInt(Object value, int defaultValue, String label) {
        if (value == null || String.valueOf(value).isBlank()) return defaultValue;
        try { int number = Integer.parseInt(String.valueOf(value)); if (number < 1 || number > MAX_IMAGE_BYTES) throw new IllegalArgumentException(label + "必须在 1 字节到 10 MB 之间"); return number; }
        catch (NumberFormatException error) { throw new IllegalArgumentException(label + "必须是整数", error); }
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }

    private static byte[] zip(byte[] bytes, String name) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(output)) {
            ZipEntry entry = new ZipEntry(name); entry.setTime(0); zip.putNextEntry(entry); zip.write(bytes); zip.closeEntry(); zip.finish(); return output.toByteArray();
        } catch (IOException error) { throw new IllegalArgumentException("文件压缩失败", error); }
    }

    private byte[] download(String value, int timeoutMs) {
        HttpURLConnection connection = null;
        long deadline = System.currentTimeMillis() + timeoutMs;
        try {
            URL url = new URL(value);
            String protocol = url.getProtocol();
            if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
                throw new IllegalArgumentException("图片地址仅支持 HTTP(S) URL");
            }
            connection = connectionFactory.open(url);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.connect();

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalArgumentException("图片下载失败，HTTP 状态码 " + status);
            }
            long contentLength = connection.getContentLengthLong();
            if (contentLength > MAX_IMAGE_BYTES) {
                throw new IllegalArgumentException("图片大小不能超过 10 MB");
            }
            int remaining = remainingMillis(deadline);
            connection.setReadTimeout(remaining);
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream(
                         contentLength > 0 ? (int) Math.min(contentLength, MAX_IMAGE_BYTES) : 8192)) {
                byte[] buffer = new byte[8192];
                int total = 0;
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read == 0) continue;
                    total += read;
                    if (total > MAX_IMAGE_BYTES) {
                        throw new IllegalArgumentException("图片大小不能超过 10 MB");
                    }
                    output.write(buffer, 0, read);
                    if (System.currentTimeMillis() > deadline) {
                        throw new IllegalArgumentException("图片下载超时（" + timeoutMs + " ms）");
                    }
                }
                return output.toByteArray();
            }
        } catch (SocketTimeoutException e) {
            throw new IllegalArgumentException("图片下载超时（" + timeoutMs + " ms）", e);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalArgumentException("图片下载失败: " + e.getMessage(), e);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String canonicalBase64(String value) {
        try {
            byte[] decoded = Base64.getMimeDecoder().decode(value);
            if (decoded.length > MAX_IMAGE_BYTES) {
                throw new IllegalArgumentException("图片大小不能超过 10 MB");
            }
            return Base64.getEncoder().encodeToString(decoded);
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().contains("10 MB")) throw e;
            throw new IllegalArgumentException("图片必须是有效 Base64 或 HTTP(S) URL", e);
        }
    }

    private int timeout(double timeoutMs) {
        if (Double.isNaN(timeoutMs) || Double.isInfinite(timeoutMs)
                || timeoutMs < 100D || timeoutMs > 300000D) {
            throw new IllegalArgumentException("图片下载超时必须在 100 到 300000 毫秒之间");
        }
        return (int) Math.round(timeoutMs);
    }

    private int remainingMillis(long deadline) {
        long remaining = deadline - System.currentTimeMillis();
        if (remaining <= 0L) throw new IllegalArgumentException("图片下载超时");
        return (int) Math.min(Integer.MAX_VALUE, remaining);
    }

    private boolean isHttpUrl(String value) {
        return value.regionMatches(true, 0, "http://", 0, 7)
                || value.regionMatches(true, 0, "https://", 0, 8);
    }

    @FunctionalInterface
    interface HttpConnectionFactory {
        HttpURLConnection open(URL url) throws IOException;
    }
}
