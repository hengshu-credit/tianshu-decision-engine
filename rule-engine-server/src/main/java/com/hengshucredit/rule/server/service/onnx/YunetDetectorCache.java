package com.hengshucredit.rule.server.service.onnx;

import org.opencv.core.MatOfByte;
import org.opencv.core.Size;
import org.opencv.objdetect.FaceDetectorYN;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class YunetDetectorCache {

    private final Map<String, CachedDetector> detectors = new ConcurrentHashMap<>();

    public void preload(byte[] modelBytes, OnnxTaskConfig config) {
        detector(modelBytes, config);
    }

    public <T> T withDetector(byte[] modelBytes, OnnxTaskConfig config, DetectorCallback<T> callback) {
        CachedDetector cached = detector(modelBytes, config);
        synchronized (cached) {
            return callback.apply(cached.detector);
        }
    }

    public int getCachedDetectorCount() {
        return detectors.size();
    }

    private CachedDetector detector(byte[] modelBytes, OnnxTaskConfig config) {
        if (modelBytes == null || modelBytes.length == 0) {
            throw new IllegalArgumentException("ONNX 模型内容为空");
        }
        String key = key(modelBytes, config);
        CachedDetector existing = detectors.get(key);
        if (existing != null) return existing;
        synchronized (detectors) {
            existing = detectors.get(key);
            if (existing != null) return existing;
            CachedDetector created = new CachedDetector(create(modelBytes, config));
            detectors.put(key, created);
            return created;
        }
    }

    private FaceDetectorYN create(byte[] modelBytes, OnnxTaskConfig config) {
        ImageTensorUtils.ensureLoaded();
        MatOfByte modelBuffer = new MatOfByte(modelBytes);
        MatOfByte configBuffer = new MatOfByte();
        try {
            return FaceDetectorYN.create("ONNX", modelBuffer, configBuffer, new Size(320, 320),
                    (float) config.getDouble("confidenceThreshold"),
                    (float) config.getDouble("nmsThreshold"), config.getInt("topK"));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("无法加载 YuNet ONNX 模型: " + e.getMessage(), e);
        } finally {
            configBuffer.release();
            modelBuffer.release();
        }
    }

    private String key(byte[] modelBytes, OnnxTaskConfig config) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(modelBytes);
            digest.update(config.toJson().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : digest.digest()) result.append(String.format("%02x", value & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }

    public interface DetectorCallback<T> {
        T apply(FaceDetectorYN detector);
    }

    private static class CachedDetector {
        private final FaceDetectorYN detector;

        private CachedDetector(FaceDetectorYN detector) {
            this.detector = detector;
        }
    }
}
