package com.hengshucredit.rule.server.service.onnx;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OnnxRuntimeSessionManager {

    private final OrtEnvironment environment = OrtEnvironment.getEnvironment();
    private final Map<String, OrtSession> sessions = new ConcurrentHashMap<>();

    public OrtEnvironment getEnvironment() {
        return environment;
    }

    public OrtSession session(byte[] modelBytes) {
        if (modelBytes == null || modelBytes.length == 0) {
            throw new IllegalArgumentException("ONNX 模型内容为空");
        }
        String key = sha256(modelBytes);
        OrtSession existing = sessions.get(key);
        if (existing != null) return existing;
        synchronized (sessions) {
            existing = sessions.get(key);
            if (existing != null) return existing;
            try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
                options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
                OrtSession created = environment.createSession(modelBytes, options);
                sessions.put(key, created);
                return created;
            } catch (OrtException e) {
                throw new IllegalArgumentException("无法加载 ONNX 模型: " + e.getMessage(), e);
            }
        }
    }

    public Map<String, Object> inspect(byte[] modelBytes) {
        OrtSession session = session(modelBytes);
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("inputs", nodeMetadata(session.getInputInfo()));
            result.put("outputs", nodeMetadata(session.getOutputInfo()));
            return result;
        } catch (OrtException e) {
            throw new IllegalArgumentException("读取 ONNX 节点元数据失败: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> nodeMetadata(Map<String, NodeInfo> nodes) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, NodeInfo> entry : nodes.entrySet()) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            if (entry.getValue().getInfo() instanceof TensorInfo) {
                TensorInfo info = (TensorInfo) entry.getValue().getInfo();
                metadata.put("type", info.type.toString());
                metadata.put("shape", OnnxValueConverter.shape(info.getShape()));
            } else {
                metadata.put("type", entry.getValue().getInfo().getClass().getSimpleName());
            }
            result.put(entry.getKey(), metadata);
        }
        return result;
    }

    public int getCachedSessionCount() {
        return sessions.size();
    }

    private String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) result.append(String.format("%02x", value & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }

    @PreDestroy
    public void close() {
        synchronized (sessions) {
            for (OrtSession session : sessions.values()) {
                try {
                    session.close();
                } catch (Exception ignored) {
                    // 关闭阶段继续释放其他会话。
                }
            }
            sessions.clear();
        }
    }
}
