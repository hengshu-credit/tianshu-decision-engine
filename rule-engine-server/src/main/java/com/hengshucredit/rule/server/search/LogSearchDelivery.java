package com.hengshucredit.rule.server.search;

import com.hengshucredit.rule.server.mapper.RuleExecutionLogMapper;
import org.springframework.beans.factory.annotation.Value;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.model.entity.RuleRuntimeCallLog;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

@Service
@DependsOn("schemaSyncService")
@ConditionalOnProperty(name = "rule-engine.search.enabled", havingValue = "true")
public class LogSearchDelivery {
    private static final Logger LOG = LoggerFactory.getLogger(LogSearchDelivery.class);
    private static final BeanPropertyRowMapper<RuleExecutionLog> EXECUTION =
            BeanPropertyRowMapper.newInstance(RuleExecutionLog.class);
    private static final BeanPropertyRowMapper<RuleRuntimeCallLog> CALL =
            BeanPropertyRowMapper.newInstance(RuleRuntimeCallLog.class);
    private static final int BULK_BYTES = 5 * 1024 * 1024;

    private enum Source {
        EXECUTIONS("rule_execution_log"),
        CALLS("rule_runtime_call_log");
        final String table;

        Source(String table) {
            this.table = table;
        }
    }

    private record Claim(ElasticLogClient.Ref ref, int attempt) {}

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ElasticLogClient client;
    private final LogSearchDocuments documents;
    private final AtomicBoolean busy = new AtomicBoolean();
    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(
                    task -> {
                        Thread thread = new Thread(task, "log-search-delivery");
                        thread.setDaemon(true);
                        return thread;
                    });

    @Value("${rule-engine.search.compact-logs:false}")
    private boolean compactLogs;

    private final LongAdder delivered = new LongAdder();
    private final LongAdder failures = new LongAdder();
    private boolean templatesReady;
    private volatile String lastError;
    private long nextTemplateAttempt;

    public LogSearchDelivery(
            JdbcTemplate jdbc,
            PlatformTransactionManager manager,
            ElasticLogClient client,
            LogSearchDocuments documents) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.client = client;
        this.documents = documents;
    }

    @Scheduled(fixedDelayString = "${rule-engine.search.poll-ms:500}", initialDelay = 1000)
    public void schedule() {
        if (worker.isShutdown() || !busy.compareAndSet(false, true)) return;
        worker.execute(
                () -> {
                    try {
                        if (!templatesReady) {
                            if (System.currentTimeMillis() < nextTemplateAttempt) return;
                            nextTemplateAttempt = System.currentTimeMillis() + 10000;
                            client.templates();
                            templatesReady = true;
                        }
                        for (Source source : Source.values()) deliver(source);
                    } catch (RuntimeException failure) {
                        failed(failure);
                    } finally {
                        busy.set(false);
                    }
                });
    }

    private void deliver(Source source) {
        List<Claim> claims =
                transaction.execute(
                        status -> {
                            var rows =
                                    jdbc.query(
                                            "SELECT id,create_time,es_revision,es_attempts FROM "
                                                    + source.table
                                                    + " WHERE es_due_at <= CURRENT_TIMESTAMP(6)"
                                                    + (source == Source.EXECUTIONS
                                                            ? " AND rule_set_stats_version>=2"
                                                            : "")
                                                    + " ORDER BY es_due_at,id LIMIT 100 FOR UPDATE"
                                                    + " SKIP LOCKED",
                                            (rs, i) ->
                                                    new Claim(
                                                            new ElasticLogClient.Ref(
                                                                    rs.getLong(1),
                                                                    rs.getObject(
                                                                            2, LocalDateTime.class),
                                                                    rs.getLong(3)),
                                                            rs.getInt(4) + 1));
                            jdbc.batchUpdate(
                                    "UPDATE "
                                            + source.table
                                            + " SET es_attempts=?,es_due_at=TIMESTAMPADD(SECOND,60,CURRENT_TIMESTAMP(6))"
                                            + " WHERE id=? AND create_time=?",
                                    rows,
                                    100,
                                    (ps, row) -> {
                                        ps.setInt(1, row.attempt());
                                        ps.setLong(2, row.ref().id());
                                        ps.setObject(3, row.ref().time());
                                    });
                            return rows;
                        });
        if (claims == null || claims.isEmpty()) return;
        List<Claim> pending = new ArrayList<>();
        List<ElasticLogClient.Document> docs = new ArrayList<>();
        StringBuilder bulk = new StringBuilder();
        int bytes = 0;
        for (Claim claim : claims) {
            try {
                var group = load(source, claim.ref());
                if (group.isEmpty())
                    continue; // superseded while this worker was preparing the previous batch
                StringBuilder lines = new StringBuilder();
                for (var doc : group) lines.append(client.bulkLine(doc));
                int size = lines.toString().getBytes(StandardCharsets.UTF_8).length;
                if (!docs.isEmpty() && bytes + (long) size > BULK_BYTES) {
                    flush(source, pending, docs, bulk);
                    bytes = 0;
                }
                docs.addAll(group);
                pending.add(claim);
                bulk.append(lines);
                bytes += size;
            } catch (RuntimeException failure) {
                finish(source, claim, false);
                failed(failure);
            }
        }
        if (!docs.isEmpty()) flush(source, pending, docs, bulk);
    }

    private List<ElasticLogClient.Document> load(Source source, ElasticLogClient.Ref ref) {
        var time = ref.time();
        if (source == Source.CALLS)
            return jdbc.query(
                    "SELECT * FROM rule_runtime_call_log WHERE id=? AND create_time=? AND"
                            + " es_revision=?",
                    (rs, i) -> documents.call(ref, CALL.mapRow(rs, i)),
                    ref.id(),
                    time,
                    ref.revision());
        var rows =
                jdbc.query(
                        "SELECT l.*,"
                                + RuleExecutionLogMapper
                                        .RESTORED_TRACE
                                + " AS stored_trace,s.rule_set_stats_json, EXISTS(SELECT 1 FROM"
                                + " rule_execution_log_current c WHERE c.id=l.id AND"
                                + " c.create_time=l.create_time) AS is_current FROM"
                                + " rule_execution_log l LEFT JOIN rule_execution_statistics s ON"
                                + " s.id=l.id AND s.create_time=l.create_time WHERE l.id=? AND"
                                + " l.create_time=? AND l.es_revision=?",
                        (rs, i) -> {
                            RuleExecutionLog execution = EXECUTION.mapRow(rs, i);
                            execution.setRuleSetStatsJson(null);
                            execution.setTraceInfo(rs.getString("stored_trace"));
                            return documents.execution(
                                    ref,
                                    execution,
                                    rs.getString("rule_set_stats_json"),
                                    rs.getBoolean("is_current"));
                        },
                        ref.id(),
                        time,
                        ref.revision());
        return rows.isEmpty() ? List.of() : rows.get(0);
    }

    private void flush(
            Source source,
            List<Claim> claims,
            List<ElasticLogClient.Document> docs,
            StringBuilder bulk) {
        try {
            var accepted = client.bulk(bulk.toString(), docs);
            int i = 0;
            for (var claim : claims) {
                boolean ok = true;
                while (i < docs.size() && docs.get(i).ref().equals(claim.ref()))
                    ok &= accepted.get(i++);
                finish(source, claim, ok);
                if (!ok) failed(new IllegalStateException("ES 未确认全部索引项"));
            }
        } catch (RuntimeException failure) {
            failed(failure);
            for (var claim : claims) finish(source, claim, false);
        } finally {
            docs.clear();
            claims.clear();
            bulk.setLength(0);
        }
    }

    private void finish(Source source, Claim claim, boolean success) {
        String due =
                success
                        ? "NULL"
                        : "TIMESTAMPADD(SECOND, LEAST(300,POW(2,LEAST(es_attempts,8))) +"
                                + " FLOOR(RAND()*3), CURRENT_TIMESTAMP(6))";
        String archive = "";
        if (success && compactLogs) {
            if (source == Source.EXECUTIONS) {
                archive =
                        ",es_trace_archive=CASE WHEN OCTET_LENGTH(trace_info)>=4096 THEN"
                                + " COMPRESS(trace_info) ELSE es_trace_archive"
                                + " END,trace_info=IF(es_trace_archive IS NULL,trace_info,NULL)";
            } else {
                archive =
                        ",es_body_aliases=es_body_aliases |"
                            + " IF(OCTET_LENGTH(original_request_body)>=4096 AND"
                            + " CAST(original_request_body AS BINARY)=CAST(raw_request_body AS"
                            + " BINARY),1,0) | IF(OCTET_LENGTH(original_response_body)>=4096 AND"
                            + " CAST(original_response_body AS BINARY)=CAST(raw_response_body AS"
                            + " BINARY),2,0),original_request_body=IF((es_body_aliases &"
                            + " 1)=1,NULL,original_request_body),original_response_body=IF((es_body_aliases"
                            + " & 2)=2,NULL,original_response_body)";
            }
        }
        int changed =
                jdbc.update(
                        "UPDATE "
                                + source.table
                                + " SET es_due_at="
                                + due
                                + archive
                                + " WHERE id=? AND create_time=?"
                                + " AND es_revision=? AND es_attempts=? AND es_due_at IS NOT NULL",
                        claim.ref().id(),
                        claim.ref().time(),
                        claim.ref().revision(),
                        claim.attempt());
        if (success && changed == 1) delivered.increment();
    }

    private void failed(RuntimeException error) {
        failures.increment();
        // ES item errors can contain user values. Only expose a status or an exception category.
        String reason =
                error.getMessage() != null && error.getMessage().matches("ES HTTP [0-9]{3}")
                        ? error.getMessage()
                        : error.getClass().getSimpleName();
        if (!reason.equals(lastError)) LOG.warn("日志检索投递暂未确认，将重试 ({})", reason);
        lastError = reason;
    }

    public Map<String, Object> metrics() {
        Map<String, Object> result = new LinkedHashMap<>(client.readMetrics());
        result.put("delivered", delivered.sum());
        result.put("failures", failures.sum());
        result.put("lastError", lastError);
        for (Source source : Source.values())
            result.put(
                    source.table + "_pending",
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM " + source.table + " WHERE es_due_at IS NOT NULL",
                            Long.class));
        return result;
    }

    @PreDestroy
    public void close() {
        worker.shutdownNow();
    }
}
