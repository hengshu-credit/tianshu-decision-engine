package com.hengshucredit.rule.server.service.onnx;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OnnxModelExecutionService {

    private final OnnxRuntimeSessionManager sessionManager;

    public OnnxModelExecutionService(OnnxRuntimeSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public Map<String, Object> execute(byte[] modelBytes, String configJson, Map<String, Object> params) {
        OnnxTaskConfig config = OnnxTaskConfig.parse(configJson);
        String image = image(params);
        switch (config.getTaskType()) {
            case YUNET_FACE_DETECTION:
                Map<String, Object> yunet = new LinkedHashMap<>();
                yunet.put("faces", new YunetFaceDetectionExecutor().detect(modelBytes, image, config));
                return yunet;
            case FACENOX_ANTISPOOF:
                return new FacenoxAntispoofExecutor(sessionManager).execute(modelBytes, image, faces(params));
            case MN3_ANTISPOOF:
                return new Mn3AntispoofExecutor(sessionManager).execute(modelBytes, image, faces(params));
            case SCRFD_FACE_DETECTION:
                return new ScrfdFaceDetectionExecutor(sessionManager).execute(modelBytes, image, config);
            case ARCFACE_RECOGNITION:
                return new ArcFaceRecognitionExecutor(sessionManager).execute(modelBytes, image, faces(params));
            case LANDMARK_2D106:
                return new LandmarkExecutor(sessionManager, false).execute(modelBytes, image, faces(params));
            case LANDMARK_3D68:
                return new LandmarkExecutor(sessionManager, true).execute(modelBytes, image, faces(params));
            case GENDER_AGE:
                return new GenderAgeExecutor(sessionManager).execute(modelBytes, image, faces(params));
            default:
                throw new IllegalArgumentException("不支持的 ONNX 任务类型: " + config.getTaskType());
        }
    }

    private static String image(Map<String, Object> params) {
        Object value = params == null ? null : params.get("image");
        if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
            throw new IllegalArgumentException("ONNX 任务输入 image 不能为空");
        }
        return (String) value;
    }

    private static List<Map<String, Object>> faces(Map<String, Object> params) {
        Object value = params == null ? null : params.get("faces");
        if (!(value instanceof List) || ((List<?>) value).isEmpty()) {
            throw new IllegalArgumentException("ONNX 任务输入 faces 不能为空");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object face : (List<?>) value) {
            if (!(face instanceof Map)) throw new IllegalArgumentException("faces 中的每一项必须为对象");
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) face).entrySet()) {
                copy.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            result.add(copy);
        }
        return result;
    }
}
