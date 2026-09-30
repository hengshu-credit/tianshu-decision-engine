package com.hengshucredit.rule.client;

import com.hengshucredit.rule.client.cache.CachedRule;
import com.hengshucredit.rule.client.cache.L1MemoryCache;
import com.hengshucredit.rule.client.auth.ClientAuthConfig;
import com.hengshucredit.rule.client.auth.ClientRequestAuthenticator;
import com.hengshucredit.rule.client.auth.ProjectClientAuthenticationException;
import com.hengshucredit.rule.client.function.ClientFunctionRegistrar;
import com.hengshucredit.rule.client.log.ExecutionLogReporter;
import com.hengshucredit.rule.client.log.HttpLogReporter;
import com.hengshucredit.rule.client.log.NoOpLogReporter;
import com.hengshucredit.rule.client.sync.HttpSyncClient;
import com.hengshucredit.rule.client.sync.RedisSubscriber;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.core.engine.RuleTerminationSignal;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.dto.RuleExperimentExecuteRequest;
import com.hengshucredit.rule.model.dto.RuleExperimentExecuteResult;
import com.hengshucredit.rule.model.dto.RuleExecutionStatus;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class RuleEngineClient {

    private static final Logger log = LoggerFactory.getLogger(RuleEngineClient.class);

    private final RuleEngineClientConfig config;
    private final L1MemoryCache l1Cache;
    private final HttpSyncClient httpSyncClient;
    private final RedisSubscriber redisSubscriber;
    private final QLExpressEngine engine;
    private final ExecutionLogReporter logReporter;
    private final boolean ownsLogReporter;
    private final ClientFunctionRegistrar functionRegistrar;
    private final ClientRuleRuntimeInvoker runtimeRuleInvoker;
    private final ConcurrentHashMap<String, CompletableFuture<CachedRule>> ruleLoads = new ConcurrentHashMap<>();
    private final Object lifecycleLock = new Object();
    private LifecycleState lifecycleState = LifecycleState.STOPPED;
    private ScheduledExecutorService scheduler;

    private RuleEngineClient(RuleEngineClientConfig config, RedisConnectionFactory connectionFactory,
                             ExecutionLogReporter externalReporter, ApplicationContext applicationContext) {
        this.config = config;
        this.engine = new QLExpressEngine();
        this.l1Cache = new L1MemoryCache(config.getL1CacheMaxSize(),
                rule -> rule.setPreparedScript(engine.prepare(rule.getCompiledScript())));
        ClientRequestAuthenticator authenticator = new ClientRequestAuthenticator(
                config.getServerUrl(), config.getHttpTimeoutMs(), resolveAuthConfig(config));
        this.httpSyncClient = new HttpSyncClient(config.getServerUrl(), config.getHttpTimeoutMs(), authenticator);
        this.redisSubscriber = connectionFactory == null ? null
                : new RedisSubscriber(l1Cache, connectionFactory, resolvePushSubscriptionKey(config),
                httpSyncClient::fetchRule);
        this.runtimeRuleInvoker = new ClientRuleRuntimeInvoker(l1Cache, httpSyncClient, engine, config,
                this::getOrLoadRule);
        this.runtimeRuleInvoker.register(engine.getRunner());
        this.functionRegistrar = new ClientFunctionRegistrar(engine, applicationContext, config.getProjectCode());

        if (config.isServerSideExecution() || !config.isLogReportEnabled()) {
            this.logReporter = new NoOpLogReporter();
            this.ownsLogReporter = true;
        } else if (externalReporter != null) {
            this.logReporter = externalReporter;
            this.ownsLogReporter = false;
        } else {
            this.logReporter = new HttpLogReporter(config.getServerUrl(), config.getHttpTimeoutMs(), authenticator,
                    config.getLogBufferSize(), config.getLogBatchSize(), config.getLogFlushIntervalMs());
            this.ownsLogReporter = true;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public void start() {
        synchronized (lifecycleLock) {
            if (lifecycleState == LifecycleState.STARTED) {
                log.debug("RuleEngineClient is already started");
                return;
            }
            lifecycleState = LifecycleState.STARTING;
            startOwnedLogReporter();
            log.info("RuleEngineClient starting: serverUrl={}, appName={}, projectCode={}, logReporter={}",
                    config.getServerUrl(), config.getAppName(), config.getProjectCode(),
                    logReporter.getClass().getSimpleName());
            try {
                if (!config.isServerSideExecution()) {
                    // 本地执行模式才下载规则、函数并订阅 Redis。
                    syncFunctions();
                    fullSync();
                    redisSubscriber.setFunctionRegistrar(functionRegistrar);
                    redisSubscriber.start();
                    scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                        Thread t = new Thread(r, "rule-client-heartbeat");
                        t.setDaemon(true);
                        return t;
                    });
                    scheduler.scheduleAtFixedRate(this::fullSync,
                            config.getHeartbeatIntervalMs(), config.getHeartbeatIntervalMs(), TimeUnit.MILLISECONDS);
                }
                lifecycleState = LifecycleState.STARTED;
                log.info("RuleEngineClient started, {} rules cached", l1Cache.size());
            } catch (ProjectClientAuthenticationException e) {
                rollbackStart();
                throw e;
            } catch (RuntimeException e) {
                rollbackStart();
                throw e;
            }
        }
    }

    public void close() {
        synchronized (lifecycleLock) {
            if (lifecycleState == LifecycleState.STOPPED) {
                closeOwnedLogReporter();
                return;
            }
            lifecycleState = LifecycleState.CLOSING;
            log.info("RuleEngineClient shutting down");
            if (scheduler != null) {
                scheduler.shutdownNow();
                scheduler = null;
            }
            if (redisSubscriber != null) redisSubscriber.stop();
            closeOwnedLogReporter();
            lifecycleState = LifecycleState.STOPPED;
        }
    }

    /**
     * 执行规则，是否采集表达式追踪由 traceEnabled 配置决定（默认开启）。
     */
    public RuleResult execute(String ruleCode, Map<String, Object> params) {
        return doExecute(ruleCode, params);
    }

    public RuleExperimentExecuteResult executeExperiment(String experimentCode,
                                                          RuleExperimentExecuteRequest request) {
        if (!config.isServerSideExecution()) {
            throw new IllegalStateException("分流实验仅支持服务端执行模式");
        }
        return httpSyncClient.executeExperiment(experimentCode, request);
    }

    /** 查询远程执行状态；本地纯计算模式没有跨请求状态。 */
    public RuleExecutionStatus getExecutionStatus(String traceId) {
        if (!config.isServerSideExecution()) {
            throw new IllegalStateException("本地纯计算模式没有服务端执行状态");
        }
        return httpSyncClient.getExecutionStatus(traceId);
    }

    /** 恢复服务端等待中的订单；完成后仍可通过 getExecutionStatus 读取同一 trace 的最终结果。 */
    public RuleResult resumeExecution(String traceId, Map<String, Object> params) {
        if (!config.isServerSideExecution()) {
            throw new IllegalStateException("本地纯计算模式没有服务端恢复状态");
        }
        return httpSyncClient.resumeExecution(traceId, params);
    }

    /**
     * 执行规则，支持传入 Java 对象（DTO / Model / POJO）作为参数。
     * 对象的字段会通过 Fastjson 自动转换为 Map&lt;String, Object&gt; 后注入表达式上下文。
     *
     * @param ruleCode 规则编码
     * @param paramObj Java 对象，字段名即为表达式中的变量名
     */
    @SuppressWarnings("unchecked")
    public RuleResult execute(String ruleCode, Object paramObj) {
        if (paramObj == null) {
            return doExecute(ruleCode, Collections.emptyMap());
        }
        if (paramObj instanceof Map) {
            return doExecute(ruleCode, (Map<String, Object>) paramObj);
        }
        return doExecute(ruleCode, paramObj);
    }

    private RuleResult doExecute(String ruleCode, Map<String, Object> params) {
        if (config.isServerSideExecution()) {
            return httpSyncClient.executeRule(ruleCode, params, config.getAppName(), config.isTraceEnabled());
        }
        long start = System.currentTimeMillis();

        CachedRule cached = getOrLoadRule(ruleCode);
        if (cached == null) {
            RuleResult r = new RuleResult();
            r.setSuccess(false);
            r.setErrorMessage("规则未找到: " + ruleCode);
            return r;
        }
        ensureLocalPureRule(cached);

        String originalInputJson = config.isLogReportEnabled() ? toJsonSafely(params) : null;
        runtimeRuleInvoker.enter(cached, params);
        RuleResult result = new RuleResult();
        try {
            result = engine.execute(cached.getPreparedScript(), params, config.isTraceEnabled(),
                    runtimeRuleInvoker.requestContext());
        } catch (RuleTerminationSignal e) {
            result.setSuccess(true);
            result.setResult(runtimeRuleInvoker.collectTerminationResult());
        } finally {
            result.setExecuteTimeMs(System.currentTimeMillis() - start);
            runtimeRuleInvoker.completeRoot(result);
            runtimeRuleInvoker.exit();
        }

        reportLog(ruleCode, cached, originalInputJson, result, System.currentTimeMillis() - start);
        return result;
    }

    private RuleResult doExecute(String ruleCode, Object params) {
        if (config.isServerSideExecution()) {
            return httpSyncClient.executeRule(ruleCode, params, config.getAppName(), config.isTraceEnabled());
        }
        long start = System.currentTimeMillis();

        CachedRule cached = getOrLoadRule(ruleCode);
        if (cached == null) {
            RuleResult r = new RuleResult();
            r.setSuccess(false);
            r.setErrorMessage("规则未找到: " + ruleCode);
            return r;
        }
        ensureLocalPureRule(cached);

        String originalInputJson = config.isLogReportEnabled() ? toJsonSafely(params) : null;
        runtimeRuleInvoker.enter(cached, params);
        RuleResult result = new RuleResult();
        try {
            result = engine.execute(cached.getPreparedScript(), params, config.isTraceEnabled(),
                    runtimeRuleInvoker.requestContext());
        } catch (RuleTerminationSignal e) {
            result.setSuccess(true);
            result.setResult(runtimeRuleInvoker.collectTerminationResult());
        } finally {
            result.setExecuteTimeMs(System.currentTimeMillis() - start);
            runtimeRuleInvoker.completeRoot(result);
            runtimeRuleInvoker.exit();
        }

        reportLog(ruleCode, cached, originalInputJson, result, System.currentTimeMillis() - start);
        return result;
    }

    private void reportLog(String ruleCode, CachedRule cached, String originalInputJson,
                           RuleResult result, long costMs) {
        if (!config.isLogReportEnabled()) return;
        try {
            RuleExecutionLog entry = new RuleExecutionLog();
            entry.setTraceId(result.getTraceId());
            entry.setRuleCode(ruleCode);
            entry.setProjectCode(cached.getProjectCode());
            entry.setRuleVersion(cached.getVersion());
            entry.setRevisionId(cached.getRevisionId());
            entry.setArtifactDigest(cached.getArtifactDigest());
            entry.setModelType(cached.getModelType());
            entry.setSource("CLIENT");
            entry.setClientAppName(config.getAppName());
            entry.setInputParams(originalInputJson);
            entry.setOutputResult(toJsonSafely(result.getResult()));
            entry.setSuccess(result.isSuccess() ? 1 : 0);
            entry.setErrorMessage(result.getErrorMessage());
            entry.setExecuteTimeMs(costMs);
            if (result.getTraces() != null) {
                entry.setTraceInfo(toJsonSafely(result.getTraces()));
            }
            logReporter.report(Collections.singletonList(entry));
        } catch (Exception e) {
            log.debug("Log report failed: {}", e.getMessage());
        }
    }

    /** 冷启动或缓存淘汰时，同一规则只允许一个线程回源，其余线程共享加载结果。 */
    private CachedRule getOrLoadRule(String ruleCode) {
        CachedRule cached = l1Cache.get(ruleCode);
        if (cached != null) return cached;
        CompletableFuture<CachedRule> created = new CompletableFuture<>();
        CompletableFuture<CachedRule> existing = ruleLoads.putIfAbsent(ruleCode, created);
        if (existing != null) return awaitRuleLoad(existing);
        try {
            // 首次读取后可能已有上一批加载完成，取得加载槽位后必须再次确认。
            CachedRule loaded = l1Cache.get(ruleCode);
            if (loaded == null) {
                loaded = httpSyncClient.fetchRule(ruleCode);
                if (loaded != null) loaded = l1Cache.putAndGet(loaded);
            }
            created.complete(loaded);
            return loaded;
        } catch (Throwable error) {
            created.completeExceptionally(error);
            throw rethrowRuleLoad(error);
        } finally {
            ruleLoads.remove(ruleCode, created);
        }
    }

    private CachedRule awaitRuleLoad(CompletableFuture<CachedRule> future) {
        try {
            return future.join();
        } catch (CompletionException error) {
            throw rethrowRuleLoad(error.getCause() == null ? error : error.getCause());
        }
    }

    private RuntimeException rethrowRuleLoad(Throwable error) {
        if (error instanceof RuntimeException runtime) return runtime;
        if (error instanceof Error fatal) throw fatal;
        return new IllegalStateException("规则回源加载失败", error);
    }

    private String toJsonSafely(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return JSON.toJSONString(value);
        } catch (StackOverflowError e) {
            return "{\"error\":\"JSON_SERIALIZE_STACK_OVERFLOW\"}";
        } catch (Exception e) {
            return "{\"error\":\"JSON_SERIALIZE_FAILED\",\"message\":\"" + escapeJson(e.getMessage()) + "\"}";
        }
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public void refreshRule(String ruleCode) {
        if (config.isServerSideExecution()) {
            throw new IllegalStateException("serverSideExecution=true 时规则由服务端统一管理，不能刷新本地缓存");
        }
        CachedRule rule = httpSyncClient.fetchRule(ruleCode);
        if (rule != null) {
            l1Cache.put(rule);
        }
    }

    public void refreshAll() {
        if (config.isServerSideExecution()) {
            throw new IllegalStateException("serverSideExecution=true 时规则由服务端统一管理，不能刷新本地缓存");
        }
        fullSync();
    }

    private void ensureLocalPureRule(CachedRule cached) {
        if (config.isServerSideExecution() || cached == null || !cached.isRequiresServerExecution()) return;
        String reason = cached.getServerExecutionReason();
        throw new IllegalStateException("规则依赖服务端运行能力，当前为本地纯计算模式；请配置 serverSideExecution=true"
                + (reason == null || reason.isBlank() ? "" : "（" + reason + "）"));
    }

    public CachedRule getRuleInfo(String ruleCode) {
        return l1Cache.get(ruleCode);
    }

    /**
     * 获取内部 QLExpress 引擎实例，用于注册自定义函数等扩展操作
     */
    public QLExpressEngine getEngine() {
        return engine;
    }

    /**
     * 获取客户端函数注册器，用于手动注册函数
     */
    public ClientFunctionRegistrar getFunctionRegistrar() {
        return functionRegistrar;
    }

    private void syncFunctions() {
        if (config.getProjectId() <= 0) return;
        try {
            List<JSONObject> functions = httpSyncClient.fetchFunctions(config.getProjectId());
            if (functions == null) {
                return;
            }
            functionRegistrar.replaceRemoteSnapshot(functions);
            log.info("Function sync completed, {} functions registered", functions.size());
        } catch (ProjectClientAuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Function sync failed: {}", e.getMessage());
        }
    }

    private void fullSync() {
        try {
            List<CachedRule> rules = httpSyncClient.fetchAll();
            if (rules == null) {
                return;
            }
            l1Cache.replaceSnapshot(rules);
            log.debug("Full sync completed, {} rules", rules.size());
        } catch (ProjectClientAuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Full sync failed: {}", e.getMessage());
        }
    }

    private static String resolvePushSubscriptionKey(RuleEngineClientConfig config) {
        if (config.getProjectCode() != null && !config.getProjectCode().trim().isEmpty()) {
            return config.getProjectCode().trim();
        }
        return null;
    }

    private static ClientAuthConfig resolveAuthConfig(RuleEngineClientConfig config) {
        if (config.getAuthConfig() != null) return config.getAuthConfig();
        return config.getToken() == null || config.getToken().isEmpty()
                ? null : ClientAuthConfig.legacyToken(config.getToken());
    }

    private void rollbackStart() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (redisSubscriber != null) redisSubscriber.stop();
        closeOwnedLogReporter();
        lifecycleState = LifecycleState.STOPPED;
    }

    private void closeOwnedLogReporter() {
        if (!ownsLogReporter) return;
        try {
            logReporter.close();
        } catch (Exception e) {
            log.warn("Log reporter close failed: {}", e.getMessage());
        }
    }

    private void startOwnedLogReporter() {
        if (ownsLogReporter) {
            logReporter.start();
        }
    }

    private enum LifecycleState {
        STOPPED, STARTING, STARTED, CLOSING
    }

    public static class Builder {
        private final RuleEngineClientConfig config = new RuleEngineClientConfig();
        private RedisConnectionFactory connectionFactory;
        private ExecutionLogReporter logReporter;
        private ApplicationContext applicationContext;

        public Builder serverUrl(String serverUrl) { config.setServerUrl(serverUrl); return this; }
        public Builder appName(String appName) { config.setAppName(appName); return this; }
        public Builder projectCode(String projectCode) { config.setProjectCode(projectCode); return this; }
        public Builder token(String token) { config.setToken(token); return this; }
        public Builder authConfig(ClientAuthConfig authConfig) { config.setAuthConfig(authConfig); return this; }
        public Builder basicAuth(String username, String password) {
            return authConfig(ClientAuthConfig.basic(username, password));
        }
        public Builder apiKeyAuth(String parameterName, String apiKey, String placement) {
            return authConfig(ClientAuthConfig.apiKey(parameterName, apiKey, placement));
        }
        public Builder hmacAuth(String accessKey, String hmacSecret) {
            return authConfig(ClientAuthConfig.hmac(accessKey, hmacSecret));
        }
        public Builder l1CacheMaxSize(int size) { config.setL1CacheMaxSize(size); return this; }
        public Builder httpTimeoutMs(int ms) { config.setHttpTimeoutMs(ms); return this; }
        public Builder logReportEnabled(boolean enabled) { config.setLogReportEnabled(enabled); return this; }
        public Builder logBufferSize(int size) { config.setLogBufferSize(size); return this; }
        public Builder logBatchSize(int size) { config.setLogBatchSize(size); return this; }
        public Builder logFlushIntervalMs(int ms) { config.setLogFlushIntervalMs(ms); return this; }
        /** 设置项目 ID，启动时自动从服务端同步 JAVA/BEAN/SCRIPT 函数（0 表示不同步） */
        public Builder projectId(long projectId) { config.setProjectId(projectId); return this; }
        /** 设置是否开启表达式追踪，默认 true */
        public Builder traceEnabled(boolean traceEnabled) { config.setTraceEnabled(traceEnabled); return this; }
        /** Execute rules on server; use this for API/DB/LIST external variables. */
        public Builder serverSideExecution(boolean serverSideExecution) { config.setServerSideExecution(serverSideExecution); return this; }

        public Builder connectionFactory(RedisConnectionFactory connectionFactory) {
            this.connectionFactory = connectionFactory;
            return this;
        }

        public Builder logReporter(ExecutionLogReporter logReporter) {
            this.logReporter = logReporter;
            return this;
        }

        /** 设置 Spring ApplicationContext，用于 BEAN 类型函数注册 */
        public Builder applicationContext(ApplicationContext applicationContext) {
            this.applicationContext = applicationContext;
            return this;
        }

        public RuleEngineClient build() {
            if (connectionFactory == null && !config.isServerSideExecution()) {
                throw new IllegalStateException("RedisConnectionFactory is required. " +
                        "Please provide it via builder.connectionFactory(redisConnectionFactory)");
            }
            return new RuleEngineClient(config, connectionFactory, logReporter, applicationContext);
        }
    }
}
