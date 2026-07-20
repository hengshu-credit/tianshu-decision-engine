package com.hengshucredit.rule.core.function;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Base64;

/** 模型图片入参函数：统一将 Base64、Data URI 或 HTTP(S) URL 转为规范 Base64。 */
public class ImageInputFunctions {

    public static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private static final String USER_AGENT = "Tianshu-Decision-Engine/1.0";

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

    private byte[] download(String value, int timeoutMs) {
        HttpURLConnection connection = null;
        long deadline = System.currentTimeMillis() + timeoutMs;
        try {
            URL url = new URL(value);
            String protocol = url.getProtocol();
            if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
                throw new IllegalArgumentException("图片地址仅支持 HTTP(S) URL");
            }
            connection = (HttpURLConnection) url.openConnection();
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
}
