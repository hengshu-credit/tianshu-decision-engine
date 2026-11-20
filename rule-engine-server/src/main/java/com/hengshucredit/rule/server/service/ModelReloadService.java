package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleModelVersion;
import com.hengshucredit.rule.server.mapper.RuleModelMapper;
import com.hengshucredit.rule.server.mapper.RuleModelVersionMapper;
import com.hengshucredit.rule.server.service.onnx.OnnxModelExecutionService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** 审批事务提交后按真实生产版本预热；各节点在写事务之外各自建立推理会话。 */
@Service
public class ModelReloadService implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(ModelReloadService.class);
    private static final String CHANNEL = "rule:model:published";
    private final String node = UUID.randomUUID().toString();
    private final ThreadPoolExecutor loader = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(128), r -> { Thread t = new Thread(r, "published-model-loader"); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.CallerRunsPolicy());
    @Resource private RuleModelMapper models;
    @Resource private RuleModelVersionMapper versions;
    @Resource private ModelFileStore files;
    @Resource private OnnxModelExecutionService onnx;
    @Resource @Lazy private RuleModelService service;
    @Resource private RedisMessageListenerContainer listeners;
    @Resource private StringRedisTemplate redis;
    @Value("${rule-engine.model.preload-on-publish:true}") private boolean preloadOnPublish;

    @PostConstruct public void subscribe() { listeners.addMessageListener(this, new ChannelTopic(CHANNEL)); }
    @PreDestroy public void close() { listeners.removeMessageListener(this); loader.shutdownNow(); }

    public void afterPublication(long id, int version) {
        Runnable committed = () -> {
            enqueue(id, version, false);
            try { redis.convertAndSend(CHANNEL, node + ":" + id + ":" + version); }
            catch (RuntimeException error) { log.warn("无法广播模型热加载 modelId={} version={}", id, version, error); }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { committed.run(); }
            });
        } else committed.run();
    }

    @Override public void onMessage(Message message, byte[] pattern) {
        String[] parts = new String(message.getBody(), StandardCharsets.UTF_8).split(":", -1);
        if (parts.length != 3 || node.equals(parts[0])) return;
        try { enqueue(Long.parseLong(parts[1]), Integer.parseInt(parts[2]), false); }
        catch (NumberFormatException error) { log.warn("忽略无效模型热加载通知"); }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void preloadPmmlAtStartup() {
        for (RuleModel m : models.selectList(new QueryWrapper<RuleModel>().select("id","published_version")
                .eq("status", 1).eq("preload_on_startup", 1).gt("published_version", 0))) {
            enqueue(m.getId(), m.getPublishedVersion(), true);
        }
    }

    private void enqueue(long id, int version, boolean startup) {
        try { loader.execute(() -> {
            try { loadCurrent(id, version, startup); }
            catch (RuntimeException | LinkageError error) { log.error("模型热加载失败 modelId={} version={}", id, version, error); }
        }); }
        catch (java.util.concurrent.RejectedExecutionException full) { log.warn("模型热加载队列不可用 modelId={} version={}",id,version); }
    }

    void loadCurrent(long id, int version, boolean startup) {
        RuleModel live = models.selectOne(new QueryWrapper<RuleModel>()
                .select("id","project_id","status","published_version").eq("id", id));
        if (live == null || !Objects.equals(live.getStatus(),1) || !Objects.equals(live.getPublishedVersion(),version)) return;
        var query = new QueryWrapper<RuleModelVersion>()
                .select("model_format","model_file_name","model_digest","model_content","model_config")
                .eq("model_id",id).eq("version",version).eq("status",1);
        if (startup) query.eq("model_format","PMML"); // ONNX 就绪检查使用已有的启动预热器。
        RuleModelVersion frozen = versions.selectOne(query);
        if (frozen == null) return;
        var config = frozen.getModelConfig() == null ? null : JSON.parseObject(frozen.getModelConfig());
        if (!startup && !(config != null && config.containsKey("preloadOnPublish")
                ? config.getBooleanValue("preloadOnPublish") : preloadOnPublish)) return;
        byte[] bytes = ModelFileStore.bytes(files, live.getProjectId(), frozen.getModelFileName(), frozen.getModelFormat(),
                frozen.getModelContent(), frozen.getModelDigest());
        if ("PMML".equals(frozen.getModelFormat())) service.preloadPmml(bytes);
        else if ("ONNX".equals(frozen.getModelFormat())) onnx.preloadWithOutcome(bytes, frozen.getModelConfig());
        log.info("已加载发布模型 modelId={} version={} format={}", id, version, frozen.getModelFormat());
    }
}
