package com.hengshucredit.rule.server.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** NULL处理状态就是持久待办；索引跳过已处理行，多节点按行锁领取。 */
@Service
@DependsOn("schemaSyncService")
public class RuleSetStatisticsBackfill {
    private static final Logger log = LoggerFactory.getLogger(RuleSetStatisticsBackfill.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final RuleSetStatisticsStore statistics;
    private final AtomicBoolean running = new AtomicBoolean();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(action -> {
        Thread thread = new Thread(action, "rule-set-statistics-backfill");
        thread.setDaemon(true); return thread;
    });

    public RuleSetStatisticsBackfill(JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        statistics = new RuleSetStatisticsStore(jdbc);
        transaction = new TransactionTemplate(transactions);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Scheduled(fixedDelay = 2000, initialDelay = 2000)
    public void schedule() {
        if (worker.isShutdown() || !running.compareAndSet(false, true)) return;
        try {
            worker.execute(() -> {
                try {
                    for (int batch = 0; batch < 50 && !Thread.currentThread().isInterrupted(); batch++) {
                        if (processBatch() == 0) break;
                    }
                } catch (Exception failure) { log.error("规则集统计历史补齐失败，未提交记录将在下轮重试", failure); }
                finally { running.set(false); }
            });
        } catch (RuntimeException failure) {
            running.set(false);
            if (!worker.isShutdown()) throw failure;
        }
    }

    int processBatch() {
        Integer changed = transaction.execute(status -> {
            var rows = jdbc.query("SELECT id,create_time,trace_info FROM rule_execution_log "
                            + "WHERE rule_set_stats_version IS NULL OR rule_set_stats_version<2 ORDER BY id,create_time LIMIT 10 FOR UPDATE SKIP LOCKED",
                    (result, row) -> new Source(result.getLong("id"), result.getTimestamp("create_time"), result.getString("trace_info")));
            for (Source row : rows) {
                RuleSetTraceSummary summary = RuleSetTraceSummary.fromTrace(row.trace());
                statistics.persist(row.id(), row.time().toLocalDateTime(), summary.json(), summary.status());
                jdbc.update("UPDATE rule_execution_log SET rule_set_stats_status=?,rule_set_stats_version=2 "
                        + "WHERE id=? AND create_time=? AND (rule_set_stats_version IS NULL OR rule_set_stats_version<2)", summary.status(), row.id(), row.time());
            }
            return rows.size();
        });
        return changed == null ? 0 : changed;
    }

    @PreDestroy public void close() { worker.shutdownNow(); }

    private record Source(long id, Timestamp time, String trace) { }
}
