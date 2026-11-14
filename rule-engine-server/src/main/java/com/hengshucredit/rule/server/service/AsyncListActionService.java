package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.core.engine.RuntimeContextBridge;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** 先 fsync 接收任务，再异步写库；失败保留原任务并退避重试，重启后重放。 */
@Service
public class AsyncListActionService implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(AsyncListActionService.class);
    private final RuleListActionWriter writer;
    private final DurableExecutionPersistenceJournal journal;
    private long lastPeerScan;
    private volatile List<Map<String,Object>> recoveryJournals = List.of();
    private final boolean scanParentDirectories;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "rule-list-actions"); thread.setDaemon(true); return thread;
    });
    private volatile boolean closed;
    private volatile long retryAt;
    private volatile int failures;
    private volatile String lastError;

    public AsyncListActionService(RuleListActionWriter writer, String directory) {
        this(writer, directory, false);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AsyncListActionService(RuleListActionWriter writer,
            @Value("${rule-engine.list-actions.journal-dir:./logs/list-action-journal}") String directory,
            @Value("${rule-engine.list-actions.scan-parent:false}") boolean scanParentDirectories) {
        this.writer = writer;
        this.journal = new DurableExecutionPersistenceJournal(directory);
        this.scanParentDirectories = scanParentDirectories;
    }

    @PostConstruct public void start() {
        journal.initialize();
        executor.scheduleWithFixedDelay(this::drain, 100, 100, TimeUnit.MILLISECONDS);
    }

    public Object submit(Long projectId, JSONObject config, Object values, LocalDateTime now) {
        return submit(projectId, config, values, now, UUID.randomUUID().toString());
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.hengshucredit.rule.server.security.ConsoleRecordOwnership recordOwnership;

    JSONObject prepare(Long projectId, JSONObject config, Object values, LocalDateTime now, String taskId,
                       VariableResolveOptions.ExperimentGroup experiment) {
        var copiedValues = JSON.parseArray(JSON.toJSONString(values));
        RuleListActionWriter.validateRequest(config, copiedValues, now);
        JSONObject rule = new JSONObject(new java.util.LinkedHashMap<>(RuntimeContextBridge.currentRule()));
        Object rootTraceId = RuntimeContextBridge.currentContext().rootRule().get("traceId");
        if (rootTraceId != null) rule.put("rootTraceId", rootTraceId);
        Long actor = recordOwnership == null ? null : recordOwnership.currentUserId();
        if (actor != null) rule.put("actorUserId", actor);
        if (experiment != null) {
            rule.put("experimentId", experiment.experimentId());
            rule.put("groupId", experiment.groupId());
            rule.put("groupType", experiment.groupType());
            rule.put("experimentTraceId", experiment.traceId());
        }
        JSONObject task = new JSONObject(true);
        task.put("taskId", taskId == null ? UUID.randomUUID().toString() : taskId);
        task.put("projectId", projectId);
        task.put("config", config);
        task.put("values", copiedValues);
        task.put("actionTime", now.toString());
        task.put("rule", rule);
        return task;
    }

    Object submit(Long projectId, JSONObject config, Object values, LocalDateTime now, String taskId) {
        return submitTask(prepare(projectId, config, values, now, taskId, null));
    }

    Map<String, Object> submitTask(JSONObject task) {
        return append(task.getString("taskId"), task.getLong("projectId"), task);
    }

    Map<String, Object> submitBatch(List<JSONObject> tasks) {
        // 一次 fsync 交接整批。恢复时按原顺序重放，已提交的每个子任务由数据库回执去重。
        return append(tasks.get(0).getString("taskId"), tasks.get(0).getLong("projectId"), Map.of("tasks", tasks));
    }

    private Map<String, Object> append(String taskId, Long projectId, Object payload) {
        var entry = new DurableExecutionPersistenceJournal.Entry(taskId, JSON.toJSONString(payload), null, true,
                null, null, projectId, null, null, null, null, null, null, null, true, false);
        if (closed || !journal.append(entry)) {
            log.error("名单动作持久化交接失败 taskId={} projectId={}", taskId, projectId);
            return Map.of("taskId", taskId, "status", "REJECTED", "error", "名单任务未能持久化，请检查恢复队列存储");
        }
        return Map.of("taskId", taskId, "status", "QUEUED");
    }

    void drain() {
        if (closed || System.currentTimeMillis() < retryAt) return;
        drainJournal(journal);
        long now = System.nanoTime();
        if (scanParentDirectories && (lastPeerScan == 0 || now - lastPeerScan >= TimeUnit.SECONDS.toNanos(2))) {
            lastPeerScan = now;
            recoveryJournals = journal.replaySiblings(this::drainJournal);
        }
    }

    private void drainJournal(DurableExecutionPersistenceJournal source) {
        try {
            for (var entry : source.read(100)) {
                if (closed) break;
                var payload = JSON.parseObject(entry.logJson());
                var tasks = payload.getJSONArray("tasks");
                if (tasks == null) writeTask(entry.eventId(), entry.projectId(), payload);
                else for (int i = 0; i < tasks.size(); i++) {
                    JSONObject task = tasks.getJSONObject(i);
                    writeTask(task.getString("taskId"), task.getLong("projectId"), task);
                }
                source.replacePrefix(1, List.of());
                failures = 0;
                lastError = null;
            }
        } catch (RuntimeException error) {
            failures = Math.min(failures + 1, 6);
            lastError = error.getMessage();
            retryAt = System.currentTimeMillis() + Math.min(60000, 1000L << failures);
            log.error("名单动作暂未落库，任务保留并将在恢复后重试", error);
        }
    }

    private void writeTask(String taskId, Long projectId, JSONObject task) {
        JSONObject rule = task.getJSONObject("rule");
        var config = task.getJSONObject("config");
        var values = task.getJSONArray("values");
        var time = LocalDateTime.parse(task.getString("actionTime"));
        if (rule == null || rule.isEmpty()) writer.write(taskId, projectId, config, values, time);
        else writer.write(taskId, projectId, config, values, time, rule);
    }

    public Map<String, Object> snapshot() {
        var result = new java.util.LinkedHashMap<>(journal.snapshot());
        var peers = recoveryJournals;
        result.put("journalCount", 1 + peers.size());
        result.put("recoveryJournals", peers);
        result.put("scanParent", scanParentDirectories);
        result.put("consecutiveFailures", failures); result.put("lastError", lastError);
        result.put("nextRetryAt", failures == 0 ? null : java.time.Instant.ofEpochMilli(retryAt));
        result.put("writeConcurrency", 1);
        return result;
    }

    @Override @PreDestroy public void close() {
        closed = true;
        executor.shutdown();
        try { if (!executor.awaitTermination(15, TimeUnit.SECONDS)) executor.shutdownNow(); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); executor.shutdownNow(); }
        journal.close();
    }
}
