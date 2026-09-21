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
        this.redisSubscriber = new RedisSubscriber(l1Cache, connectionFactory, resolvePushSubscriptionKey(config),
                httpSyncClient::fetchRule);
        this.runtimeRuleInvoker = new ClientRuleRuntimeInvoker(l1Cache, httpSyncClient, engine, config);
        this.runtimeRuleInvoker.register(engine.getRunner());
        this.functionRegistrar = new ClientFunctionRegistrar(engine, applicationContext, config.getProjectCode());

        if (!config.isLogReportEnabled()) {
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
                // 先完成首次 HTTP 同步，避免认证/配置错误被 Redis 重试掩盖。
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
            redisSubscriber.stop();
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

        CachedRule cached = l1Cache.get(ruleCode);
        if (cached == null) {
            cached = httpSyncClient.fetchRule(ruleCode);
            if (cached != null) {
                l1Cache.put(cached);
            }
        }
        if (cached == null) {
            RuleResult r = new RuleResult();
            r.setSuccess(false);
            r.setErrorMessage("规则未找到: " + ruleCode);
            return r;
        }

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

        CachedRule cached = l1Cache.get(ruleCode);
        if (cached == null) {
            cached = httpSyncClient.fetchRule(ruleCode);
            if (cached != null) {
                l1Cache.put(cached);
            }
        }
        if (cached == null) {
            RuleResult r = new RuleResult();
            r.setSuccess(false);
            r.setErrorMessage("规则未找到: " + ruleCode);
            return r;
        }

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
        CachedRule rule = httpSyncClient.fetchRule(ruleCode);
        if (rule != null) {
            l1Cache.put(rule);
        }
    }

    public void refreshAll() {
        fullSync();
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
        redisSubscriber.stop();
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
            if (connectionFactory == null) {
                throw new IllegalStateException("RedisConnectionFactory is required. " +
                        "Please provide it via builder.connectionFactory(redisConnectionFactory)");
            }
            return new RuleEngineClient(config, connectionFactory, logReporter, applicationContext);
        }
    }
}
