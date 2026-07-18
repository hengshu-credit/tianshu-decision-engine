package com.hengshucredit.rule.server.service.onnx;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class OnnxInferenceRunner {

    private final OnnxRuntimeSessionManager sessionManager;
    private final OnnxRuntimeConfig runtimeConfig;

    public OnnxInferenceRunner(OnnxRuntimeSessionManager sessionManager) {
        this(sessionManager, OnnxRuntimeConfig.cpu());
    }

    public OnnxInferenceRunner(OnnxRuntimeSessionManager sessionManager, OnnxRuntimeConfig runtimeConfig) {
        this.sessionManager = sessionManager;
        this.runtimeConfig = runtimeConfig == null ? OnnxRuntimeConfig.cpu() : runtimeConfig;
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
        return sessionManager.withCpuFallback(modelBytes, runtimeConfig,
                config -> runNativeExact(modelBytes, tensorData, config));
    }

    private Map<String, Object> runNativeExact(byte[] modelBytes, Object tensorData,
                                                OnnxRuntimeConfig effectiveConfig) {
        try (OnnxRuntimeSessionManager.SessionLease lease =
                     sessionManager.acquireSession(modelBytes, effectiveConfig)) {
            OrtSession session = lease.session();
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

    public long[] firstInputShape(byte[] modelBytes) {
        return sessionManager.withCpuFallback(modelBytes, runtimeConfig,
                config -> firstInputShapeExact(modelBytes, config));
    }

    private long[] firstInputShapeExact(byte[] modelBytes, OnnxRuntimeConfig effectiveConfig) {
        try (OnnxRuntimeSessionManager.SessionLease lease =
                     sessionManager.acquireSession(modelBytes, effectiveConfig)) {
            OrtSession session = lease.session();
            String inputName = session.getInputNames().iterator().next();
            if (!(session.getInputInfo().get(inputName).getInfo() instanceof TensorInfo)) {
                throw new IllegalArgumentException("ONNX 首个输入节点不是 Tensor");
            }
            return ((TensorInfo) session.getInputInfo().get(inputName).getInfo()).getShape();
        } catch (OrtException e) {
            throw new IllegalArgumentException("读取 ONNX 输入尺寸失败: " + e.getMessage(), e);
        }
    }
}
