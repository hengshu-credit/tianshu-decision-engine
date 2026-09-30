package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.core.trace.TraceIdGenerator;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.openapi.OpenApiContract;
import com.hengshucredit.rule.server.openapi.OpenApiContractCodec;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;

/** 规则显式幂等键和跨节点恢复状态协调器。 */
@Service
public class RuleIdempotencyService {
    private final ThreadLocal<Decision> current = new ThreadLocal<>();

    @Resource private RuleDefinitionService definitionService;
    @Resource private RuleVariableService variableService;
    @Resource private RuleProjectService projectService;
    @Resource private RuleExecutionStateService stateService;

    public Decision begin(RulePublished published, Long projectId, Map<String, Object> params) {
        OpenApiContract.IdempotencyConfig config = config(published);
        if (config == null || !config.isEnabled()) {
            current.remove();
            return Decision.disabled();
        }
        IdempotencyKeyCalculator.Result calculatedKey = resolveKey(published, projectId, params, config);
        String keyHash = calculatedKey.keyHash();
        String requestHash = sha256(CanonicalJson.write(params == null ? Map.of() : params));
        Long stateProjectId = projectId == null ? 0L : projectId;
        int leaseSeconds = executionLeaseSeconds();
        stateService.deleteExpired(stateProjectId, published.getDefinitionId(), keyHash);
        RuleExecutionStateService.State state = stateService.find(stateProjectId, published.getDefinitionId(), keyHash);
        try {
            if (state == null) {
                String rootTraceId = newRootTrace(published, projectId);
                String owner = UUID.randomUUID().toString();
                RuleExecutionStateService.Creation creation = stateService.create(stateProjectId, published.getDefinitionId(), published.getRuleCode(), keyHash,
                        requestHash, rootTraceId, published.getRevisionId(), published.getArtifactDigest(), owner, leaseSeconds,
                        config.getTtlSeconds());
                state = creation.state();
                if (creation.inserted()) {
                    Decision decision = Decision.claimed(state, owner, config.getTtlSeconds(), leaseSeconds,
                            published.getRevisionId(), published.getArtifactDigest());
                    current.set(decision);
                    return decision;
                }
            }
            if (!requestHash.equals(state.requestDigest())) {
                current.remove();
                return Decision.conflict();
            }
            boolean samePublished = Objects.equals(state.currentRevisionId(), published.getRevisionId())
                    && Objects.equals(state.artifactDigest(), published.getArtifactDigest());
            if ("SUCCEEDED".equals(state.status()) && state.resultJson() != null && samePublished) {
                Decision decision = Decision.completed(state, JSON.parseObject(state.resultJson(), RuleResult.class));
                current.remove();
                return decision;
            }
            String owner = UUID.randomUUID().toString();
            boolean claimed = stateService.claim(state.id(), owner, leaseSeconds, config.getTtlSeconds(),
                    published.getRevisionId(), published.getArtifactDigest());
            if (!claimed) {
                current.remove();
                return Decision.inProgress(state);
            }
            RuleExecutionStateService.State claimedState = stateService.find(
                    stateProjectId, published.getDefinitionId(), keyHash);
            if (claimedState != null) state = claimedState;
            Decision decision = Decision.claimed(state, owner, config.getTtlSeconds(), leaseSeconds,
                    published.getRevisionId(), published.getArtifactDigest());
            current.set(decision);
            return decision;
        } catch (RuntimeException error) {
            current.remove();
            throw error;
        }
    }

    public Decision current() { return current.get(); }

    /** 记录原始订单入参，支持仅凭 trace_id 跨节点恢复；不改变幂等摘要。 */
    public void recordRequestParams(Decision decision, Map<String, Object> params) {
        if (decision == null || !decision.claimed) return;
        decision.requestParams = params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> requestParams(RuleExecutionStateService.State state) {
        if (state == null || state.checkpointJson() == null || state.checkpointJson().isBlank()) return null;
        JSONObject checkpoint = JSON.parseObject(state.checkpointJson());
        Map<String, Object> params = checkpoint.getObject("requestParams", Map.class);
        return params == null ? null : new LinkedHashMap<>(params);
    }

    /** 按已持久化的 root trace 恢复一次可重试执行，要求入参摘要与原订单一致。 */
    public Decision resumeByTrace(RuleExecutionStateService.State state,
                                  RulePublished published,
                                  Map<String, Object> params) {
        if (state == null || published == null) throw new IllegalArgumentException("订单执行状态或规则版本不存在");
        if (params == null) params = requestParams(state);
        if (params == null) throw new IllegalArgumentException("订单检查点未保存原始入参，无法仅凭 trace_id 恢复");
        String requestHash = sha256(CanonicalJson.write(params == null ? Map.of() : params));
        if (!Objects.equals(requestHash, state.requestDigest())) {
            throw new IllegalArgumentException("恢复入参与原订单不一致，拒绝重复或串单执行");
        }
        OpenApiContract.IdempotencyConfig config = config(published);
        int ttlSeconds = config == null ? 86400 : config.getTtlSeconds();
        String owner = UUID.randomUUID().toString();
        int leaseSeconds = executionLeaseSeconds();
        if (!stateService.claim(state.id(), owner, leaseSeconds, ttlSeconds,
                published.getRevisionId(), published.getArtifactDigest())) {
            return Decision.inProgress(state);
        }
        RuleExecutionStateService.State claimed = stateService.find(state.projectId(), state.definitionId(), state.keyHash());
        Decision decision = Decision.claimed(claimed == null ? state : claimed, owner, ttlSeconds,
                leaseSeconds, published.getRevisionId(), published.getArtifactDigest());
        current.set(decision);
        return decision;
    }

    public <T> T withDecision(Decision decision, Callable<T> task) {
        if (task == null) throw new IllegalArgumentException("执行任务不能为空");
        Decision previous = current.get();
        current.set(decision);
        try {
            return task.call();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("幂等执行任务失败", e);
        } finally {
            if (previous == null) current.remove(); else current.set(previous);
        }
    }

    public void clear(Decision decision) {
        if (decision == null || current.get() == decision) current.remove();
    }

    public void checkpoint(Decision decision, String checkpointJson, Long revisionId, String artifactDigest) {
        if (decision == null || !decision.claimed) return;
        stateService.checkpoint(decision.state.id(), decision.owner, "RUNNING", checkpointJson,
                revisionId, artifactDigest, null, decision.leaseSeconds);
        stateService.persistCheckpoint(decision.state.rootTraceId(), checkpointJson,
                revisionId, artifactDigest, "RUNNING");
        decision.checkpointJson = checkpointJson;
    }

    /** 在来源批次完成后立即落盘，避免只有整个脚本结束时才有检查点。 */
    public void checkpointCurrent(VariableResolutionInvocationCache cache, Map<String, Object> values) {
        checkpointCurrent(cache, values,
                com.hengshucredit.rule.core.engine.RuntimeContextBridge.currentSourceStates());
    }

    public void checkpointCurrent(VariableResolutionInvocationCache cache, Map<String, Object> values,
                                  Map<String, Map<String, Object>> sourceStates) {
        Decision decision = current.get();
        if (decision == null || !decision.claimed) return;
        checkpoint(decision, snapshotCheckpoint(cache, values, sourceStates),
                decision.currentRevisionId, decision.currentArtifactDigest);
    }

    @SuppressWarnings("unchecked")
    public void restoreCheckpoint(Decision decision, VariableResolutionInvocationCache cache,
                                  Map<String, Object> values) {
        restoreCheckpoint(decision, cache, values, null);
    }

    @SuppressWarnings("unchecked")
    public void restoreCheckpoint(Decision decision, VariableResolutionInvocationCache cache,
                                  Map<String, Object> values, VariableResolveOptions options) {
        if (decision == null || decision.getCheckpointJson() == null || cache == null) return;
        JSONObject checkpoint = JSON.parseObject(decision.getCheckpointJson());
        // 只恢复外数/名单/数据库/模型等不可重复来源的输出；纯 QL 赋值必须随新规则重新计算。
        Map<String, Object> resolved = checkpoint.getObject("resolvedValues", Map.class);
        if ("SOURCE".equalsIgnoreCase(checkpoint.getString("resolvedValuesScope"))
                && resolved != null && values != null) values.putAll(resolved);
        Map<String, Object> responses = checkpoint.getObject("responses", Map.class);
        if (responses != null) responses.forEach((key, value) -> {
            if (value instanceof Map) cache.restoreResponse(key, (Map<String, Object>) value);
        });
        Map<String, Object> variables = checkpoint.getObject("variables", Map.class);
        if (variables != null) variables.forEach((key, value) -> cache.restoreVariableResult(key, null, value));
        Map<String, Object> fields = checkpoint.getObject("fieldResults", Map.class);
        if (fields != null) fields.forEach(cache::restoreFieldResult);
        Map<String, String> fingerprints = checkpoint.getObject("sourceFingerprints", Map.class);
        cache.restoreSourceFingerprints(fingerprints);
        Map<String, Object> sourceResponseKeys = checkpoint.getObject("sourceResponseKeys", Map.class);
        cache.restoreSourceResponseKeys(sourceResponseKeys);
        Map<String, Object> steps = checkpoint.getObject("steps", Map.class);
        cache.restoreCompletedSteps(steps);
        Map<String, Object> randomValues = checkpoint.getObject("randomValues", Map.class);
        com.hengshucredit.rule.core.engine.RuntimeContextBridge.restoreRandomValues(randomValues);
        if (options != null) {
            Map<String, Map<String, Object>> sourceStates = checkpoint.getObject("sourceStates", Map.class);
            options.mergeSourceStates(sourceStates);
            if (steps != null) steps.forEach((key, value) -> {
                VariableResolutionInvocationCache.SourceStep step =
                        VariableResolutionInvocationCache.SourceStep.fromMap(String.valueOf(key), value);
                if (step != null) options.mergeSourceStates(step.getSourceStates());
            });
        }
    }

    public String snapshotCheckpoint(VariableResolutionInvocationCache cache, Map<String, Object> values) {
        return snapshotCheckpoint(cache, values, null);
    }

    public String snapshotCheckpoint(VariableResolutionInvocationCache cache, Map<String, Object> values,
                                     Map<String, Map<String, Object>> sourceStates) {
        Map<String, Object> checkpoint = new LinkedHashMap<>();
        checkpoint.put("version", 2);
        checkpoint.put("resolvedValuesScope", "SOURCE");
        checkpoint.put("resolvedValues", cache == null ? Map.of() : cache.snapshotSourceValues());
        checkpoint.put("responses", cache == null ? Map.of() : cache.snapshotResponses());
        checkpoint.put("variables", cache == null ? Map.of() : cache.snapshotVariableResults());
        checkpoint.put("fieldResults", cache == null ? Map.of() : cache.snapshotFieldResults());
        checkpoint.put("sourceFingerprints", cache == null ? Map.of() : cache.snapshotSourceFingerprints());
        checkpoint.put("sourceResponseKeys", cache == null ? Map.of() : cache.snapshotSourceResponseKeys());
        checkpoint.put("steps", cache == null ? Map.of() : cache.snapshotCompletedSteps());
        checkpoint.put("randomValues", com.hengshucredit.rule.core.engine.RuntimeContextBridge.randomSnapshot());
        checkpoint.put("sourceStates", sourceStates == null ? Map.of() : sourceStates);
        Decision decision = current.get();
        if (decision != null && decision.requestParams != null) {
            checkpoint.put("requestParams", new LinkedHashMap<>(decision.requestParams));
        }
        return CanonicalJson.write(checkpoint);
    }

    public void complete(Decision decision, RuleResult result, String checkpointJson) {
        if (decision == null || !decision.claimed) {
            clear(decision);
            return;
        }
        String status = result != null && result.isSuccess() ? "SUCCEEDED"
                : result != null && "WAITING_EXTERNAL".equals(result.getExecutionStatus())
                ? "WAITING_EXTERNAL"
                : result != null && "UNKNOWN".equals(result.getExecutionStatus())
                ? "UNKNOWN_RETRYABLE" : "FAILED_RETRYABLE";
        String effectiveCheckpoint = checkpointJson == null ? decision.checkpointJson : checkpointJson;
        stateService.finish(decision.state.id(), decision.owner, status, effectiveCheckpoint,
                JSON.toJSONString(result), result == null ? "规则执行结果为空" : result.getErrorMessage());
        stateService.persistCheckpoint(decision.state.rootTraceId(), effectiveCheckpoint,
                decision.currentRevisionId, decision.currentArtifactDigest, status);
        current.remove();
    }

    public void persistStep(Decision decision, VariableResolutionInvocationCache.SourceStep step) {
        if (decision == null || !decision.claimed || step == null) return;
        stateService.persistSourceStep(decision.state.rootTraceId(), step,
                decision.currentRevisionId, decision.currentArtifactDigest);
    }

    public void release(Decision decision, String errorMessage) {
        if (decision == null || !decision.claimed) {
            clear(decision);
            return;
        }
        stateService.finish(decision.state.id(), decision.owner, "FAILED_RETRYABLE",
                decision.checkpointJson, null, errorMessage);
        stateService.persistCheckpoint(decision.state.rootTraceId(), decision.checkpointJson,
                decision.currentRevisionId, decision.currentArtifactDigest, "FAILED_RETRYABLE");
        current.remove();
    }

    private OpenApiContract.IdempotencyConfig config(RulePublished published) {
        if (published == null || published.getOpenApiConfigJson() == null
                || published.getOpenApiConfigJson().isBlank()) return null;
        return OpenApiContractCodec.parse(published.getOpenApiConfigJson()).getIdempotency();
    }

    private IdempotencyKeyCalculator.Result resolveKey(RulePublished published, Long projectId, Map<String, Object> params,
                              OpenApiContract.IdempotencyConfig config) {
        List<RuleDefinitionInputField> fields = definitionService.listInputFields(published.getDefinitionId());
        Map<String, String> paths = new LinkedHashMap<>();
        if (fields != null) for (RuleDefinitionInputField field : fields) {
            if (field == null || field.getVarId() == null || field.getRefType() == null
                    || field.getScriptName() == null || field.getScriptName().isBlank()) continue;
            paths.put(field.getRefType().trim().toUpperCase() + ":" + field.getVarId(), field.getScriptName().trim());
        }
        JSONObject json = JSON.parseObject(published.getOpenApiConfigJson());
        if (json == null) throw new IllegalArgumentException("幂等键配置无效");
        JSONObject idempotency = json.getJSONObject("idempotency");
        if (idempotency == null) idempotency = json;
        Map<String, Object> values = new LinkedHashMap<>();
        if (params != null) values.putAll(params);
        if (variableService != null) {
            Map<String, Object> constants = variableService.buildRefConstantValueMap(projectId);
            Map<String, Object> trusted = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : constants.entrySet()) {
                String path = "__idempotencyConstants." + entry.getKey().replace(':', '_');
                paths.put(entry.getKey(), path);
                trusted.put(entry.getKey().replace(':', '_'), entry.getValue());
            }
            values.put("__idempotencyConstants", trusted);
        }
        return IdempotencyKeyCalculator.calculate(idempotency, values, paths);
    }

    private String newRootTrace(RulePublished published, Long projectId) {
        String scope = "0000";
        if (projectId != null && projectId > 0) {
            var project = projectService.getById(projectId);
            scope = project != null && project.getTraceScopeCode() != null
                    ? project.getTraceScopeCode() : TraceIdGenerator.projectScopeCode(projectId);
        }
        String type = TraceIdGenerator.ruleTypeCode(published.getModelType() == null ? "SCRIPT" : published.getModelType());
        return TraceIdGenerator.generate(type, projectId == null || projectId <= 0 ? "G" : "P", scope);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format("%02x", item & 0xff));
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private int executionLeaseSeconds() {
        int remaining = RequestDeadlineContext.remainingMillis();
        if (remaining == Integer.MAX_VALUE) return 120;
        return Math.max(30, Math.min(3600, (int) Math.ceil(remaining / 1000.0) + 30));
    }

    public enum Status { DISABLED, CLAIMED, COMPLETED, CONFLICT, IN_PROGRESS }

    public static final class Decision {
        private final boolean claimed;
        private final RuleExecutionStateService.State state;
        private final String owner;
        private final RuleResult result;
        private final Status status;
        private final int ttlSeconds;
        private final int leaseSeconds;
        private final Long currentRevisionId;
        private final String currentArtifactDigest;
        private String checkpointJson;
        private Map<String, Object> requestParams;

        private Decision(boolean claimed, RuleExecutionStateService.State state, String owner,
                         RuleResult result, Status status, int ttlSeconds, int leaseSeconds,
                         Long currentRevisionId, String currentArtifactDigest) {
            this.claimed = claimed;
            this.state = state;
            this.owner = owner;
            this.result = result;
            this.status = status;
            this.ttlSeconds = ttlSeconds;
            this.leaseSeconds = leaseSeconds;
            this.currentRevisionId = currentRevisionId;
            this.currentArtifactDigest = currentArtifactDigest;
            this.checkpointJson = state == null ? null : state.checkpointJson();
        }

        static Decision disabled() { return new Decision(false, null, null, null, Status.DISABLED, 0, 0, null, null); }
        static Decision claimed(RuleExecutionStateService.State state, String owner, int ttl,
                                int leaseSeconds,
                                Long revisionId, String artifactDigest) {
            return new Decision(true, state, owner, null, Status.CLAIMED, ttl, leaseSeconds, revisionId, artifactDigest);
        }
        static Decision completed(RuleExecutionStateService.State state, RuleResult result) {
            return new Decision(false, state, null, result, Status.COMPLETED, 0, 0,
                    state == null ? null : state.currentRevisionId(), state == null ? null : state.artifactDigest());
        }
        static Decision conflict() { return new Decision(false, null, null, null, Status.CONFLICT, 0, 0, null, null); }
        static Decision inProgress() { return inProgress(null); }
        static Decision inProgress(RuleExecutionStateService.State state) {
            return new Decision(false, state, null, null, Status.IN_PROGRESS, 0, 0,
                    state == null ? null : state.currentRevisionId(),
                    state == null ? null : state.artifactDigest());
        }
        public Status getStatus() { return status; }
        public RuleResult getResult() { return result; }
        public String getRootTraceId() { return state == null ? null : state.rootTraceId(); }
        public String getExecutionStatus() { return status == Status.IN_PROGRESS ? "IN_PROGRESS" : null; }
        public String getCheckpointJson() { return state == null ? null : state.checkpointJson(); }
        public int getAttemptNo() { return state == null ? 1 : state.attemptNo(); }
        public Long getCurrentRevisionId() { return currentRevisionId; }
        public String getCurrentArtifactDigest() { return currentArtifactDigest; }
        public boolean isClaimed() { return claimed; }
    }
}
