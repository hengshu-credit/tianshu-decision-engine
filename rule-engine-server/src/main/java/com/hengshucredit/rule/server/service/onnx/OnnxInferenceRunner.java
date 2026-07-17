package com.hengshucredit.rule.server.service.onnx;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class OnnxInferenceRunner {

    private final OnnxRuntimeSessionManager sessionManager;

    public OnnxInferenceRunner(OnnxRuntimeSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public Map<String, Object> run(byte[] modelBytes, Object tensorData) {
        Map<String, Object> nativeOutputs = runNative(modelBytes, tensorData);
        Map<String, Object> outputs = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : nativeOutputs.entrySet()) {
            outputs.put(entry.getKey(), OnnxValueConverter.toJava(entry.getValue()));
        }
        return outputs;
    }

    public Map<String, Object> runNative(byte[] modelBytes, Object tensorData) {
        OrtSession session = sessionManager.session(modelBytes);
        OnnxTensor tensor = null;
        OrtSession.Result result = null;
        try {
            String inputName = session.getInputNames().iterator().next();
            tensor = OnnxTensor.createTensor(sessionManager.getEnvironment(), tensorData);
            result = session.run(Collections.singletonMap(inputName, tensor));
            Map<String, Object> outputs = new LinkedHashMap<>();
            for (Map.Entry<String, OnnxValue> entry : result) {
                outputs.put(entry.getKey(), entry.getValue().getValue());
            }
            return outputs;
        } catch (OrtException e) {
            throw new IllegalArgumentException("ONNX 推理失败: " + e.getMessage(), e);
        } finally {
            if (result != null) result.close();
            if (tensor != null) tensor.close();
        }
    }
}
