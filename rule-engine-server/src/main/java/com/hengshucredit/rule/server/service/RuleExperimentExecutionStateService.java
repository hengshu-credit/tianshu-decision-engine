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
    @Resource private RuleExecutionStateService ruleStates;
    @Resource private ExecutionLeaseHeartbeat heartbeat;
    private final ThreadLocal<Decision> active = new ThreadLocal<>();

    public <T> T withLease(Decision decision, java.util.function.Supplier<T> job) {
        Decision previous = active.get();
        try (var lease = heartbeat != null && decision != null && decision.isClaimed()
                ? heartbeat.track(true, decision.state.id(), decision.owner, 120) : null) {
            active.set(decision);
            return job.get();
        } finally {
            if (previous == null) active.remove(); else active.set(previous);
        }
    }

    VariableResolutionInvocationCache routingCache(String traceId) {
        Decision decision = active.get();
        if (decision == null || !decision.isClaimed() || !traceId.equals(decision.getTraceId())) return null;
        if (decision.routingCalls != null) return decision.routingCalls;
        var cache = new VariableResolutionInvocationCache();
        if (decision.getAttemptNo() > 1 && !"CACHE_THEN_CALL".equals(decision.externalRecoveryMode)) cache.restoreCompletedSteps(ruleStates.externalSteps(traceId));
        cache.setSourceStepListener(step -> {
            ruleStates.persistExperimentStep(decision.state.id(), decision.owner, traceId, step);
        });
        decision.routingCalls = cache;
        return cache;
    }

    boolean hasLease() { return active.get() != null && active.get().isClaimed(); }

    String activeTrace() { return hasLease() ? active.get().getTraceId() : null; }

    public static class ConfigurationConflict extends IllegalStateException {
        ConfigurationConflict(String message) { super(message); }
    }

    void verifyConfig(String digest) {
        if (hasLease() && !java.util.Objects.equals(active.get().configDigest, digest))
            throw new ConfigurationConflict("接单时的实验配置已变更，请核对原版本后恢复");
    }

    void pinStrategy(Long groupId, com.hengshucredit.rule.model.entity.RulePublished rule) {
        pinStrategy("$.strategy", groupId, rule);
    }

    void pinTestStrategy(Long groupId, com.hengshucredit.rule.model.entity.RulePublished rule) {
        pinStrategy("$.testStrategy" + groupId, groupId, rule);
    }

    private void pinStrategy(String path, Long groupId, com.hengshucredit.rule.model.entity.RulePublished rule) {
        if (!hasLease()) return;
        Decision decision = active.get();
        var selection = new java.util.LinkedHashMap<String,Object>();
        selection.put("groupId", groupId);
        selection.put("definitionId", rule.getDefinitionId());
        selection.put("revisionId", rule.getRevisionId());
        selection.put("artifactDigest", rule.getArtifactDigest());
        String pin = CanonicalJson.write(selection);
        // Lease and selection are checked in the same write, before any group source or action starts.
        int changed = jdbcTemplate.update("UPDATE rule_experiment_execution_state SET dispatch_json=JSON_SET(COALESCE(dispatch_json,JSON_OBJECT()),?,CAST(? AS JSON)), "
                        + "lease_until=DATE_ADD(NOW(6),INTERVAL 120 SECOND) WHERE id=? AND lease_owner=? AND lease_until>NOW(6) "
                        + "AND (JSON_EXTRACT(dispatch_json,?) IS NULL OR JSON_EXTRACT(dispatch_json,?)=CAST(? AS JSON))",
                path, pin, decision.state.id(), decision.owner, path, path, pin);
        if (changed != 1) throw new ConfigurationConflict("实验订单执行权、分组或策略修订已变更，请核对后恢复");
    }

    void explicitFreshPolicy() {
        if (hasLease()) active.get().externalRecoveryMode = "CACHE_THEN_CALL";
    }

    String externalRecoveryMode() { return active.get() == null ? null : active.get().externalRecoveryMode; }

    @org.springframework.transaction.annotation.Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Decision enqueue(Long projectId, Long experimentId, String experimentCode, RuleExperimentExecuteRequest request,
                            String configDigest, ExecutionDispatch dispatch) {
        boolean keyed = request.getRequestKey() != null && !request.getRequestKey().isBlank();
        String key = sha256(keyed ? request.getRequestKey().trim() : UUID.randomUUID().toString());
        String json = CanonicalJson.write(request);
        String digest = sha256(json);
        if (keyed) deleteExpired(projectId, experimentId, key);
        String trace = com.hengshucredit.rule.core.trace.TraceIdGenerator.generate("EX", "P",
                com.hengshucredit.rule.core.trace.TraceIdGenerator.projectScopeCode(projectId));
        jdbcTemplate.update("INSERT INTO rule_experiment_execution_state "
                        + "(project_id,experiment_id,experiment_code,request_key_hash,request_digest,request_json,experiment_trace_id,config_digest,"
                        + "status,attempt_no,dispatch_json,expire_time) VALUES (?,?,?,?,?,?,?,?,'QUEUED',0,?,DATE_ADD(NOW(6),INTERVAL 1 DAY)) "
                        + "ON DUPLICATE KEY UPDATE id=id",
                projectId, experimentId, experimentCode, key, digest, json, trace, configDigest, CanonicalJson.write(dispatch));
        var state = find(projectId, experimentId, key);
        if (state == null) throw new IllegalStateException("实验订单登记失败");
        if (!digest.equals(state.requestDigest())) return Decision.conflict();
        if ("SUCCEEDED".equals(state.status()) && state.resultJson() != null)
            return Decision.completed(state, com.alibaba.fastjson.JSON.parseObject(state.resultJson(), RuleExperimentExecuteResult.class));
        if (!configDigest.equals(state.configDigest()) && state.dispatchJson()!=null && !canAdoptRoutingRepair(state, request)) return Decision.conflict();
        if ("QUEUED".equals(state.status()) || "RUNNING".equals(state.status()) && Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT lease_until<NOW(6) FROM rule_experiment_execution_state WHERE id=?", Boolean.class, state.id())))
            return Decision.queued(state);
        if (state.leaseOwner() == null && jdbcTemplate.update("UPDATE rule_experiment_execution_state SET status='QUEUED',lease_until=NULL,"
                        + "dispatch_json=JSON_MERGE_PATCH(COALESCE(dispatch_json,JSON_OBJECT()),CAST(? AS JSON)),request_json=COALESCE(request_json,?),config_digest=?,finish_time=NULL,expire_time=DATE_ADD(NOW(6),INTERVAL 1 DAY) "
                        + "WHERE id=? AND attempt_no=? AND status=? AND config_digest=? AND lease_owner IS NULL",
                CanonicalJson.write(dispatch), json, configDigest, state.id(), state.attemptNo(), state.status(), state.configDigest()) == 1)
            return Decision.queued(state);
        return Decision.inProgress(state);
    }

    @org.springframework.transaction.annotation.Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Decision begin(Long projectId, Long experimentId, String experimentCode,
                          RuleExperimentExecuteRequest request, String configDigest,
                          String traceId) {
        String requestKey = request == null ? null : request.getRequestKey();
        boolean keyed = requestKey != null && !requestKey.isBlank();
        if (!keyed && traceId == null) return Decision.disabled();
        State original = traceId == null ? null : readState("experiment_trace_id=? AND project_id=? AND experiment_id=?", traceId, projectId, experimentId);
        if (traceId != null && original == null) return Decision.conflict();
        String keyHash = original == null ? sha256(requestKey.trim()) : original.keyHash();
        String requestJson = CanonicalJson.write(request);
        String requestDigest = sha256(requestJson);
        if (original == null) deleteExpired(projectId, experimentId, keyHash);
        State state = original == null ? find(projectId, experimentId, keyHash) : original;
        if (state == null) {
            String owner = UUID.randomUUID().toString();
            String rootTrace = traceId == null || traceId.isBlank() ? com.hengshucredit.rule.core.trace.TraceIdGenerator.generate(
                    com.hengshucredit.rule.core.trace.TraceIdGenerator.moduleTypeCode("EXPERIMENT"), "P",
                    com.hengshucredit.rule.core.trace.TraceIdGenerator.projectScopeCode(projectId)) : traceId;
            int inserted = jdbcTemplate.update("INSERT IGNORE INTO rule_experiment_execution_state "
                            + "(project_id, experiment_id, experiment_code, request_key_hash, request_digest, request_json, "
                            + "experiment_trace_id, config_digest, status, attempt_no, lease_owner, lease_until, expire_time) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'RUNNING', 1, ?, DATE_ADD(NOW(6), INTERVAL 120 SECOND), "
                            + "DATE_ADD(NOW(6), INTERVAL 86400 SECOND))",
                    projectId, experimentId, experimentCode, keyHash, requestDigest, requestJson, rootTrace, configDigest, owner);
            state = find(projectId, experimentId, keyHash);
            if (inserted == 1) return Decision.claimed(state, owner, configDigest);
        }
        if (traceId != null && !traceId.equals(state.experimentTraceId())) return Decision.conflict();
        if (!requestDigest.equals(state.requestDigest())) return Decision.conflict();
        if ("SUCCEEDED".equals(state.status()) && state.resultJson() != null) {
            return Decision.completed(state, JSON.parseObject(state.resultJson(), RuleExperimentExecuteResult.class));
        }
        if (!configDigest.equals(state.configDigest()) && state.dispatchJson() != null && !canAdoptRoutingRepair(state, request)) return Decision.conflict();
        String owner = UUID.randomUUID().toString();
        boolean claimed = jdbcTemplate.update("UPDATE rule_experiment_execution_state SET lease_owner=?, "
                        + "lease_until=DATE_ADD(NOW(6), INTERVAL 120 SECOND), expire_time=DATE_ADD(NOW(6), INTERVAL 86400 SECOND), "
                        + "attempt_no=attempt_no+1, config_digest=?, request_json=COALESCE(request_json,?), status='RUNNING', result_json=NULL, finish_time=NULL "
                        + "WHERE id=? AND (status<>'SUCCEEDED' OR config_digest<>?) "
                        + "AND (lease_owner IS NULL OR lease_until<NOW(6) OR lease_owner=?) AND attempt_no=? "
                        + "AND (dispatch_json IS NULL OR config_digest=? OR (status='PAUSED_ROUTING' AND JSON_EXTRACT(dispatch_json,'$.strategy') IS NULL))",
                owner, configDigest, requestJson, state.id(), configDigest, owner, state.attemptNo(), configDigest) == 1;
        if (!claimed && !configDigest.equals(state.configDigest())) return Decision.conflict();
        if (!claimed) return Decision.inProgress(state);
        return Decision.claimed(find(projectId, experimentId, keyHash), owner, configDigest);
    }

    public Decision claimAccepted(long id, int attempt, String trace) {
        String owner = UUID.randomUUID().toString();
        int changed = jdbcTemplate.update("UPDATE rule_experiment_execution_state SET status='RUNNING',attempt_no=attempt_no+1,"
                        + "lease_owner=?,lease_until=DATE_ADD(NOW(6),INTERVAL 120 SECOND),expire_time=DATE_ADD(NOW(6),INTERVAL 1 DAY),"
                        + "finish_time=NULL,result_json=NULL WHERE id=? AND experiment_trace_id=? AND attempt_no=? AND "
                        + "(status='QUEUED' OR (status IN ('RUNNING','WAITING_EXTERNAL') AND lease_until<=NOW(6)))", owner,id,trace,attempt);
        if (changed != 1) return null;
        State claimed = readState("id=? AND lease_owner=?", id,owner);
        if (claimed == null) throw new IllegalStateException("实验订单执行权已失效");
        return Decision.claimed(claimed,owner,claimed.configDigest());
    }

    public void awaitStrategy(Decision decision) {
        jdbcTemplate.update("UPDATE rule_experiment_execution_state SET status='QUEUED',lease_owner=NULL,"
                        + "lease_until=DATE_ADD(NOW(6),INTERVAL 3 SECOND) WHERE id=? AND lease_owner=? AND lease_until>NOW(6)",
                decision.state.id(), decision.owner);
    }

    public static class StrategyInProgress extends RuntimeException {}

    boolean canPollOriginal(String trace, RuleExperimentExecuteResult result) {
        if (result == null || result.routingPaused() || result.platformStatus() != com.hengshucredit.rule.model.PlatformCode.RESULT_PENDING) return false;
        String child = result.getProductionGroup() == null ? trace : result.getProductionGroup().getTraceId();
        return child != null && ruleStates.canPollOriginal(trace, child);
    }

    public void complete(Decision decision, RuleExperimentExecuteResult result) {
        if (decision == null || !decision.claimed) return;
        int changed = jdbcTemplate.update("UPDATE rule_experiment_execution_state SET status=?, result_json=?, "
                        + "error_message=?, lease_owner=NULL, lease_until=CASE WHEN ? THEN DATE_ADD(NOW(6),INTERVAL 30 SECOND) ELSE NULL END, finish_time=NOW(6) "
                        + "WHERE id=? AND lease_owner=? AND lease_until>NOW(6)",
                result != null && result.isSuccess() ? "SUCCEEDED"
                        : result != null && result.routingPaused() ? "PAUSED_ROUTING"
                        : result != null && result.platformStatus() == com.hengshucredit.rule.model.PlatformCode.RESULT_PENDING
                        ? "WAITING_EXTERNAL" : "FAILED_RETRYABLE",
                JSON.toJSONString(result), result == null ? "实验结果为空" : result.getErrorMessage(),
                !"CACHE_THEN_CALL".equals(decision.externalRecoveryMode) && canPollOriginal(decision.getTraceId(), result), decision.state.id(), decision.owner);
        if (changed != 1) throw new IllegalStateException("实验执行租约已失效，无法提交旧尝试结果");
    }

    private boolean canAdoptRoutingRepair(State state, RuleExperimentExecuteRequest request) {
        return "PAUSED_ROUTING".equals(state.status()) && request != null && !"FIXED".equals(request.getVersionMode())
                && state.dispatchJson() != null && JSON.parseObject(state.dispatchJson()).get("strategy") == null;
    }

    public void release(Decision decision, String errorMessage) {
        if (decision == null || !decision.claimed) return;
        jdbcTemplate.update("UPDATE rule_experiment_execution_state SET status='FAILED_RETRYABLE', "
                        + "error_message=?, lease_owner=NULL, lease_until=NULL, finish_time=NOW(6) "
                        + "WHERE id=? AND lease_owner=? AND lease_until>NOW(6)",
                errorMessage, decision.state.id(), decision.owner);
    }

    public record TraceRequest(Long projectId, String experimentCode, RuleExperimentExecuteRequest request, String status,
                               int attemptNo, String resultJson, String error, LocalDateTime expireTime) {}

    public TraceRequest byTrace(String traceId) {
        return jdbcTemplate.query("SELECT project_id,experiment_code,request_json,status,attempt_no,result_json,error_message,expire_time "
                + "FROM rule_experiment_execution_state WHERE experiment_trace_id=? AND (expire_time>NOW(6) OR status IN ('QUEUED','RUNNING') OR dispatch_json IS NOT NULL AND status<>'SUCCEEDED')",
                (rs, ignored) -> new TraceRequest(rs.getLong(1), rs.getString(2),
                        JSON.parseObject(rs.getString(3), RuleExperimentExecuteRequest.class), rs.getString(4),
                        rs.getInt(5), rs.getString(6), rs.getString(7), rs.getObject(8, LocalDateTime.class)), traceId)
                .stream().findFirst().orElse(null);
    }

    public State find(Long projectId, Long experimentId, String keyHash) {
        return readState("project_id=? AND experiment_id=? AND request_key_hash=?", projectId,experimentId,keyHash);
    }

    private State readState(String predicate, Object... args) {
        return jdbcTemplate.query("SELECT id, project_id, experiment_id, experiment_code, request_key_hash, request_digest, "
                        + "experiment_trace_id, config_digest, status, attempt_no, result_json, error_message, lease_owner, lease_until, expire_time, dispatch_json "
                        + "FROM rule_experiment_execution_state WHERE " + predicate + " LIMIT 1",
                (rs, row) -> new State(rs.getLong("id"), rs.getLong("project_id"), rs.getLong("experiment_id"),
                        rs.getString("experiment_code"), rs.getString("request_key_hash"), rs.getString("request_digest"),
                        rs.getString("experiment_trace_id"), rs.getString("config_digest"), rs.getString("status"),
                        rs.getInt("attempt_no"), rs.getString("result_json"), rs.getString("error_message"),
                        rs.getString("lease_owner"), rs.getObject("lease_until", LocalDateTime.class),
                        rs.getObject("expire_time", LocalDateTime.class), rs.getString("dispatch_json")), args)
                .stream().findFirst().orElse(null);
    }

    private void deleteExpired(Long projectId, Long experimentId, String keyHash) {
        var traces = jdbcTemplate.queryForList("SELECT experiment_trace_id FROM rule_experiment_execution_state WHERE project_id=? AND experiment_id=? "
                        + "AND request_key_hash=? AND expire_time<=NOW(6) AND (status='SUCCEEDED' OR dispatch_json IS NULL AND status NOT IN ('QUEUED','RUNNING')) "
                        + "AND (lease_until IS NULL OR lease_until<=NOW(6)) FOR UPDATE SKIP LOCKED", String.class, projectId, experimentId, keyHash);
        ruleStates.deleteTraces(true, traces);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(digest.length * 2);
            for (byte b : digest) out.append(String.format("%02x", b & 0xff));
            return out.toString();
        } catch (Exception e) { throw new IllegalStateException("实验幂等键摘要失败", e); }
    }

    public enum Status { DISABLED, QUEUED, CLAIMED, COMPLETED, CONFLICT, IN_PROGRESS }

    public static final class Decision {
        ExecutionDispatch dispatch() {
            return state == null || state.dispatchJson() == null ? null : JSON.parseObject(state.dispatchJson(), ExecutionDispatch.class);
        }
        public String acceptedRecoveryMode() {
            var options = dispatch();
            return options == null ? null : options.externalRecoveryMode();
        }
        private String externalRecoveryMode;
        private VariableResolutionInvocationCache routingCalls;
        public void setExternalRecoveryMode(String mode) { externalRecoveryMode = mode; }
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
        static Decision inProgress(State state) { return new Decision(false, state, null, null, Status.IN_PROGRESS, state.configDigest()); }
        static Decision queued(State state) { return new Decision(false, state, null, null, Status.QUEUED, state.configDigest()); }
        public Status getStatus() { return status; }
        public RuleExperimentExecuteResult getResult() { return result; }
        public String getTraceId() { return state == null ? null : state.experimentTraceId(); }
        public long getStateId() { return state.id(); }
        public int getAttemptNo() { return state == null ? 1 : state.attemptNo(); }
        public boolean isClaimed() { return claimed; }
    }

    public record State(Long id, Long projectId, Long experimentId, String experimentCode,
                        String keyHash, String requestDigest, String experimentTraceId,
                        String configDigest, String status, int attemptNo, String resultJson,
                        String errorMessage, String leaseOwner, LocalDateTime leaseUntil,
                        LocalDateTime expireTime, String dispatchJson) { }
}
