package com.hengshucredit.rule.client.sync;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.client.cache.CachedRule;
import com.hengshucredit.rule.client.cache.L1MemoryCache;
import com.hengshucredit.rule.client.function.ClientFunctionRegistrar;
import com.hengshucredit.rule.model.dto.RulePushMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;

import java.util.Arrays;
import java.util.Collections;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Redis订阅器 - 使用Spring Data Redis实现
 * 支持规则推送（PUBLISH/UNPUBLISH）和函数推送（FUNC_UPDATE/FUNC_DELETE）
 *
 * 断线重连机制（Spring Data Redis 2.3 兼容）：
 * 1. 启动时指数退避重试（最多3次，间隔1s/2s/4s）
 * 2. 后台守护线程定期检测连接存活，每30s一次
 * 3. 检测到订阅失效时自动重建整个监听容器
 */
public class RedisSubscriber {

    private static final Logger log = LoggerFactory.getLogger(RedisSubscriber.class);

    /** 启动重试次数 */
    private static final int MAX_STARTUP_RETRIES = 3;
    /** 守护线程检测间隔（ms） */
    private static final long HEALTH_CHECK_INTERVAL_MS = 30_000;
    /** 守护线程重连等待时间（ms） */
    private static final long RECONNECT_WAIT_MS = 5_000;

    private final L1MemoryCache cache;
    private final RedisConnectionFactory connectionFactory;
    private final String projectCode;
    private final String channel;
    private final List<Topic> topics;
    private final Function<String, CachedRule> ruleFetcher;
    private static final int MAX_PROCESSED_OPERATIONS = 8192;
    private final Set<String> processedOperations = ConcurrentHashMap.newKeySet();
    private final Deque<String> processedOperationOrder = new ArrayDeque<>();
    private final Object processedOperationsLock = new Object();
    private final ConcurrentHashMap<String, OperationLock> operationLocks = new ConcurrentHashMap<>();

    private RedisMessageListenerContainer container;
    private ClientFunctionRegistrar functionRegistrar;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger reconnectCount = new AtomicInteger(0);
    private Thread healthCheckThread;

    public RedisSubscriber(L1MemoryCache cache, RedisConnectionFactory connectionFactory, String projectCode) {
        this(cache, connectionFactory, projectCode, null);
    }

    public RedisSubscriber(L1MemoryCache cache, RedisConnectionFactory connectionFactory, String projectCode,
                           Function<String, CachedRule> ruleFetcher) {
        this.cache = cache;
        this.connectionFactory = connectionFactory;
        this.projectCode = trimToNull(projectCode);
        this.channel = this.projectCode == null ? null : "rule:push:" + this.projectCode;
        this.ruleFetcher = ruleFetcher;
        this.topics = this.channel == null
                ? Collections.<Topic>singletonList(new ChannelTopic("rule:push:broadcast"))
                : Arrays.<Topic>asList(new ChannelTopic(channel), new ChannelTopic("rule:push:broadcast"));
        if (this.projectCode == null) {
            log.warn("projectCode is not configured; project Redis channel subscription is disabled");
        }
    }

    /**
     * 设置函数注册器，用于处理 FUNC_UPDATE 推送
     */
    public void setFunctionRegistrar(ClientFunctionRegistrar functionRegistrar) {
        this.functionRegistrar = functionRegistrar;
    }

    /**
     * 启动Redis订阅（带指数退避重试）
     */
    public void start() {
        if (projectCode == null) {
            // 项目级推送必须按真实 projectCode 隔离；没有项目编码时不订阅广播频道。
            log.warn("Redis subscriber skipped because projectCode is not configured");
            return;
        }
        if (connectionFactory == null) {
            log.warn("Redis subscriber skipped because RedisConnectionFactory is not configured");
            return;
        }
        if (running.compareAndSet(false, true)) {
            doStartWithRetry();
            startHealthCheck();
        }
    }

    private void doStartWithRetry() {
        int attempts = 0;
        long delay = 1_000;

        while (attempts < MAX_STARTUP_RETRIES) {
            try {
                doStart();
                log.info("Redis subscriber started successfully (attempt {})", attempts + 1);
                return;
            } catch (Exception e) {
                attempts++;
                log.warn("Redis subscriber start attempt {} failed: {}. Retrying in {}ms...",
                        attempts, e.getMessage(), delay);
                if (attempts < MAX_STARTUP_RETRIES) {
                    try { Thread.sleep(delay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
                    delay = Math.min(delay * 2, 10_000);
                } else {
                    log.error("Redis subscriber failed to start after {} attempts: {}",
                            MAX_STARTUP_RETRIES, e.getMessage());
                }
            }
        }
    }

    private synchronized void doStart() {
        if (!running.get()) {
            return;
        }
        if (connectionFactory == null) throw new IllegalStateException("RedisConnectionFactory 未配置");
        stopContainer();
        container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(new RuleMessageListener(), topics);
        container.afterPropertiesSet();
        container.start();
        reconnectCount.set(0);
    }

    /**
     * 停止Redis订阅
     */
    public synchronized void stop() {
        running.set(false);
        stopContainer();
        stopHealthCheck();
        log.info("Redis subscriber stopped");
    }

    private void stopContainer() {
        if (container != null) {
            try {
                container.stop();
                container.destroy();
            } catch (Exception e) {
                log.debug("Error stopping RedisMessageListenerContainer: {}", e.getMessage());
            }
            container = null;
        }
    }

    private void stopHealthCheck() {
        if (healthCheckThread != null) {
            healthCheckThread.interrupt();
            healthCheckThread = null;
        }
    }

    /**
     * 启动守护线程，定期检测订阅是否存活
     */
    private void startHealthCheck() {
        healthCheckThread = new Thread(() -> {
            log.debug("Redis subscriber health-check thread started");
            while (running.get()) {
                try {
                    Thread.sleep(HEALTH_CHECK_INTERVAL_MS);
                    if (!running.get()) break;

                    if (!isSubscriptionAlive()) {
                        int count = reconnectCount.incrementAndGet();
                        log.warn("Redis subscription appears dead, attempting reconnect #{}", count);
                        Thread.sleep(RECONNECT_WAIT_MS);
                        if (running.get()) {
                            try {
                                doStart();
                                log.info("Redis subscription recovered after #{} reconnect", count);
                            } catch (Exception e) {
                                log.error("Reconnect attempt failed: {}", e.getMessage());
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            log.debug("Redis subscriber health-check thread exited");
        }, "redis-subscriber-health");
        healthCheckThread.setDaemon(true);
        healthCheckThread.start();
    }

    /**
     * 通过 PING 检测 Redis 连接是否可用
     */
    private boolean isSubscriptionAlive() {
        RedisConnection conn = null;
        try {
            conn = connectionFactory.getConnection();
            String pong = conn.ping();
            return "PONG".equals(pong);
        } catch (Exception e) {
            log.debug("Redis health-check ping failed: {}", e.getMessage());
            return false;
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (Exception ignored) {}
            }
        }
    }

    private class RuleMessageListener implements org.springframework.data.redis.connection.MessageListener {
        @Override
        public void onMessage(Message message, byte[] pattern) {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            handleMessage(body);
        }
    }

    private void handleMessage(String message) {
        try {
            RulePushMessage push = JSON.parseObject(message, RulePushMessage.class);
            if (!appliesToConfiguredProject(push)) {
                log.debug("Ignored Redis push outside configured project: action={}, projectCode={}, scope={}",
                        push.getAction(), push.getProjectCode(), push.getScope());
                return;
            }
            String operationId = trimToNull(push.getOperationId());
            if (operationId == null) {
                processPush(push);
                return;
            }
            OperationLock lock = acquireOperationLock(operationId);
            try {
                synchronized (lock) {
                    if (isDuplicateOperation(operationId)) return;
                    if (processPush(push)) rememberOperation(operationId);
                }
            } finally {
                releaseOperationLock(operationId, lock);
            }
        } catch (Exception e) {
            log.warn("Failed to handle Redis push message: {}", e.getMessage());
        }
    }

    private boolean processPush(RulePushMessage push) {
        String action = push.getAction();
        boolean handled = false;

        if ("PUBLISH".equals(action)) {
            cache.invalidateVersions(push.getDefinitionId());
            CachedRule cached = resolvePublishedRule(push);
            if (cached == null) {
                log.debug("Ignored rule push outside authorized scope or unavailable: {}", push.getRuleCode());
                return false;
            }
            cache.put(cached);
            log.info("Rule updated via Redis push: {} v{}", push.getRuleCode(), push.getVersion());
            handled = true;

        } else if ("VERSION_UPDATE".equals(action)) {
            cache.invalidateVersions(push.getDefinitionId());
            handled = true;
        } else if ("UNPUBLISH".equals(action) || "DELETE".equals(action)) {
            cache.remove(push.getRuleCode());
            log.info("Rule removed via Redis push: {}", push.getRuleCode());
            handled = true;

        } else if ("FUNC_UPDATE".equals(action)) {
            if (functionRegistrar != null && push.getFuncCode() != null) {
                handled = functionRegistrar.tryRegisterRemoteFromPush(
                        push.getScope(), push.getProjectCode(), push.getFuncCode(), push.getFuncImplType(),
                        push.getFuncImplScript(), push.getFuncImplClass(),
                        push.getFuncImplMethod(), push.getFuncImplBeanName(),
                        push.getFuncParamsJson());
                if (handled) {
                    log.info("Function updated via Redis push: {} ({})", push.getFuncCode(), push.getFuncImplType());
                } else {
                    log.warn("Function update deferred via Redis push: {} ({}); the operation remains retryable",
                            push.getFuncCode(), push.getFuncImplType());
                }
            }

        } else if ("FUNC_DELETE".equals(action)) {
            if (functionRegistrar != null && push.getFuncCode() != null) {
                handled = functionRegistrar.tryRemoveRemote(push.getScope(), push.getProjectCode(), push.getFuncCode());
                log.info("Function removed via Redis push: {}", push.getFuncCode());
            }
        }
        return handled;
    }

    private OperationLock acquireOperationLock(String operationId) {
        return operationLocks.compute(operationId, (ignored, current) -> {
            OperationLock lock = current == null ? new OperationLock() : current;
            lock.references.incrementAndGet();
            return lock;
        });
    }

    private void releaseOperationLock(String operationId, OperationLock lock) {
        operationLocks.computeIfPresent(operationId, (ignored, current) -> {
            if (current != lock) {
                return current;
            }
            return lock.references.decrementAndGet() == 0 ? null : lock;
        });
    }

    private boolean isDuplicateOperation(String operationId) {
        String value = trimToNull(operationId);
        if (value == null) return false;
        synchronized (processedOperationsLock) {
            return processedOperations.contains(value);
        }
    }

    private void rememberOperation(String operationId) {
        String value = trimToNull(operationId);
        if (value == null) return;
        synchronized (processedOperationsLock) {
            if (!processedOperations.add(value)) return;
            processedOperationOrder.addLast(value);
            while (processedOperationOrder.size() > MAX_PROCESSED_OPERATIONS) {
                processedOperations.remove(processedOperationOrder.removeFirst());
            }
        }
    }

    private CachedRule resolvePublishedRule(RulePushMessage push) {
        if (ruleFetcher != null) {
            return ruleFetcher.apply(push.getRuleCode());
        }
        CachedRule cached = new CachedRule();
        cached.setRuleCode(push.getRuleCode());
        cached.setDefinitionId(push.getDefinitionId());
        cached.setVersionBindingId(push.getVersionBindingId());
        cached.setBindingGeneration(push.getBindingGeneration());
        cached.setProjectCode(push.getProjectCode());
        cached.setVersion(push.getVersion() != null ? push.getVersion() : 0);
        cached.setRevisionId(push.getRevisionId());
        cached.setArtifactDigest(push.getArtifactDigest());
        cached.setModelType(push.getModelType());
        cached.setCompiledScript(push.getCompiledScript());
        cached.setCompiledType(push.getCompiledType());
        cached.setModelJson(push.getModelJson());
        cached.setOutputScriptNames(push.getOutputScriptNames());
        cached.setLastUpdateTime(System.currentTimeMillis());
        return cached;
    }

    private boolean appliesToConfiguredProject(RulePushMessage push) {
        String scope = trimToNull(push.getScope());
        if ("GLOBAL".equals(scope)) {
            return true;
        }
        String pushedProjectCode = trimToNull(push.getProjectCode());
        if ("PROJECT".equals(scope)) {
            return projectCode != null && projectCode.equals(pushedProjectCode);
        }
        return pushedProjectCode == null || (projectCode != null && projectCode.equals(pushedProjectCode));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static final class OperationLock {
        private final AtomicInteger references = new AtomicInteger();
    }
}
