package com.hengshucredit.rule.server.service.onnx;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtProvider;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import ai.onnxruntime.providers.OrtCUDAProviderOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OnnxRuntimeSessionManager {

    private static final Logger log = LoggerFactory.getLogger(OnnxRuntimeSessionManager.class);
    private final EnvironmentLoader environmentLoader;
    private volatile OrtEnvironment environment;
    private volatile Throwable environmentFailure;
    private final Map<String, CachedSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> cpuFallbacks = new ConcurrentHashMap<>();

    public OnnxRuntimeSessionManager() {
        this(() -> OrtEnvironment.getEnvironment());
    }

    OnnxRuntimeSessionManager(EnvironmentLoader environmentLoader) {
        this.environmentLoader = environmentLoader;
    }

    @FunctionalInterface
    interface EnvironmentLoader {
        OrtEnvironment load();
    }

    @FunctionalInterface
    interface RuntimeOperation<T> {
        T execute(OnnxRuntimeConfig runtimeConfig);
    }

    public OrtEnvironment getEnvironment() {
        return environment();
    }

    public OrtSession session(byte[] modelBytes) {
        return session(modelBytes, OnnxRuntimeConfig.cpu());
    }

    public OrtSession session(byte[] modelBytes, OnnxRuntimeConfig runtimeConfig) {
        return withCpuFallback(modelBytes, runtimeConfig,
                config -> sessionExact(modelBytes, config));
    }

    <T> T withCpuFallback(byte[] modelBytes, OnnxRuntimeConfig runtimeConfig,
                          RuntimeOperation<T> operation) {
        if (modelBytes == null || modelBytes.length == 0) {
            throw new IllegalArgumentException("ONNX 模型内容为空");
        }
        OnnxRuntimeConfig effectiveConfig = runtimeConfig == null ? OnnxRuntimeConfig.cpu() : runtimeConfig;
        if (!effectiveConfig.isCuda()) return operation.execute(effectiveConfig);
        String fallbackKey = sha256(modelBytes) + ':' + effectiveConfig.cacheKey();
        if (cpuFallbacks.containsKey(fallbackKey)) return operation.execute(OnnxRuntimeConfig.cpu());
        try {
            T cudaResult = operation.execute(effectiveConfig);
            if (cpuFallbacks.containsKey(fallbackKey)) retireSession(modelBytes, effectiveConfig);
            return cudaResult;
        } catch (RuntimeException | LinkageError cudaFailure) {
            try {
                T cpuResult = operation.execute(OnnxRuntimeConfig.cpu());
                String reason = failureMessage(cudaFailure);
                boolean newFallback = cpuFallbacks.putIfAbsent(fallbackKey, reason) == null;
                retireSession(modelBytes, effectiveConfig);
                if (newFallback) {
                    log.warn("ONNX CUDA 执行失败，已自动回退 CPU；修复 GPU 环境后请重启服务。原因: {}", reason);
                }
                return cpuResult;
            } catch (RuntimeException | LinkageError cpuFailure) {
                IllegalArgumentException combined = new IllegalArgumentException(
                        "ONNX CUDA 执行失败且 CPU 自动回退也失败；CUDA: " + failureMessage(cudaFailure)
                                + "；CPU: " + failureMessage(cpuFailure), cpuFailure);
                combined.addSuppressed(cudaFailure);
                throw combined;
            }
        }
    }

    SessionLease acquireSession(byte[] modelBytes, OnnxRuntimeConfig runtimeConfig) {
        if (modelBytes == null || modelBytes.length == 0) {
            throw new IllegalArgumentException("ONNX 模型内容为空");
        }
        OnnxRuntimeConfig effectiveConfig = runtimeConfig == null ? OnnxRuntimeConfig.cpu() : runtimeConfig;
        String key = sha256(modelBytes) + ':' + effectiveConfig.cacheKey();
        synchronized (sessions) {
            sessionExact(modelBytes, effectiveConfig);
            CachedSession cached = sessions.get(key);
            if (cached == null) throw new IllegalStateException("ONNX 会话缓存状态异常");
            return cached.acquire();
        }
    }

    void retireSession(byte[] modelBytes, OnnxRuntimeConfig runtimeConfig) {
        if (modelBytes == null || modelBytes.length == 0 || runtimeConfig == null || !runtimeConfig.isCuda()) return;
        String key = sha256(modelBytes) + ':' + runtimeConfig.cacheKey();
        synchronized (sessions) {
            CachedSession cached = sessions.remove(key);
            if (cached != null) cached.retire();
        }
    }

    OrtSession sessionExact(byte[] modelBytes, OnnxRuntimeConfig runtimeConfig) {
        if (modelBytes == null || modelBytes.length == 0) {
            throw new IllegalArgumentException("ONNX 模型内容为空");
        }
        OnnxRuntimeConfig effectiveConfig = runtimeConfig == null ? OnnxRuntimeConfig.cpu() : runtimeConfig;
        String key = sha256(modelBytes) + ':' + effectiveConfig.cacheKey();
        CachedSession existing = sessions.get(key);
        if (existing != null) return existing.session;
        synchronized (sessions) {
            existing = sessions.get(key);
            if (existing != null) return existing.session;
            SessionResources resources = null;
            try {
                resources = createOptions(effectiveConfig);
                OrtSession created = environment().createSession(modelBytes, resources.options);
                sessions.put(key, new CachedSession(created, resources.options, resources.cudaOptions));
                return created;
            } catch (OrtException | LinkageError e) {
                if (resources != null) resources.close();
                String prefix = effectiveConfig.isCuda()
                        ? "无法使用 CUDA 加载 ONNX 模型，请检查 CUDA、cuDNN 与 ONNX Runtime 版本: "
                        : "无法加载 ONNX 模型: ";
                throw new IllegalArgumentException(prefix + e.getMessage(), e);
            } catch (RuntimeException e) {
                if (resources != null) resources.close();
                throw e;
            }
        }
    }

    private SessionResources createOptions(OnnxRuntimeConfig config) throws OrtException {
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        OrtCUDAProviderOptions cudaOptions = null;
        try {
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            if (config.isCuda()) {
                ensureCudaProviderCompiled();
                cudaOptions = new OrtCUDAProviderOptions(config.getCudaDeviceId());
                if (config.getCudaGpuMemLimitMb() > 0L) {
                    cudaOptions.add("gpu_mem_limit", String.valueOf(config.getCudaGpuMemLimitMb() * 1024L * 1024L));
                }
                cudaOptions.add("arena_extend_strategy", config.getCudaArenaExtendStrategy());
                cudaOptions.add("cudnn_conv_algo_search", config.getCudaCudnnConvAlgoSearch());
                cudaOptions.add("do_copy_in_default_stream", config.isCudaDoCopyInDefaultStream() ? "1" : "0");
                options.addCUDA(cudaOptions);
            }
            return new SessionResources(options, cudaOptions);
        } catch (OrtException | RuntimeException | LinkageError e) {
            if (cudaOptions != null) cudaOptions.close();
            options.close();
            throw e;
        }
    }

    private void ensureCudaProviderCompiled() {
        environment();
        if (!OrtEnvironment.getAvailableProviders().contains(OrtProvider.CUDA)) {
            throw new IllegalArgumentException("当前 ONNX Runtime 未包含 CUDA Execution Provider");
        }
    }

    public Map<String, Object> runtimeCapabilities() {
        Map<String, Object> result = new LinkedHashMap<>();
        EnumSet<OrtProvider> providers;
        try {
            result.put("onnxRuntimeVersion", environment().getVersion());
            providers = OrtEnvironment.getAvailableProviders();
        } catch (RuntimeException | LinkageError e) {
            result.put("onnxRuntimeVersion", null);
            result.put("availableProviders", new ArrayList<>());
            result.put("cudaAvailable", false);
            result.put("cudaError", failureMessage(e));
            result.put("cpuFallbackEnabled", true);
            result.put("activeCpuFallbackCount", cpuFallbacks.size());
            return result;
        }
        List<String> providerNames = new ArrayList<>();
        for (OrtProvider provider : providers) providerNames.add(provider.name());
        result.put("availableProviders", providerNames);
        boolean cudaAvailable = providers.contains(OrtProvider.CUDA);
        String cudaError = null;
        if (cudaAvailable) {
            try (OrtSession.SessionOptions options = new OrtSession.SessionOptions();
                 OrtCUDAProviderOptions cudaOptions = new OrtCUDAProviderOptions(0)) {
                // 追加 provider 时才会加载 CUDA 共享库及其依赖；仅构造 provider options 无法验证环境。
                options.addCUDA(cudaOptions);
            } catch (RuntimeException | LinkageError | OrtException e) {
                cudaAvailable = false;
                cudaError = failureMessage(e);
            }
        } else {
            cudaError = "当前服务使用 CPU 版 ONNX Runtime；如需 CUDA，请使用 -Ponnx-gpu 构建后端";
        }
        result.put("cudaAvailable", cudaAvailable);
        result.put("cudaError", cudaError);
        result.put("cpuFallbackEnabled", true);
        result.put("activeCpuFallbackCount", cpuFallbacks.size());
        return result;
    }

    private OrtEnvironment environment() {
        OrtEnvironment current = environment;
        if (current != null) return current;
        Throwable failure = environmentFailure;
        if (failure != null) {
            throw new IllegalStateException("ONNX Runtime 加载失败: " + failureMessage(failure), failure);
        }
        synchronized (this) {
            if (environment != null) return environment;
            if (environmentFailure != null) {
                throw new IllegalStateException(
                        "ONNX Runtime 加载失败: " + failureMessage(environmentFailure), environmentFailure);
            }
            try {
                environment = environmentLoader.load();
                return environment;
            } catch (RuntimeException | LinkageError e) {
                environmentFailure = e;
                throw new IllegalStateException("ONNX Runtime 加载失败: " + failureMessage(e), e);
            }
        }
    }

    public Map<String, Object> inspect(byte[] modelBytes) {
        try (SessionLease lease = acquireSession(modelBytes, OnnxRuntimeConfig.cpu())) {
            OrtSession session = lease.session();
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

    int getActiveCpuFallbackCount() {
        return cpuFallbacks.size();
    }

    private String failureMessage(Throwable failure) {
        if (failure == null) return "未知错误";
        String message = failure.getMessage();
        return message == null || message.trim().isEmpty() ? failure.getClass().getSimpleName() : message;
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
        close(isJvmShutdownInProgress());
    }

    void close(boolean jvmShutdownInProgress) {
        synchronized (sessions) {
            if (jvmShutdownInProgress) {
                // OrtEnvironment 会注册独立的 JVM shutdown hook。Java 不保证多个 hook 的执行顺序，
                // 此时主动关闭 CUDA session 可能与环境释放并发并触发原生访问冲突；进程退出时交由 OS 回收。
                sessions.clear();
                cpuFallbacks.clear();
                return;
            }
            for (CachedSession session : sessions.values()) {
                session.retire();
            }
            sessions.clear();
            cpuFallbacks.clear();
        }
    }

    private boolean isJvmShutdownInProgress() {
        Thread probe = new Thread(() -> { }, "onnx-shutdown-state-probe");
        try {
            Runtime.getRuntime().addShutdownHook(probe);
            Runtime.getRuntime().removeShutdownHook(probe);
            return false;
        } catch (IllegalStateException | SecurityException e) {
            return true;
        }
    }

    private static final class SessionResources {
        private final OrtSession.SessionOptions options;
        private final OrtCUDAProviderOptions cudaOptions;

        private SessionResources(OrtSession.SessionOptions options, OrtCUDAProviderOptions cudaOptions) {
            this.options = options;
            this.cudaOptions = cudaOptions;
        }

        private void close() {
            if (cudaOptions != null) cudaOptions.close();
            options.close();
        }
    }

    static final class SessionLease implements AutoCloseable {
        private final CachedSession owner;
        private boolean closed;

        private SessionLease(CachedSession owner) {
            this.owner = owner;
        }

        OrtSession session() {
            return owner.session;
        }

        @Override
        public synchronized void close() {
            if (closed) return;
            closed = true;
            owner.release();
        }
    }

    static final class CachedSession {
        private final OrtSession session;
        private final OrtSession.SessionOptions options;
        private final OrtCUDAProviderOptions cudaOptions;
        private final Runnable closeAction;
        private int activeUsers;
        private boolean retired;
        private boolean closed;

        private CachedSession(OrtSession session, OrtSession.SessionOptions options,
                              OrtCUDAProviderOptions cudaOptions) {
            this.session = session;
            this.options = options;
            this.cudaOptions = cudaOptions;
            this.closeAction = this::closeNativeResources;
        }

        CachedSession(Runnable closeAction) {
            this.session = null;
            this.options = null;
            this.cudaOptions = null;
            this.closeAction = closeAction;
        }

        synchronized SessionLease acquire() {
            if (retired || closed) throw new IllegalStateException("ONNX 会话已停止使用");
            activeUsers++;
            return new SessionLease(this);
        }

        synchronized void release() {
            if (activeUsers > 0) activeUsers--;
            closeIfUnused();
        }

        synchronized void retire() {
            retired = true;
            closeIfUnused();
        }

        synchronized boolean isClosed() {
            return closed;
        }

        private void closeIfUnused() {
            if (!retired || activeUsers > 0 || closed) return;
            closed = true;
            try {
                closeAction.run();
            } catch (RuntimeException | LinkageError e) {
                log.warn("释放 ONNX 会话资源失败: {}", failureMessageOf(e));
            }
        }

        private void closeNativeResources() {
            try {
                session.close();
            } catch (OrtException ignored) {
                // 关闭阶段继续释放 provider options。
            }
            if (cudaOptions != null) cudaOptions.close();
            options.close();
        }

        private static String failureMessageOf(Throwable failure) {
            String message = failure.getMessage();
            return message == null || message.trim().isEmpty()
                    ? failure.getClass().getSimpleName() : message;
        }
    }
}
