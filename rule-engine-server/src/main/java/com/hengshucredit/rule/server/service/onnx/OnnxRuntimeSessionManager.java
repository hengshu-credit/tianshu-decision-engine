package com.hengshucredit.rule.server.service.onnx;

import com.hengshucredit.rule.core.engine.ExecutionCapacityException;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtProvider;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import ai.onnxruntime.providers.OrtCUDAProviderOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Service
public class OnnxRuntimeSessionManager {

    private static final Logger log = LoggerFactory.getLogger(OnnxRuntimeSessionManager.class);
    // 8 种任务各保留 CPU 检查和一种运行配置；这是数量护栏，不代表 native/显存字节预算。
    private static final int DEFAULT_MAX_SESSIONS = 16;
    private final EnvironmentLoader environmentLoader;
    private final int maxSessions;
    private volatile OrtEnvironment environment;
    private volatile Throwable environmentFailure;
    private final Map<String, CachedSession> sessions = new LinkedHashMap<>(16, 0.75F, true);
    private final Set<CachedSession> retiredSessions = new HashSet<>();
    private final Map<String, LoadingSession> loadingSessions = new LinkedHashMap<>();
    private final Set<LoadingSession> pendingLoads = new HashSet<>();
    private final Map<String, String> cpuFallbacks = new LinkedHashMap<>(16, 0.75F, true);
    private long capacityRejections;
    private long evictions;
    private boolean closed;
    private boolean jvmShutdown;

    public OnnxRuntimeSessionManager() {
        this(DEFAULT_MAX_SESSIONS);
    }

    @Autowired
    public OnnxRuntimeSessionManager(
            @Value("${rule-engine.onnx.session-cache-max-entries:16}") int maxSessions) {
        this(() -> OrtEnvironment.getEnvironment(), maxSessions);
    }

    OnnxRuntimeSessionManager(EnvironmentLoader environmentLoader) {
        this(environmentLoader, DEFAULT_MAX_SESSIONS);
    }

    OnnxRuntimeSessionManager(EnvironmentLoader environmentLoader, int maxSessions) {
        if (maxSessions <= 0) throw new IllegalArgumentException("ONNX 会话缓存数量上限必须大于 0");
        this.environmentLoader = environmentLoader;
        this.maxSessions = maxSessions;
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

    /** 兼容预热入口；推理和元数据读取必须使用租约，不能长期持有这个裸会话。 */
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
        synchronized (sessions) {
            ensureOpen();
        }
        if (!effectiveConfig.isCuda()) return operation.execute(effectiveConfig);
        String fallbackKey = sha256(modelBytes) + ':' + effectiveConfig.cacheKey();
        if (cpuFallbackReason(fallbackKey) != null) return operation.execute(OnnxRuntimeConfig.cpu());
        try {
            T cudaResult = operation.execute(effectiveConfig);
            if (cpuFallbackReason(fallbackKey) != null) retireSession(modelBytes, effectiveConfig);
            return cudaResult;
        } catch (ExecutionCapacityException | SessionWaitInterruptedException controlFailure) {
            throw controlFailure;
        } catch (RuntimeException | LinkageError cudaFailure) {
            if (!isProviderFailure(cudaFailure)) throw cudaFailure;
            synchronized (sessions) {
                if (closed || Thread.currentThread().isInterrupted()) throw cudaFailure;
            }
            retireSession(modelBytes, effectiveConfig);
            try {
                T cpuResult = operation.execute(OnnxRuntimeConfig.cpu());
                String reason = failureMessage(cudaFailure);
                boolean newFallback = rememberCpuFallback(fallbackKey, reason);
                if (newFallback) {
                    log.warn("ONNX CUDA 执行失败，已自动回退 CPU；回退记录按会话缓存上限保留。原因: {}", reason);
                }
                return cpuResult;
            } catch (ExecutionCapacityException | SessionWaitInterruptedException controlFailure) {
                controlFailure.addSuppressed(cudaFailure);
                throw controlFailure;
            } catch (RuntimeException | LinkageError cpuFailure) {
                IllegalArgumentException combined = new IllegalArgumentException(
                        "ONNX CUDA 执行失败且 CPU 自动回退也失败；CUDA: " + failureMessage(cudaFailure)
                                + "；CPU: " + failureMessage(cpuFailure), cpuFailure);
                combined.addSuppressed(cudaFailure);
                throw combined;
            }
        }
    }

    private boolean isProviderFailure(Throwable failure) {
        String message = failureMessage(failure).toLowerCase(java.util.Locale.ROOT);
        return message.contains("cuda") || message.contains("cudnn")
                || message.contains("cublas") || message.contains("execution provider")
                || message.contains("provider");
    }

    private String cpuFallbackReason(String key) {
        synchronized (sessions) {
            return cpuFallbacks.get(key);
        }
    }

    private boolean rememberCpuFallback(String key, String reason) {
        synchronized (sessions) {
            if (closed) return false;
            boolean added = cpuFallbacks.putIfAbsent(key, reason) == null;
            while (cpuFallbacks.size() > maxSessions) {
                Iterator<String> oldest = cpuFallbacks.keySet().iterator();
                oldest.next();
                oldest.remove();
            }
            return added;
        }
    }

    SessionLease acquireSession(byte[] modelBytes, OnnxRuntimeConfig runtimeConfig) {
        if (modelBytes == null || modelBytes.length == 0) {
            throw new IllegalArgumentException("ONNX 模型内容为空");
        }
        OnnxRuntimeConfig effectiveConfig = runtimeConfig == null ? OnnxRuntimeConfig.cpu() : runtimeConfig;
        String key = sha256(modelBytes) + ':' + effectiveConfig.cacheKey();
        while (true) {
            LoadingSession loading;
            CachedSession evicted = null;
            boolean startsLoading = false;
            synchronized (sessions) {
                ensureOpen();
                CachedSession cached = sessions.get(key);
                if (cached != null) return cached.acquire();
                loading = loadingSessions.get(key);
                if (loading == null) {
                    retiredSessions.removeIf(CachedSession::isClosed);
                    if (loadedSessionCount() >= maxSessions) {
                        evicted = detachIdleSession();
                    } else {
                        loading = new LoadingSession(key);
                        loadingSessions.put(key, loading);
                        pendingLoads.add(loading);
                        startsLoading = true;
                    }
                }
                if (loading != null) loading.waiters++;
            }
            if (evicted != null) {
                // native 关闭期间仍保留退役名额，其他热 key 可以继续获取租约。
                evicted.retire();
                continue;
            }
            if (startsLoading) createAndPublish(loading, modelBytes, effectiveConfig);
            return awaitLease(loading);
        }
    }

    private void createAndPublish(LoadingSession loading, byte[] bytes, OnnxRuntimeConfig config) {
        CachedSession created;
        try {
            created = createCachedSession(bytes, config);
        } catch (Throwable failure) {
            synchronized (sessions) {
                pendingLoads.remove(loading);
                loadingSessions.remove(loading.key, loading);
                loading.future.completeExceptionally(failure);
            }
            return;
        }
        boolean retire;
        boolean skipNativeClose;
        synchronized (sessions) {
            pendingLoads.remove(loading);
            retire = closed || loading.retired || loadingSessions.get(loading.key) != loading;
            skipNativeClose = jvmShutdown;
            loadingSessions.remove(loading.key, loading);
            if (retire) {
                if (!skipNativeClose) retiredSessions.add(created);
                loading.future.completeExceptionally(new IllegalStateException("ONNX 会话创建完成前已退役或关闭"));
            } else {
                sessions.put(loading.key, created);
                // 发布与各等待者取得真实租约之间，临时租约阻止空闲淘汰。
                loading.pin = created.acquire();
                loading.future.complete(created);
            }
        }
        if (retire && !skipNativeClose) created.retire();
    }

    private SessionLease awaitLease(LoadingSession loading) {
        try {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            CachedSession cached = loading.future.get();
            synchronized (sessions) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                ensureOpen();
                if (loading.retired || sessions.get(loading.key) != cached) {
                    throw new IllegalStateException("ONNX 会话已退役，请重新获取");
                }
                return cached.acquire();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SessionWaitInterruptedException(interrupted);
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("ONNX 会话创建失败", cause);
        } finally {
            SessionLease pin = null;
            synchronized (sessions) {
                loading.waiters--;
                if (loading.waiters == 0) {
                    pin = loading.pin;
                    loading.pin = null;
                }
            }
            if (pin != null) pin.close();
        }
    }

    void retireSession(byte[] modelBytes, OnnxRuntimeConfig runtimeConfig) {
        if (modelBytes == null || modelBytes.length == 0 || runtimeConfig == null || !runtimeConfig.isCuda()) return;
        String key = sha256(modelBytes) + ':' + runtimeConfig.cacheKey();
        CachedSession cached;
        synchronized (sessions) {
            cached = sessions.remove(key);
            if (cached != null) retiredSessions.add(cached);
            LoadingSession loading = loadingSessions.remove(key);
            if (loading != null) loading.retire();
        }
        if (cached != null) cached.retire();
    }

    OrtSession sessionExact(byte[] modelBytes, OnnxRuntimeConfig runtimeConfig) {
        try (SessionLease lease = acquireSession(modelBytes, runtimeConfig)) {
            return lease.session();
        }
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("ONNX 会话管理器已关闭");
    }

    private int loadedSessionCount() {
        return sessions.size() + retiredSessions.size() + pendingLoads.size();
    }

    private CachedSession detachIdleSession() {
        Iterator<CachedSession> candidates = sessions.values().iterator();
        while (candidates.hasNext()) {
            CachedSession candidate = candidates.next();
            if (!candidate.isIdle()) continue;
            candidates.remove();
            retiredSessions.add(candidate);
            evictions++;
            return candidate;
        }
        capacityRejections++;
        throw new SessionCapacityException(maxSessions);
    }

    public Map<String, Object> sessionCacheMetrics() {
        synchronized (sessions) {
            retiredSessions.removeIf(CachedSession::isClosed);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("maxEntries", maxSessions);
            result.put("cachedSessions", sessions.size());
            result.put("retiredSessions", retiredSessions.size());
            result.put("loadingSessions", pendingLoads.size());
            result.put("loadedSessions", loadedSessionCount());
            result.put("evictions", evictions);
            result.put("capacityRejections", capacityRejections);
            return result;
        }
    }

    public static final class SessionCapacityException extends ExecutionCapacityException {
        private SessionCapacityException(int maximum) {
            super(Resource.ONNX_SESSION_CACHE, "ONNX 会话数量已达上限（" + maximum
                    + "），当前会话正在加载、使用或等待释放，请稍后重试或调整 rule-engine.onnx.session-cache-max-entries");
        }
    }

    private static final class SessionWaitInterruptedException extends IllegalStateException {
        private SessionWaitInterruptedException(InterruptedException cause) {
            super("等待 ONNX 会话创建被中断", cause);
        }
    }

    private static final class LoadingSession {
        private final String key;
        private final CompletableFuture<CachedSession> future = new CompletableFuture<>();
        private int waiters;
        private SessionLease pin;
        private boolean retired;

        private LoadingSession(String key) { this.key = key; }

        private void retire() {
            retired = true;
            future.completeExceptionally(new IllegalStateException("ONNX 会话创建任务已退役或关闭"));
        }
    }

    CachedSession createCachedSession(byte[] modelBytes, OnnxRuntimeConfig config) {
        SessionResources resources = null;
        try {
            resources = createOptions(config);
            OrtSession created = environment().createSession(modelBytes, resources.options);
            return new CachedSession(created, resources.options, resources.cudaOptions);
        } catch (OrtException | LinkageError e) {
            if (resources != null) resources.close();
            String prefix = config.isCuda()
                    ? "无法使用 CUDA 加载 ONNX 模型，请检查 CUDA、cuDNN 与 ONNX Runtime 版本: "
                    : "无法加载 ONNX 模型: ";
            throw new IllegalArgumentException(prefix + e.getMessage(), e);
        } catch (RuntimeException e) {
            if (resources != null) resources.close();
            throw e;
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
        result.put("sessionCache", sessionCacheMetrics());
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
            result.put("activeCpuFallbackCount", getActiveCpuFallbackCount());
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
        result.put("activeCpuFallbackCount", getActiveCpuFallbackCount());
        return result;
    }

    /**
     * Returns the effective runtime state for one persisted ONNX model/configuration pair
     * without creating a native session.
     */
    public Map<String, Object> runtimeState(String modelDigest, OnnxRuntimeConfig runtimeConfig) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (modelDigest == null || modelDigest.trim().isEmpty() || runtimeConfig == null) {
            result.put("state", "UNKNOWN");
            result.put("effectiveProvider", null);
            result.put("reason", "模型摘要或运行配置缺失");
            return result;
        }
        if (!runtimeConfig.isCuda()) {
            result.put("state", "CPU");
            result.put("effectiveProvider", OnnxRuntimeConfig.CPU);
            result.put("reason", null);
            return result;
        }
        String key = modelDigest.trim() + ':' + runtimeConfig.cacheKey();
        String fallbackReason = cpuFallbackReason(key);
        if (fallbackReason != null) {
            result.put("state", "CPU_FALLBACK");
            result.put("effectiveProvider", OnnxRuntimeConfig.CPU);
            result.put("reason", fallbackReason);
            return result;
        }
        synchronized (sessions) {
            if (sessions.containsKey(key)) {
                result.put("state", "CUDA");
                result.put("effectiveProvider", OnnxRuntimeConfig.CUDA);
                result.put("reason", null);
                return result;
            }
        }
        result.put("state", "NOT_INITIALIZED");
        result.put("effectiveProvider", null);
        result.put("reason", null);
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
        synchronized (sessions) {
            return sessions.size();
        }
    }

    int getActiveCpuFallbackCount() {
        synchronized (sessions) {
            return cpuFallbacks.size();
        }
    }

    boolean usesCpuFallback(byte[] modelBytes, OnnxRuntimeConfig runtimeConfig) {
        if (modelBytes == null || modelBytes.length == 0
                || runtimeConfig == null || !runtimeConfig.isCuda()) {
            return false;
        }
        String key = sha256(modelBytes) + ':' + runtimeConfig.cacheKey();
        return cpuFallbackReason(key) != null;
    }

    public boolean hasCpuFallback(byte[] modelBytes, OnnxRuntimeConfig runtimeConfig) {
        return usesCpuFallback(modelBytes, runtimeConfig);
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
        List<CachedSession> toRetire;
        synchronized (sessions) {
            closed = true;
            jvmShutdown |= jvmShutdownInProgress;
            for (LoadingSession loading : loadingSessions.values()) loading.retire();
            loadingSessions.clear();
            if (jvmShutdownInProgress) {
                // OrtEnvironment 会注册独立的 JVM shutdown hook。Java 不保证多个 hook 的执行顺序，
                // 此时主动关闭 CUDA session 可能与环境释放并发并触发原生访问冲突；进程退出时交由 OS 回收。
                for (CachedSession session : sessions.values()) session.abandonNativeClose();
                for (CachedSession session : retiredSessions) session.abandonNativeClose();
                sessions.clear();
                retiredSessions.clear();
                cpuFallbacks.clear();
                return;
            }
            retiredSessions.addAll(sessions.values());
            toRetire = new ArrayList<>(retiredSessions);
            sessions.clear();
            cpuFallbacks.clear();
        }
        for (CachedSession session : toRetire) session.retire();
        synchronized (sessions) { retiredSessions.removeIf(CachedSession::isClosed); }
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
        private boolean closing;
        private boolean abandoned;
        private volatile boolean closed;

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

        void release() {
            boolean shouldClose;
            synchronized (this) {
                if (activeUsers > 0) activeUsers--;
                shouldClose = markForClose();
            }
            if (shouldClose) closeResources();
        }

        void retire() {
            boolean shouldClose;
            synchronized (this) {
                retired = true;
                shouldClose = markForClose();
            }
            if (shouldClose) closeResources();
        }

        boolean isClosed() {
            return closed;
        }

        synchronized boolean isIdle() {
            return activeUsers == 0 && !retired && !closing && !abandoned;
        }

        synchronized void abandonNativeClose() {
            abandoned = true;
        }

        private boolean markForClose() {
            if (!retired || activeUsers > 0 || closed || closing || abandoned) return false;
            closing = true;
            return true;
        }

        private void closeResources() {
            try {
                closeAction.run();
            } catch (RuntimeException | LinkageError e) {
                log.warn("释放 ONNX 会话资源失败: {}", failureMessageOf(e));
            } finally {
                closed = true;
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
