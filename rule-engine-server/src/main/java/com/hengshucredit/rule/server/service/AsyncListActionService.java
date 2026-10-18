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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
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
    private final String journalDirectory;
    private final boolean scanParentDirectories;
    private final List<DurableExecutionPersistenceJournal> journals = new ArrayList<>();
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
        this.journalDirectory = directory;
        this.scanParentDirectories = scanParentDirectories;
    }

    @PostConstruct public void start() {
        openJournals();
        executor.scheduleWithFixedDelay(this::drain, 100, 100, TimeUnit.MILLISECONDS);
    }

    private void openJournals() {
        Path primary = Path.of(journalDirectory).toAbsolutePath().normalize();
        List<Path> directories = new ArrayList<>();
        directories.add(primary);
        Path parent = primary.getParent();
        if (scanParentDirectories && parent != null && Files.isDirectory(parent)) {
            try (var stream = Files.list(parent)) {
                stream.filter(Files::isDirectory).sorted(Comparator.comparing(Path::toString))
                        .filter(path -> !path.equals(primary))
                        .filter(path -> Files.exists(path.resolve("execution-persistence.ndjson.lock"))
                                || Files.exists(path.resolve("execution-persistence.ndjson")))
                        .forEach(directories::add);
            } catch (Exception error) {
                log.warn("扫描名单动作恢复目录失败，继续使用当前实例目录: {}", parent, error);
            }
        }
        for (Path directory : directories) {
            DurableExecutionPersistenceJournal candidate =
                    new DurableExecutionPersistenceJournal(directory.toString());
            candidate.initialize();
            journals.add(candidate);
        }
    }

    public Object submit(Long projectId, JSONObject config, Object values, LocalDateTime now) {
        List<?> copiedValues = JSON.parseArray(JSON.toJSONString(values));
        // 不可恢复的内容错误在持久化交接前反馈，避免坏数据堵住有序恢复队列。
        RuleListActionWriter.validateRequest(config, copiedValues, now);
        String taskId = UUID.randomUUID().toString();
        JSONObject payload = new JSONObject(true);
        payload.put("config", config); payload.put("values", copiedValues); payload.put("actionTime", now.toString());
        payload.put("rule", RuntimeContextBridge.currentRule());
        var entry = new DurableExecutionPersistenceJournal.Entry(taskId, payload.toJSONString(), null, true,
                null, null, projectId, null, null, null, null, null, null, null, true, false);
        if (closed || journals.isEmpty() || !journals.get(0).append(entry)) {
            log.error("名单动作持久化交接失败 taskId={} projectId={}", taskId, projectId);
            return Map.of("taskId", taskId, "status", "REJECTED", "error", "名单任务未能持久化，请检查恢复队列存储");
        }
        return Map.of("taskId", taskId, "status", "QUEUED");
    }

    void drain() {
        if (closed || System.currentTimeMillis() < retryAt) return;
        try {
            // 只加载有界批次；数据库故障时积压保留在磁盘，不填满引擎堆内存。
            for (var journal : journals) {
                for (var entry : journal.read(100)) {
                    if (closed) break;
                    var payload = JSON.parseObject(entry.logJson());
                    JSONObject rule = payload.getJSONObject("rule");
                    boolean hasRuleIdentity = rule != null && (rule.get("traceId") != null
                            || rule.get("code") != null || rule.get("revisionId") != null
                            || rule.get("artifactDigest") != null);
                    if (!hasRuleIdentity) {
                        writer.write(entry.eventId(), entry.projectId(), payload.getJSONObject("config"),
                                payload.getJSONArray("values"), LocalDateTime.parse(payload.getString("actionTime")));
                    } else {
                        writer.write(entry.eventId(), entry.projectId(), payload.getJSONObject("config"),
                                payload.getJSONArray("values"), LocalDateTime.parse(payload.getString("actionTime")), rule);
                    }
                    journal.replacePrefix(1, List.of());
                    failures = 0;
                    lastError = null;
                }
            }
        } catch (RuntimeException error) {
            failures = Math.min(failures + 1, 6);
            lastError = error.getMessage();
            retryAt = System.currentTimeMillis() + Math.min(60000, 1000L << failures);
            log.error("名单动作暂未落库，任务保留并将在恢复后重试", error);
        }
    }

    public Map<String, Object> snapshot() {
        var result = new java.util.LinkedHashMap<>(journals.isEmpty()
                ? Map.of() : journals.get(0).snapshot());
        result.put("journalCount", journals.size());
        result.put("recoveryJournals", journals.stream().skip(1).map(DurableExecutionPersistenceJournal::snapshot).toList());
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
        for (var journal : journals) journal.close();
    }
}
