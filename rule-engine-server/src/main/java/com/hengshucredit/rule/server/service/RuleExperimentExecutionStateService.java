package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.dto.RuleExperimentExecuteResult;
import com.hengshucredit.rule.model.dto.RuleExperimentExecuteRequest;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/** 项目运行实验的跨节点幂等协调器。 */
@Service
public class RuleExperimentExecutionStateService {
    @Resource private JdbcTemplate jdbcTemplate;

    public Decision begin(Long projectId, Long experimentId, String experimentCode,
                          RuleExperimentExecuteRequest request, String configDigest,
                          String traceId) {
        String requestKey = request == null ? null : request.getRequestKey();
        if (requestKey == null || requestKey.isBlank()) return Decision.disabled();
        String keyHash = sha256(requestKey.trim());
        String requestDigest = sha256(CanonicalJson.write(request == null ? Map.of() : request));
        deleteExpired(projectId, experimentId, keyHash);
        State state = find(projectId, experimentId, keyHash);
        if (state == null) {
            String owner = UUID.randomUUID().toString();
            String rootTrace = traceId == null || traceId.isBlank() ? com.hengshucredit.rule.core.trace.TraceIdGenerator.generate(
                    com.hengshucredit.rule.core.trace.TraceIdGenerator.moduleTypeCode("EXPERIMENT"), "P",
                    com.hengshucredit.rule.core.trace.TraceIdGenerator.projectScopeCode(projectId)) : traceId;
            int inserted = jdbcTemplate.update("INSERT IGNORE INTO rule_experiment_execution_state "
                            + "(project_id, experiment_id, experiment_code, request_key_hash, request_digest, "
                            + "experiment_trace_id, config_digest, status, attempt_no, lease_owner, lease_until, expire_time) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, 'RUNNING', 1, ?, DATE_ADD(NOW(6), INTERVAL 120 SECOND), "
                            + "DATE_ADD(NOW(6), INTERVAL 86400 SECOND))",
                    projectId, experimentId, experimentCode, keyHash, requestDigest, rootTrace, configDigest, owner);
            state = find(projectId, experimentId, keyHash);
            if (inserted == 1) return Decision.claimed(state, owner, configDigest);
        }
        if (!requestDigest.equals(state.requestDigest())) return Decision.conflict();
        if ("SUCCEEDED".equals(state.status()) && configDigest.equals(state.configDigest()) && state.resultJson() != null) {
            return Decision.completed(state, JSON.parseObject(state.resultJson(), RuleExperimentExecuteResult.class));
        }
        String owner = UUID.randomUUID().toString();
        boolean claimed = jdbcTemplate.update("UPDATE rule_experiment_execution_state SET lease_owner=?, "
                        + "lease_until=DATE_ADD(NOW(6), INTERVAL 120 SECOND), expire_time=DATE_ADD(NOW(6), INTERVAL 86400 SECOND), "
                        + "attempt_no=attempt_no+1, config_digest=?, status='RUNNING', result_json=NULL, finish_time=NULL "
                        + "WHERE id=? AND (lease_until IS NULL OR lease_until<NOW(6) OR lease_owner=?)",
                owner, configDigest, state.id(), owner) == 1;
        if (!claimed) return Decision.inProgress();
        return Decision.claimed(find(projectId, experimentId, keyHash), owner, configDigest);
    }

    public void complete(Decision decision, RuleExperimentExecuteResult result) {
        if (decision == null || !decision.claimed) return;
        jdbcTemplate.update("UPDATE rule_experiment_execution_state SET status=?, result_json=?, "
                        + "error_message=?, lease_owner=NULL, lease_until=NULL, finish_time=NOW(6) "
                        + "WHERE id=? AND lease_owner=? AND lease_until>NOW(6)",
                result != null && result.isSuccess() ? "SUCCEEDED" : "FAILED_RETRYABLE",
                JSON.toJSONString(result), result == null ? "实验结果为空" : result.getErrorMessage(),
                decision.state.id(), decision.owner);
    }

    public void release(Decision decision, String errorMessage) {
        if (decision == null || !decision.claimed) return;
        jdbcTemplate.update("UPDATE rule_experiment_execution_state SET status='FAILED_RETRYABLE', "
                        + "error_message=?, lease_owner=NULL, lease_until=NULL, finish_time=NOW(6) "
                        + "WHERE id=? AND lease_owner=? AND lease_until>NOW(6)",
                errorMessage, decision.state.id(), decision.owner);
    }

    public State find(Long projectId, Long experimentId, String keyHash) {
        return jdbcTemplate.query("SELECT id, project_id, experiment_id, experiment_code, request_key_hash, request_digest, "
                        + "experiment_trace_id, config_digest, status, attempt_no, result_json, error_message, lease_owner, lease_until, expire_time "
                        + "FROM rule_experiment_execution_state WHERE project_id=? AND experiment_id=? AND request_key_hash=? LIMIT 1",
                (rs, row) -> new State(rs.getLong("id"), rs.getLong("project_id"), rs.getLong("experiment_id"),
                        rs.getString("experiment_code"), rs.getString("request_key_hash"), rs.getString("request_digest"),
                        rs.getString("experiment_trace_id"), rs.getString("config_digest"), rs.getString("status"),
                        rs.getInt("attempt_no"), rs.getString("result_json"), rs.getString("error_message"),
                        rs.getString("lease_owner"), rs.getObject("lease_until", LocalDateTime.class),
                        rs.getObject("expire_time", LocalDateTime.class)), projectId, experimentId, keyHash)
                .stream().findFirst().orElse(null);
    }

    private void deleteExpired(Long projectId, Long experimentId, String keyHash) {
        jdbcTemplate.update("DELETE FROM rule_experiment_execution_state WHERE project_id=? AND experiment_id=? "
                        + "AND request_key_hash=? AND expire_time<=NOW(6) AND status<>'RUNNING' "
                        + "AND (lease_until IS NULL OR lease_until<=NOW(6))", projectId, experimentId, keyHash);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(digest.length * 2);
            for (byte b : digest) out.append(String.format("%02x", b & 0xff));
            return out.toString();
        } catch (Exception e) { throw new IllegalStateException("实验幂等键摘要失败", e); }
    }

    public enum Status { DISABLED, CLAIMED, COMPLETED, CONFLICT, IN_PROGRESS }

    public static final class Decision {
        private final boolean claimed;
        private final State state;
        private final String owner;
        private final RuleExperimentExecuteResult result;
        private final Status status;
        private final String configDigest;

        private Decision(boolean claimed, State state, String owner, RuleExperimentExecuteResult result,
                         Status status, String configDigest) {
            this.claimed = claimed; this.state = state; this.owner = owner;
            this.result = result; this.status = status; this.configDigest = configDigest;
        }

        static Decision disabled() { return new Decision(false, null, null, null, Status.DISABLED, null); }
        static Decision claimed(State state, String owner, String digest) { return new Decision(true, state, owner, null, Status.CLAIMED, digest); }
        static Decision completed(State state, RuleExperimentExecuteResult result) { return new Decision(false, state, null, result, Status.COMPLETED, state.configDigest()); }
        static Decision conflict() { return new Decision(false, null, null, null, Status.CONFLICT, null); }
        static Decision inProgress() { return new Decision(false, null, null, null, Status.IN_PROGRESS, null); }
        public Status getStatus() { return status; }
        public RuleExperimentExecuteResult getResult() { return result; }
        public String getTraceId() { return state == null ? null : state.experimentTraceId(); }
        public int getAttemptNo() { return state == null ? 1 : state.attemptNo(); }
        public boolean isClaimed() { return claimed; }
    }

    public record State(Long id, Long projectId, Long experimentId, String experimentCode,
                        String keyHash, String requestDigest, String experimentTraceId,
                        String configDigest, String status, int attemptNo, String resultJson,
                        String errorMessage, String leaseOwner, LocalDateTime leaseUntil,
                        LocalDateTime expireTime) { }
}
