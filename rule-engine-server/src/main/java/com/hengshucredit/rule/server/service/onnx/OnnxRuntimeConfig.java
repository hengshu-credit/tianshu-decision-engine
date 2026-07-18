package com.hengshucredit.rule.server.service.onnx;

import com.alibaba.fastjson.JSONObject;

import java.util.Locale;

public final class OnnxRuntimeConfig {

    public static final String CPU = "CPU";
    public static final String CUDA = "CUDA";
    public static final String DEFAULT_ARENA_EXTEND_STRATEGY = "kNextPowerOfTwo";
    public static final String DEFAULT_CUDNN_CONV_ALGO_SEARCH = "EXHAUSTIVE";

    private final String executionProvider;
    private final int cudaDeviceId;
    private final long cudaGpuMemLimitMb;
    private final String cudaArenaExtendStrategy;
    private final String cudaCudnnConvAlgoSearch;
    private final boolean cudaDoCopyInDefaultStream;

    private OnnxRuntimeConfig(String executionProvider, int cudaDeviceId, long cudaGpuMemLimitMb,
                              String cudaArenaExtendStrategy, String cudaCudnnConvAlgoSearch,
                              boolean cudaDoCopyInDefaultStream) {
        this.executionProvider = executionProvider;
        this.cudaDeviceId = cudaDeviceId;
        this.cudaGpuMemLimitMb = cudaGpuMemLimitMb;
        this.cudaArenaExtendStrategy = cudaArenaExtendStrategy;
        this.cudaCudnnConvAlgoSearch = cudaCudnnConvAlgoSearch;
        this.cudaDoCopyInDefaultStream = cudaDoCopyInDefaultStream;
    }

    public static OnnxRuntimeConfig cpu() {
        return new OnnxRuntimeConfig(CPU, 0, 0L, DEFAULT_ARENA_EXTEND_STRATEGY,
                DEFAULT_CUDNN_CONV_ALGO_SEARCH, true);
    }

    public static OnnxRuntimeConfig from(JSONObject values) {
        JSONObject source = values == null ? new JSONObject() : values;
        String provider = text(source.getString("executionProvider"), CPU).toUpperCase(Locale.ROOT);
        if (!CPU.equals(provider) && !CUDA.equals(provider)) {
            throw new IllegalArgumentException("ONNX 执行设备仅支持 CPU 或 CUDA");
        }
        int deviceId = integer(source, "cudaDeviceId", 0);
        if (deviceId < 0) throw new IllegalArgumentException("CUDA 设备号不能小于 0");
        long memoryLimitMb = longValue(source, "cudaGpuMemLimitMb", 0L);
        if (memoryLimitMb < 0L) throw new IllegalArgumentException("CUDA 显存上限不能小于 0 MB");
        if (memoryLimitMb > Long.MAX_VALUE / 1024L / 1024L) {
            throw new IllegalArgumentException("CUDA 显存上限过大");
        }
        String arenaStrategy = text(source.getString("cudaArenaExtendStrategy"),
                DEFAULT_ARENA_EXTEND_STRATEGY);
        if (!"kNextPowerOfTwo".equals(arenaStrategy) && !"kSameAsRequested".equals(arenaStrategy)) {
            throw new IllegalArgumentException("CUDA 显存扩展策略无效");
        }
        String algorithmSearch = text(source.getString("cudaCudnnConvAlgoSearch"),
                DEFAULT_CUDNN_CONV_ALGO_SEARCH).toUpperCase(Locale.ROOT);
        if (!"EXHAUSTIVE".equals(algorithmSearch)
                && !"HEURISTIC".equals(algorithmSearch)
                && !"DEFAULT".equals(algorithmSearch)) {
            throw new IllegalArgumentException("CUDA cuDNN 卷积算法策略无效");
        }
        boolean copyInDefaultStream = !source.containsKey("cudaDoCopyInDefaultStream")
                || source.getBooleanValue("cudaDoCopyInDefaultStream");
        return new OnnxRuntimeConfig(provider, deviceId, memoryLimitMb, arenaStrategy,
                algorithmSearch, copyInDefaultStream);
    }

    private static String text(String value, String defaultValue) {
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }

    private static int integer(JSONObject values, String key, int defaultValue) {
        if (!values.containsKey(key) || values.get(key) == null) return defaultValue;
        try {
            return values.getInteger(key);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(key + " 必须为整数", e);
        }
    }

    private static long longValue(JSONObject values, String key, long defaultValue) {
        if (!values.containsKey(key) || values.get(key) == null) return defaultValue;
        try {
            return values.getLong(key);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(key + " 必须为整数", e);
        }
    }

    public void applyTo(JSONObject target) {
        if (target == null) throw new IllegalArgumentException("ONNX 配置对象不能为空");
        target.put("executionProvider", executionProvider);
        target.put("cudaDeviceId", cudaDeviceId);
        target.put("cudaGpuMemLimitMb", cudaGpuMemLimitMb);
        target.put("cudaArenaExtendStrategy", cudaArenaExtendStrategy);
        target.put("cudaCudnnConvAlgoSearch", cudaCudnnConvAlgoSearch);
        target.put("cudaDoCopyInDefaultStream", cudaDoCopyInDefaultStream);
    }

    public String cacheKey() {
        if (!isCuda()) return CPU;
        return CUDA + ':' + cudaDeviceId + ':' + cudaGpuMemLimitMb + ':' + cudaArenaExtendStrategy
                + ':' + cudaCudnnConvAlgoSearch + ':' + cudaDoCopyInDefaultStream;
    }

    public String getExecutionProvider() {
        return executionProvider;
    }

    public boolean isCuda() {
        return CUDA.equals(executionProvider);
    }

    public int getCudaDeviceId() {
        return cudaDeviceId;
    }

    public long getCudaGpuMemLimitMb() {
        return cudaGpuMemLimitMb;
    }

    public String getCudaArenaExtendStrategy() {
        return cudaArenaExtendStrategy;
    }

    public String getCudaCudnnConvAlgoSearch() {
        return cudaCudnnConvAlgoSearch;
    }

    public boolean isCudaDoCopyInDefaultStream() {
        return cudaDoCopyInDefaultStream;
    }
}
