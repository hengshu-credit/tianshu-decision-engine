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
                        + "current_artifact_digest, checkpoint_json, result_json, lease_owner, lease_until, error_message, expire_time, dispatch_json "
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
                        + "current_artifact_digest, checkpoint_json, result_json, lease_owner, lease_until, error_message, expire_time, dispatch_json "
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
                rs.getString("error_message"), rs.getObject("expire_time", LocalDateTime.class), rs.getString("dispatch_json"));
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
        return create(projectId, definitionId, ruleCode, keyHash, requestDigest, rootTraceId, revisionId, artifactDigest,
                owner, leaseSeconds, retentionSeconds, null);
    }

    Creation create(Long projectId, Long definitionId, String ruleCode, String keyHash, String requestDigest,
                    String rootTraceId, Long revisionId, String artifactDigest, String owner,
                    int leaseSeconds, int retentionSeconds, String experimentScope) {
        String parent = experimentScope == null ? null : experimentScope.split(":", 2)[0];
        String dispatch = parent == null ? null : CanonicalJson.write(java.util.Map.of("parentExperimentTrace", parent));
        int inserted = jdbcTemplate.update(
                    "INSERT IGNORE INTO rule_execution_state "
                            + "(project_id, definition_id, rule_code, idempotency_key_hash, request_digest, "
                            + "root_trace_id, status, attempt_no, initial_revision_id, current_revision_id, "
                            + "current_artifact_digest, lease_owner, lease_until, expire_time, create_time, update_time, dispatch_json) "
                    + "VALUES (?, ?, ?, ?, ?, ?, 'RUNNING', 1, ?, ?, ?, ?, DATE_ADD(NOW(6), INTERVAL ? SECOND), "
                    + "DATE_ADD(NOW(6), INTERVAL ? SECOND), NOW(6), NOW(6), ?)",
                    projectId, definitionId, ruleCode, keyHash, requestDigest, rootTraceId,
                    revisionId, revisionId, artifactDigest, owner, leaseSeconds, retentionSeconds, dispatch);
        State state = find(projectId, definitionId, keyHash);
        if (state == null) throw new IllegalStateException("无法创建或读取规则执行状态");
        return new Creation(state, inserted == 1);
    }

    public void enqueue(Long projectId, Long definitionId, String ruleCode, String key, String digest,
                        String trace, Long revision, String artifact, int ttl, String checkpoint, ExecutionDispatch dispatch) {
        jdbcTemplate.update("INSERT INTO rule_execution_state (project_id,definition_id,rule_code,idempotency_key_hash,request_digest,"
                        + "root_trace_id,status,attempt_no,initial_revision_id,current_revision_id,current_artifact_digest,checkpoint_json,dispatch_json,expire_time) "
                        + "VALUES (?,?,?,?,?,?,'QUEUED',0,?,?,?,?,?,DATE_ADD(NOW(6),INTERVAL ? SECOND)) ON DUPLICATE KEY UPDATE id=id",
                projectId, definitionId, ruleCode, key, digest, trace, revision, revision, artifact, checkpoint, CanonicalJson.write(dispatch), ttl);
    }

    boolean queueRetry(State state, Long revision, String artifact, ExecutionDispatch dispatch) {
        return jdbcTemplate.update("UPDATE rule_execution_state SET status='QUEUED',current_revision_id=?,"
                        + "current_artifact_digest=?,dispatch_json=?,finish_time=NULL "
                        + "WHERE id=? AND status=? AND attempt_no=? AND lease_owner IS NULL AND current_artifact_digest <=> ?",
                revision, artifact, CanonicalJson.write(dispatch), state.id(), state.status(), state.attemptNo(), state.artifactDigest()) == 1;
    }

    @org.springframework.transaction.annotation.Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void deleteExpired(Long projectId, Long definitionId, String keyHash) {
        var traces = jdbcTemplate.queryForList("SELECT root_trace_id FROM rule_execution_state WHERE project_id=? "
                        + "AND definition_id=? AND idempotency_key_hash=? AND expire_time<=NOW(6) "
                        + "AND (status='SUCCEEDED' OR dispatch_json IS NULL AND status NOT IN ('RUNNING','QUEUED')) "
                        + "AND (lease_until IS NULL OR lease_until<=NOW(6)) FOR UPDATE SKIP LOCKED",
                String.class, projectId, definitionId, keyHash);
        deleteTraces(false, traces);
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
        return claim(id,owner,leaseSeconds,retentionSeconds,currentRevisionId,currentArtifactDigest,null);
    }

    boolean claim(Long id, String owner, int leaseSeconds, int retentionSeconds, Long currentRevisionId,
                  String currentArtifactDigest, Integer acceptedAttempt) {
        return jdbcTemplate.update(
                "UPDATE rule_execution_state SET lease_owner = ?, lease_until = DATE_ADD(NOW(6), INTERVAL ? SECOND), "
                        + "attempt_no = attempt_no + 1, status = 'RUNNING', "
                        + "current_revision_id = COALESCE(?, current_revision_id), "
                        + "current_artifact_digest = COALESCE(?, current_artifact_digest), "
                        + "expire_time = DATE_ADD(NOW(6), INTERVAL ? SECOND), result_json = NULL, "
                        + "finish_time = NULL, update_time = NOW(6) "
                        + "WHERE id = ? AND (status <> 'SUCCEEDED' OR NOT (current_revision_id <=> ? AND current_artifact_digest <=> ?)) "
                        + "AND (lease_until IS NULL OR lease_until <= NOW(6) OR lease_owner = ?) "
                        + "AND (? IS NULL OR (attempt_no=? AND status IN ('QUEUED','RUNNING')))",
                owner, leaseSeconds, currentRevisionId, currentArtifactDigest, retentionSeconds, id,
                currentRevisionId, currentArtifactDigest, owner, acceptedAttempt, acceptedAttempt) == 1;
    }

    public void checkpoint(Long id, String owner, String status, String checkpointJson,
                           Long currentRevisionId, String currentArtifactDigest, String errorMessage) {
        checkpoint(id, owner, status, checkpointJson, currentRevisionId, currentArtifactDigest,
                errorMessage, 120);
    }

    @org.springframework.transaction.annotation.Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void checkpoint(Long id, String owner, String status, String checkpointJson,
                           Long currentRevisionId, String currentArtifactDigest, String errorMessage,
                           int leaseSeconds) {
        int effectiveLeaseSeconds = Math.max(30, Math.min(3600, leaseSeconds));
        int changed = jdbcTemplate.update(
                "UPDATE rule_execution_state SET status = ?, checkpoint_json = ?, "
                        + "current_revision_id = ?, current_artifact_digest = ?, error_message = ?, update_time = NOW(6) "
                        + ", lease_until = DATE_ADD(NOW(6), INTERVAL ? SECOND) "
                        + "WHERE id = ? AND lease_owner = ? AND lease_until > NOW(6)",
                status, checkpointJson, currentRevisionId, currentArtifactDigest, errorMessage,
                effectiveLeaseSeconds, id, owner);
        if (changed != 1) throw new IllegalStateException("执行租约已失效，已停止写入旧断点");
        persistCheckpoint(id);
    }

    private void persistCheckpoint(Long stateId) {
        jdbcTemplate.update(
                "INSERT INTO rule_execution_checkpoint "
                        + "(root_trace_id, step_id, step_type, step_status, source_revision_id, "
                        + "source_artifact_digest, resolved_value, update_time, create_time) "
                        + "SELECT root_trace_id,'__ROOT__','ROOT',status,current_revision_id,current_artifact_digest,checkpoint_json,NOW(6),NOW(6) "
                        + "FROM rule_execution_state WHERE id=? "
                        + "ON DUPLICATE KEY UPDATE step_status = VALUES(step_status), "
                        + "source_revision_id = VALUES(source_revision_id), "
                        + "source_artifact_digest = VALUES(source_artifact_digest), "
                        + "resolved_value = VALUES(resolved_value), update_time = NOW(6)",
                stateId);
    }

    public void persistSourceStep(String rootTraceId, SourceStep step,
                                  Long revisionId, String artifactDigest) {
        writeStep(null, null, rootTraceId, step, revisionId, artifactDigest, false);
    }

    private void writeStep(Long stateId, String owner, String rootTraceId, SourceStep step, Long revisionId, String artifactDigest, boolean experiment) {
        if (rootTraceId == null || step == null || step.getSourceKey() == null) return;
        var args = new java.util.ArrayList<>(java.util.Arrays.asList(rootTraceId, step.getSourceKey(), step.getSourceType(),
                step.getStatus(), step.getInputDigest(), step.getDependencyDigest(), revisionId, artifactDigest,
                "MODEL".equals(step.getSourceType()) ? null : JSON.toJSONString(step.getValue()), encodeResponse(step), stepDetails(step)));
        String source = " SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(6), NOW(6)";
        if (stateId != null) {
            source += (experiment ? " FROM rule_experiment_execution_state" : " FROM rule_execution_state")
                    + " WHERE id=? AND lease_owner=? AND lease_until>NOW(6)";
            args.add(stateId); args.add(owner);
        }
        int changed = jdbcTemplate.update(
                "INSERT INTO rule_execution_checkpoint "
                        + "(root_trace_id, step_id, step_type, step_status, input_digest, dependency_digest, "
                        + "source_revision_id, source_artifact_digest, resolved_value, external_result, attempt_history, update_time, create_time) "
                        + source + " ON DUPLICATE KEY UPDATE step_type = VALUES(step_type), step_status = VALUES(step_status), "
                        + "input_digest = VALUES(input_digest), dependency_digest = VALUES(dependency_digest), "
                        + "source_revision_id = VALUES(source_revision_id), source_artifact_digest = VALUES(source_artifact_digest), "
                        + "resolved_value = VALUES(resolved_value), external_result = VALUES(external_result), "
                        + "attempt_history = VALUES(attempt_history), update_time = NOW(6)",
                args.toArray());
        if (stateId != null && changed == 0) throw new IllegalStateException("订单执行租约已失效，无法记录外数步骤");
    }

    private static String stepDetails(SourceStep step) {
        if ("EXTERNAL_API".equals(step.getSourceType())) return CanonicalJson.write(step.getMetadata());
        var details = new java.util.LinkedHashMap<String,Object>();
        details.put("format", 1);
        details.put("configDigest", step.getConfigDigest());
        details.put("sourceStates", step.getSourceStates());
        details.put("metadata", step.getMetadata());
        return CanonicalJson.write(details);
    }

    private static String encodeResponse(SourceStep step) {
        return "EXTERNAL_API".equals(step.getSourceType()) ? ExternalResponseReceipt.encode(step.getResponse())
                : JSON.toJSONString(step.getResponse());
    }

    void persistLeasedStep(Long stateId, String owner, String trace, SourceStep step, Long revision, String digest) {
        writeStep(stateId, owner, trace, step, revision, digest, false);
    }

    void persistExperimentStep(Long stateId, String owner, String trace, SourceStep step) {
        writeStep(stateId, owner, trace, step, null, null, true);
    }

    /** Reconcile receipts written by parallel suppliers after the most recent root snapshot. */
    public java.util.Map<String, Object> externalSteps(String traceId) {
        java.util.Map<String, Object> steps = new java.util.LinkedHashMap<>();
        jdbcTemplate.query("SELECT step_id,step_type,step_status,input_digest,dependency_digest,resolved_value,external_result,attempt_history "
                        + "FROM rule_execution_checkpoint WHERE root_trace_id=? AND step_type IN ('EXTERNAL_API','VARIABLE','MODEL')", rs -> {
            String key = ExternalApiRequestPlan.checkpointIdentity(rs.getString("step_id"));
            String type = rs.getString("step_type");
            var step = new java.util.LinkedHashMap<String,Object>();
            step.put("sourceType", type);
            step.put("status", rs.getString("step_status"));
            var details = JSON.parseObject(rs.getString("attempt_history"));
            if ("EXTERNAL_API".equals(type)) {
                step.put("response", ExternalResponseReceipt.decode(rs.getString("external_result")));
                step.put("metadata", details);
            } else {
                // Older variable rows lack fingerprints. Their complete root snapshot remains authoritative.
                if (details == null || details.getIntValue("format") != 1) return;
                step.putAll(details);
                step.put("inputDigest", rs.getString("input_digest"));
                step.put("dependencyDigest", rs.getString("dependency_digest"));
                step.put("value", JSON.parse(rs.getString("resolved_value")));
                step.put("response", JSON.parse(rs.getString("external_result")));
            }
            steps.put(key, step);
        }, traceId);
        return steps;
    }

    @org.springframework.transaction.annotation.Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void finish(Long id, String owner, String status, String checkpointJson, String resultJson,
                       String errorMessage) {
        int changed = jdbcTemplate.update(
                "UPDATE rule_execution_state SET status = ?, checkpoint_json = ?, result_json = ?, error_message = ?, "
                        + "lease_owner = NULL, lease_until = NULL, finish_time = NOW(6), update_time = NOW(6) "
                        + "WHERE id = ? AND lease_owner = ? AND lease_until > NOW(6)",
                status, checkpointJson, resultJson, errorMessage, id, owner);
        if (changed != 1) throw new IllegalStateException("执行租约已失效，无法提交旧尝试结果");
        persistCheckpoint(id);
    }

    /** 先锁定已过保留期的成功订单，同事务删除回执。未完成实验的子策略仍可能用于恢复。 */
    @org.springframework.transaction.annotation.Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void purgeCompleted(boolean experiment) {
        String table = experiment ? "rule_experiment_execution_state" : "rule_execution_state";
        String traceColumn = experiment ? "experiment_trace_id" : "root_trace_id";
        String parentCheck = experiment ? "" : " AND NOT EXISTS (SELECT 1 FROM rule_experiment_execution_state parent "
                + "WHERE parent.experiment_trace_id=JSON_UNQUOTE(JSON_EXTRACT(s.dispatch_json,'$.parentExperimentTrace')) "
                + "AND (parent.status<>'SUCCEEDED' OR parent.expire_time>NOW(6)))";
        var traces = jdbcTemplate.queryForList("SELECT " + traceColumn + " FROM " + table + " s WHERE s.expire_time<=NOW(6) "
                + "AND s.status='SUCCEEDED' AND s.dispatch_json IS NOT NULL" + parentCheck
                + " ORDER BY s.expire_time LIMIT 16 FOR UPDATE SKIP LOCKED", String.class);
        deleteTraces(experiment, traces);
    }

    void deleteTraces(boolean experiment, List<String> traces) {
        if (traces.isEmpty()) return;
        String table = experiment ? "rule_experiment_execution_state" : "rule_execution_state";
        String traceColumn = experiment ? "experiment_trace_id" : "root_trace_id";
        String placeholders = String.join(",", java.util.Collections.nCopies(traces.size(), "?"));
        jdbcTemplate.update("DELETE FROM rule_execution_checkpoint WHERE root_trace_id IN (" + placeholders + ")", traces.toArray());
        jdbcTemplate.update("DELETE FROM " + table + " WHERE " + traceColumn + " IN (" + placeholders + ")", traces.toArray());
    }

    public record State(Long id, Long projectId, Long definitionId, String ruleCode,
                        String keyHash, String requestDigest, String rootTraceId, String status,
                        int attemptNo, Long initialRevisionId, Long currentRevisionId,
                        String artifactDigest, String checkpointJson, String resultJson, String leaseOwner,
                        LocalDateTime leaseUntil, String errorMessage, LocalDateTime expireTime, String dispatchJson) {
        public State(Long id, Long projectId, Long definitionId, String ruleCode, String keyHash, String requestDigest,
                     String rootTraceId, String status, int attemptNo, Long initialRevisionId, Long currentRevisionId,
                     String artifactDigest, String checkpointJson, String resultJson, String leaseOwner, LocalDateTime leaseUntil,
                     String errorMessage, LocalDateTime expireTime) {
            this(id, projectId, definitionId, ruleCode, keyHash, requestDigest, rootTraceId, status, attemptNo, initialRevisionId,
                    currentRevisionId, artifactDigest, checkpointJson, resultJson, leaseOwner, leaseUntil, errorMessage, expireTime, null);
        }
        public ExecutionDispatch dispatch() { return dispatchJson == null || JSON.parseObject(dispatchJson).containsKey("parentExperimentTrace") ? null : JSON.parseObject(dispatchJson, ExecutionDispatch.class); }
    }

    public record Creation(State state, boolean inserted) { }
}
