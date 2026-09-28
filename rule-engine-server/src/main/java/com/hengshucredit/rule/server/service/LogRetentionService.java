package com.hengshucredit.rule.server.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 可选的后台日志清理器。默认关闭且每类天数为 0，生产可以按部署策略显式启用。
 * 规则/外数/模块/模型/数据库/名单调用、账单、生命周期和名单变更审计数据永久保留；
 * 仅代码白名单中的辅助日志类别接受部署级保留天数配置，避免误删业务证据。
 */
@Service
public class LogRetentionService {
    private static final Logger log = LoggerFactory.getLogger(LogRetentionService.class);
    private static final String LOCK_NAME = "CONCAT('tianshu:log-retention:', MD5(DATABASE()))";
    private static final List<String> NEVER_DELETE = List.of(
            "executionLog", "runtimeCallLog", "experimentLog", "billingRecord",
            "lifecycleEvent", "listRecordLog");
    @Resource private JdbcTemplate jdbcTemplate;
    @Resource private LogRetentionProperties properties;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile Map<String, Object> lastRun = Map.of("lastRunStatus", "NOT_RUN");

    @PostConstruct
    public void validateConfiguration() {
        properties.validate();
    }

    @Scheduled(fixedDelayString = "${rule-engine.log-retention.interval-ms:86400000}")
    public void cleanup() {
        if (!properties.isEnabled()) return;
        properties.validate();
        if (retentionDays().values().stream().noneMatch(days -> days > 0) || !running.compareAndSet(false, true)) return;
        Run run = new Run();
        lastRun = run.snapshot("RUNNING", null);
        String status = "FAILED";
        try {
            status = jdbcTemplate.execute((ConnectionCallback<String>) connection -> {
                if (!lockOperation(connection, "GET_LOCK(" + LOCK_NAME + ", 0)")) return "SKIPPED_LOCKED";
                try {
                    delete(connection, run, "authAccessLog", "rule_auth_access_log", "create_time", properties.getAuthAccessLogDays());
                    return !run.failures.isEmpty() ? "FAILED" : run.limited.isEmpty() ? "COMPLETED" : "BATCH_LIMIT_REACHED";
                } finally {
                    try {
                        if (!lockOperation(connection, "RELEASE_LOCK(" + LOCK_NAME + ")")) {
                            throw new SQLException("日志清理锁释放失败");
                        }
                    } catch (SQLException failure) {
                        // 不能将仍持有命名锁的连接放回连接池。
                        try { connection.abort(Runnable::run); }
                        catch (SQLException abortFailure) { failure.addSuppressed(abortFailure); }
                        throw failure;
                    }
                }
            });
        } catch (RuntimeException failure) {
            run.failures.put("database", "连接或清理锁异常，请查看服务日志");
            log.warn("日志清理任务失败", failure);
        } finally {
            lastRun = run.snapshot(status, LocalDateTime.now());
            running.set(false);
        }
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", properties.isEnabled());
        result.putAll(lastRun);
        result.put("retentionDays", retentionDays());
        result.put("batchSize", properties.getBatchSize());
        result.put("maxBatchesPerTable", properties.getMaxBatchesPerTable());
        result.put("queryTimeoutSeconds", properties.getQueryTimeoutSeconds());
        result.put("neverDeleteCategories", NEVER_DELETE);
        return result;
    }

    private boolean lockOperation(Connection connection, String operation) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT " + operation)) {
            statement.setQueryTimeout(properties.getQueryTimeoutSeconds());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("日志清理锁未返回结果");
                int locked = result.getInt(1);
                if (result.wasNull()) throw new SQLException("日志清理锁操作失败");
                return locked == 1;
            }
        }
    }

    private void delete(Connection connection, Run run, String key, String table, String timeColumn, int days) {
        if (days <= 0) return;
        try {
            if (!hasTimeIndex(connection, table, timeColumn)) {
                run.failures.put(key, "时间列缺少前导索引，已跳过清理");
                return;
            }
            String sql = "DELETE FROM `" + table + "` WHERE `" + timeColumn + "` < ? ORDER BY `"
                    + timeColumn + "` LIMIT ?";
            run.deleted.put(key, 0L);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setQueryTimeout(properties.getQueryTimeoutSeconds());
                statement.setTimestamp(1, Timestamp.valueOf(run.startedAt.minusDays(days)));
                statement.setInt(2, properties.getBatchSize());
                for (int batch = 0; batch < properties.getMaxBatchesPerTable(); batch++) {
                    int count = statement.executeUpdate();
                    run.deleted.merge(key, (long) count, Long::sum);
                    if (count < properties.getBatchSize()) return;
                }
                run.limited.add(key);
            }
        } catch (SQLException failure) {
            run.failures.put(key, "本类清理失败，请查看服务日志；删除量为已确认批次");
            log.warn("日志清理失败，table={}", table, failure);
        }
    }

    private boolean hasTimeIndex(Connection connection, String table, String column) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()"
                        + " AND table_name = ? AND column_name = ? AND seq_in_index = 1 AND is_visible = 'YES'")) {
            statement.setQueryTimeout(properties.getQueryTimeoutSeconds());
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) > 0;
            }
        }
    }

    private static final class Run {
        private final LocalDateTime startedAt = LocalDateTime.now();
        private final Map<String, Long> deleted = new LinkedHashMap<>();
        private final Map<String, String> failures = new LinkedHashMap<>();
        private final List<String> limited = new ArrayList<>();

        private Map<String, Object> snapshot(String status, LocalDateTime finishedAt) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("lastRunAt", startedAt);
            result.put("lastFinishedAt", finishedAt);
            result.put("lastRunStatus", status);
            result.put("lastDeleted", Map.copyOf(deleted));
            result.put("lastFailures", Map.copyOf(failures));
            result.put("batchLimitedTables", List.copyOf(limited));
            return Collections.unmodifiableMap(result);
        }
    }

    private Map<String, Integer> retentionDays() {
        Map<String, Integer> days = new LinkedHashMap<>();
        days.put("authAccessLog", properties.getAuthAccessLogDays());
        return days;
    }
}
