package com.hengshucredit.rule.server.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import com.hengshucredit.rule.server.service.VariableResolutionInvocationCache.SourceStep;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 跨请求、跨节点的规则恢复状态。该表保存检查点和状态，不替代执行日志。
 */
@Service
public class RuleExecutionStateService {
    @Resource
    private JdbcTemplate jdbcTemplate;

    public State find(Long projectId, Long definitionId, String keyHash) {
        List<State> rows = jdbcTemplate.query(
                "SELECT id, project_id, definition_id, rule_code, idempotency_key_hash, request_digest, "
                        + "root_trace_id, status, attempt_no, initial_revision_id, current_revision_id, "
                        + "current_artifact_digest, checkpoint_json, result_json, lease_owner, lease_until, error_message, expire_time "
                        + "FROM rule_execution_state WHERE project_id = ? AND definition_id = ? "
                        + "AND idempotency_key_hash = ? LIMIT 1",
                stateRowMapper(),
                projectId, definitionId, keyHash);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public State findByRootTraceId(String rootTraceId) {
        if (rootTraceId == null || rootTraceId.isBlank()) return null;
        List<State> rows = jdbcTemplate.query(
                "SELECT id, project_id, definition_id, rule_code, idempotency_key_hash, request_digest, "
                        + "root_trace_id, status, attempt_no, initial_revision_id, current_revision_id, "
                        + "current_artifact_digest, checkpoint_json, result_json, lease_owner, lease_until, error_message, expire_time "
                        + "FROM rule_execution_state WHERE root_trace_id = ? LIMIT 1",
                stateRowMapper(), rootTraceId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private RowMapper<State> stateRowMapper() {
        return (rs, rowNum) -> new State(rs.getLong("id"), rs.getObject("project_id", Long.class),
                rs.getLong("definition_id"), rs.getString("rule_code"),
                rs.getString("idempotency_key_hash"), rs.getString("request_digest"),
                rs.getString("root_trace_id"), rs.getString("status"), rs.getInt("attempt_no"),
                rs.getObject("initial_revision_id", Long.class),
                rs.getObject("current_revision_id", Long.class),
                rs.getString("current_artifact_digest"), rs.getString("checkpoint_json"), rs.getString("result_json"),
                rs.getString("lease_owner"), rs.getObject("lease_until", LocalDateTime.class),
                rs.getString("error_message"), rs.getObject("expire_time", LocalDateTime.class));
    }

    public Creation create(Long projectId, Long definitionId, String ruleCode, String keyHash,
                        String requestDigest, String rootTraceId, Long revisionId, String artifactDigest,
                        String owner, int leaseSeconds) {
        return create(projectId, definitionId, ruleCode, keyHash, requestDigest, rootTraceId,
                revisionId, artifactDigest, owner, leaseSeconds, 86400);
    }

    public Creation create(Long projectId, Long definitionId, String ruleCode, String keyHash,
                        String requestDigest, String rootTraceId, Long revisionId, String artifactDigest,
                        String owner, int leaseSeconds, int retentionSeconds) {
        int inserted = jdbcTemplate.update(
                    "INSERT IGNORE INTO rule_execution_state "
                            + "(project_id, definition_id, rule_code, idempotency_key_hash, request_digest, "
                            + "root_trace_id, status, attempt_no, initial_revision_id, current_revision_id, "
                            + "current_artifact_digest, lease_owner, lease_until, expire_time, create_time, update_time) "
                    + "VALUES (?, ?, ?, ?, ?, ?, 'RUNNING', 1, ?, ?, ?, ?, DATE_ADD(NOW(6), INTERVAL ? SECOND), "
                    + "DATE_ADD(NOW(6), INTERVAL ? SECOND), NOW(6), NOW(6))",
                    projectId, definitionId, ruleCode, keyHash, requestDigest, rootTraceId,
                    revisionId, revisionId, artifactDigest, owner, leaseSeconds, retentionSeconds);
        State state = find(projectId, definitionId, keyHash);
        if (state == null) throw new IllegalStateException("无法创建或读取规则执行状态");
        return new Creation(state, inserted == 1);
    }

    public void deleteExpired(Long projectId, Long definitionId, String keyHash) {
        jdbcTemplate.update("DELETE FROM rule_execution_state WHERE project_id = ? "
                        + "AND definition_id = ? AND idempotency_key_hash = ? AND expire_time IS NOT NULL "
                        + "AND expire_time <= NOW(6) AND status <> 'RUNNING' "
                        + "AND (lease_until IS NULL OR lease_until <= NOW(6))", projectId, definitionId, keyHash);
    }

    public boolean claim(Long id, String owner, int leaseSeconds) {
        return claim(id, owner, leaseSeconds, null, null);
    }

    public boolean claim(Long id, String owner, int leaseSeconds,
                         Long currentRevisionId, String currentArtifactDigest) {
        return claim(id, owner, leaseSeconds, leaseSeconds, currentRevisionId, currentArtifactDigest);
    }

    public boolean claim(Long id, String owner, int leaseSeconds, int retentionSeconds,
                         Long currentRevisionId, String currentArtifactDigest) {
        return jdbcTemplate.update(
                "UPDATE rule_execution_state SET lease_owner = ?, lease_until = DATE_ADD(NOW(6), INTERVAL ? SECOND), "
                        + "attempt_no = attempt_no + 1, status = 'RUNNING', "
                        + "current_revision_id = COALESCE(?, current_revision_id), "
                        + "current_artifact_digest = COALESCE(?, current_artifact_digest), "
                        + "expire_time = DATE_ADD(NOW(6), INTERVAL ? SECOND), result_json = NULL, "
                        + "finish_time = NULL, update_time = NOW(6) "
                        + "WHERE id = ? AND (lease_until IS NULL OR lease_until < NOW(6) OR lease_owner = ?)",
                owner, leaseSeconds, currentRevisionId, currentArtifactDigest, retentionSeconds, id, owner) == 1;
    }

    public void checkpoint(Long id, String owner, String status, String checkpointJson,
                           Long currentRevisionId, String currentArtifactDigest, String errorMessage) {
        checkpoint(id, owner, status, checkpointJson, currentRevisionId, currentArtifactDigest,
                errorMessage, 120);
    }

    public void checkpoint(Long id, String owner, String status, String checkpointJson,
                           Long currentRevisionId, String currentArtifactDigest, String errorMessage,
                           int leaseSeconds) {
        int effectiveLeaseSeconds = Math.max(30, Math.min(3600, leaseSeconds));
        jdbcTemplate.update(
                "UPDATE rule_execution_state SET status = ?, checkpoint_json = ?, "
                        + "current_revision_id = ?, current_artifact_digest = ?, error_message = ?, update_time = NOW(6) "
                        + ", lease_until = DATE_ADD(NOW(6), INTERVAL ? SECOND) "
                        + "WHERE id = ? AND lease_owner = ? AND lease_until > NOW(6)",
                status, checkpointJson, currentRevisionId, currentArtifactDigest, errorMessage,
                effectiveLeaseSeconds, id, owner);
    }

    public void persistCheckpoint(String rootTraceId, String checkpointJson,
                                  Long revisionId, String artifactDigest, String status) {
        if (rootTraceId == null || rootTraceId.isBlank()) return;
        jdbcTemplate.update(
                "INSERT INTO rule_execution_checkpoint "
                        + "(root_trace_id, step_id, step_type, step_status, source_revision_id, "
                        + "source_artifact_digest, resolved_value, update_time, create_time) "
                        + "VALUES (?, '__ROOT__', 'ROOT', ?, ?, ?, ?, NOW(6), NOW(6)) "
                        + "ON DUPLICATE KEY UPDATE step_status = VALUES(step_status), "
                        + "source_revision_id = VALUES(source_revision_id), "
                        + "source_artifact_digest = VALUES(source_artifact_digest), "
                        + "resolved_value = VALUES(resolved_value), update_time = NOW(6)",
                rootTraceId, status == null ? "RUNNING" : status, revisionId, artifactDigest, checkpointJson);
    }

    public void persistSourceStep(String rootTraceId, SourceStep step,
                                  Long revisionId, String artifactDigest) {
        if (rootTraceId == null || step == null || step.getSourceKey() == null) return;
        jdbcTemplate.update(
                "INSERT INTO rule_execution_checkpoint "
                        + "(root_trace_id, step_id, step_type, step_status, input_digest, dependency_digest, "
                        + "source_revision_id, source_artifact_digest, resolved_value, external_result, attempt_history, update_time, create_time) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(6), NOW(6)) "
                        + "ON DUPLICATE KEY UPDATE step_type = VALUES(step_type), step_status = VALUES(step_status), "
                        + "input_digest = VALUES(input_digest), dependency_digest = VALUES(dependency_digest), "
                        + "source_revision_id = VALUES(source_revision_id), source_artifact_digest = VALUES(source_artifact_digest), "
                        + "resolved_value = VALUES(resolved_value), external_result = VALUES(external_result), "
                        + "attempt_history = VALUES(attempt_history), update_time = NOW(6)",
                rootTraceId, step.getSourceKey(), step.getSourceType(), step.getStatus(),
                step.getInputDigest(), step.getDependencyDigest(), revisionId, artifactDigest,
                JSON.toJSONString(step.getValue()), JSON.toJSONString(step.getResponse()),
                CanonicalJson.write(step.getMetadata()));
    }

    public void finish(Long id, String owner, String status, String checkpointJson, String resultJson,
                       String errorMessage) {
        jdbcTemplate.update(
                "UPDATE rule_execution_state SET status = ?, checkpoint_json = ?, result_json = ?, error_message = ?, "
                        + "lease_owner = NULL, lease_until = NULL, finish_time = NOW(6), update_time = NOW(6) "
                        + "WHERE id = ? AND lease_owner = ? AND lease_until > NOW(6)",
                status, checkpointJson, resultJson, errorMessage, id, owner);
    }

    public record State(Long id, Long projectId, Long definitionId, String ruleCode,
                        String keyHash, String requestDigest, String rootTraceId, String status,
                        int attemptNo, Long initialRevisionId, Long currentRevisionId,
                        String artifactDigest, String checkpointJson, String resultJson, String leaseOwner,
                        LocalDateTime leaseUntil, String errorMessage, LocalDateTime expireTime) {
    }

    public record Creation(State state, boolean inserted) { }
}
