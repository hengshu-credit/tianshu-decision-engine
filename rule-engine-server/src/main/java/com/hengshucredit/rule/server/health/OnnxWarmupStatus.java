package com.hengshucredit.rule.server.health;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OnnxWarmupStatus {

    private OnnxWarmupState state = OnnxWarmupState.NOT_STARTED;
    private int targetCount;
    private int successCount;
    private int failureCount;
    private int cpuFallbackCount;
    private String lastErrorType;
    private final java.util.List<Map<String, Object>> failures = new java.util.ArrayList<>();

    public synchronized void resetForRetry() {
        if (state == OnnxWarmupState.WARMING) throw new IllegalStateException("ONNX 模型仍在预热中");
        state = OnnxWarmupState.NOT_STARTED;
        targetCount = 0;
        successCount = 0;
        failureCount = 0;
        cpuFallbackCount = 0;
        lastErrorType = null;
        failures.clear();
    }

    public synchronized void start(int targets) {
        if (targets < 0) {
            throw new IllegalArgumentException("ONNX warmup target count must not be negative");
        }
        if (state != OnnxWarmupState.NOT_STARTED) {
            throw new IllegalStateException("ONNX warmup has already started");
        }
        targetCount = targets;
        state = OnnxWarmupState.WARMING;
    }

    public synchronized void recordSuccess(boolean cpuFallback) {
        requireWarming();
        successCount++;
        if (cpuFallback) {
            cpuFallbackCount++;
        }
    }

    public synchronized void recordFailure(Throwable failure) {
        requireWarming();
        failureCount++;
        lastErrorType = failure == null ? "UnknownFailure" : failure.getClass().getSimpleName();
    }

    public synchronized void recordFailure(Throwable failure, Long modelId, String modelName, Integer version) {
        recordFailure(failure);
        if (failures.size() < 20) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("modelId", modelId);
            item.put("modelName", modelName == null || modelName.isBlank() ? "模型 #" + modelId : modelName);
            item.put("version", version);
            item.put("errorType", lastErrorType);
            failures.add(java.util.Collections.unmodifiableMap(item));
        }
    }

    public synchronized void fail(Throwable failure) {
        if (state == OnnxWarmupState.READY) {
            throw new IllegalStateException("Ready ONNX warmup cannot transition to failed");
        }
        failureCount = Math.max(1, failureCount);
        lastErrorType = failure == null ? "UnknownFailure" : failure.getClass().getSimpleName();
        state = OnnxWarmupState.FAILED;
    }

    public synchronized void complete() {
        requireWarming();
        state = failureCount == 0 ? OnnxWarmupState.READY : OnnxWarmupState.FAILED;
    }

    public synchronized OnnxWarmupState getState() {
        return state;
    }

    public synchronized Map<String, Object> details() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("state", state.name());
        details.put("targetCount", targetCount);
        details.put("successCount", successCount);
        details.put("failureCount", failureCount);
        details.put("cpuFallbackCount", cpuFallbackCount);
        details.put("failures", java.util.List.copyOf(failures));
        if (lastErrorType != null) {
            details.put("lastErrorType", lastErrorType);
        }
        return details;
    }

    private void requireWarming() {
        if (state != OnnxWarmupState.WARMING) {
            throw new IllegalStateException("ONNX warmup is not in progress");
        }
    }
}
