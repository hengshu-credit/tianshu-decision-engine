package com.hengshucredit.rule.server.controller.sync;

import com.hengshucredit.rule.model.dto.RuleExperimentExecuteRequest;
import com.hengshucredit.rule.model.dto.RuleExperimentExecuteResult;
import com.hengshucredit.rule.model.dto.RuleExecutionStatus;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.service.RuleExperimentService;
import com.hengshucredit.rule.server.service.RuleExecutionStateService;
import com.hengshucredit.rule.server.service.RuleExperimentExecutionStateService;
import com.hengshucredit.rule.server.service.RuleDefinitionService;
import com.hengshucredit.rule.server.service.RuleRuntimeCallLogService;
import com.hengshucredit.rule.server.service.RuleIdempotencyService;
import com.alibaba.fastjson.JSON;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;
import java.util.LinkedHashMap;

/** 面向业务 SDK/HTTP 的项目运行入口，不暴露控制台会话。 */
@RestController
@RequestMapping("/api/rule/runtime")
public class RuleRuntimeController {
    @Resource
    private RuleExperimentService experimentService;

    @Resource
    private RuleExecutionStateService stateService;

    @Resource
    private RuleExperimentExecutionStateService experimentStateService;

    @Resource
    private RuleDefinitionService definitionService;

    @Resource
    private RuleRuntimeCallLogService runtimeCallLogService;

    @Resource
    private com.hengshucredit.rule.server.mapper.RulePublishedMapper publishedMapper;

    @Resource
    private com.hengshucredit.rule.server.service.RuleExecuteService executeService;

    @Resource
    private RuleIdempotencyService idempotencyService;

    @PostMapping("/experiment/execute/{experimentCode}")
    public R<RuleExperimentExecuteResult> executeExperiment(
            @PathVariable String experimentCode,
            @RequestBody(required = false) RuleExperimentExecuteRequest request,
            HttpServletRequest httpRequest) {
        ProjectAuthContext auth = ProjectAuthContext.from(httpRequest);
        if (auth == null || auth.getProjectId() == null) return R.fail(401, "Unauthorized project token");
        RuleExperimentExecutionStateService.Decision activeDecision = null;
        try {
            var experiment = experimentService.getOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.hengshucredit.rule.model.entity.RuleExperiment>()
                    .eq(com.hengshucredit.rule.model.entity.RuleExperiment::getExperimentCode, experimentCode)
                    .eq(com.hengshucredit.rule.model.entity.RuleExperiment::getStatus, 1));
            if (experiment == null || !auth.getProjectId().equals(experiment.getProjectId())) {
                return R.fail(404, "Experiment not found");
            }
            var decision = experimentStateService.begin(auth.getProjectId(), experiment.getId(), experimentCode,
                    request, experimentService.executionFingerprint(experimentCode, auth.getProjectId()), null);
            activeDecision = decision.isClaimed() ? decision : null;
            if (decision.getStatus() == RuleExperimentExecutionStateService.Status.COMPLETED) {
                return R.ok(decision.getResult());
            }
            if (decision.getStatus() == RuleExperimentExecutionStateService.Status.CONFLICT) {
                return R.fail(409, "幂等键已被其他请求使用且请求内容不同");
            }
            if (decision.getStatus() == RuleExperimentExecutionStateService.Status.IN_PROGRESS) {
                return R.fail(409, "相同幂等键的请求正在执行");
            }
            RuleExperimentExecuteResult result = experimentService.executeForProject(
                    experimentCode, auth.getProjectId(), request, decision.getTraceId());
            experimentStateService.complete(decision, result);
            activeDecision = null;
            return R.ok(result);
        } catch (IllegalArgumentException e) {
            if (activeDecision != null) experimentStateService.release(activeDecision, e.getMessage());
            return R.fail(e.getMessage());
        } catch (RuntimeException e) {
            if (activeDecision != null) experimentStateService.release(activeDecision, e.getMessage());
            return R.fail(500, "实验执行异常");
        }
    }

    @GetMapping("/executions/{traceId}")
    public R<RuleExecutionStatus> getExecutionStatus(@PathVariable String traceId,
                                                       HttpServletRequest httpRequest) {
        ProjectAuthContext auth = ProjectAuthContext.from(httpRequest);
        if (auth == null || auth.getProjectId() == null) return R.fail(401, "Unauthorized project token");
        RuleExecutionStateService.State state = stateService.findByRootTraceId(traceId);
        boolean projectAllowed = state != null && state.projectId() != null && state.projectId() > 0
                ? state.projectId().equals(auth.getProjectId())
                : state != null && definitionService.isDefinitionAvailableInProject(
                        state.definitionId(), auth.getProjectId());
        if (state == null || !projectAllowed) {
            return R.fail(404, "Execution status not found");
        }
        RuleExecutionStatus status = new RuleExecutionStatus();
        status.setTraceId(state.rootTraceId());
        status.setStatus(state.status());
        status.setAttemptNo(state.attemptNo());
        status.setRevisionId(state.currentRevisionId());
        status.setArtifactDigest(state.artifactDigest());
        status.setErrorMessage(state.errorMessage());
        status.setExpireTime(state.expireTime());
        if (state.resultJson() != null && isTerminalSuccess(state)) {
            status.setResult(JSON.parseObject(state.resultJson(), com.hengshucredit.rule.model.dto.RuleResult.class));
        } else {
            status.setIntermediate(intermediateState(state));
        }
        return R.ok(status);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> intermediateState(RuleExecutionStateService.State state) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", state.status());
        result.put("attemptNo", state.attemptNo());
        result.put("traceId", state.rootTraceId());
        result.put("errorMessage", state.errorMessage());
        if (state.checkpointJson() == null || state.checkpointJson().isBlank()) return result;
        try {
            Map<String, Object> checkpoint = JSON.parseObject(state.checkpointJson(), Map.class);
            Object sourceStates = checkpoint.get("sourceStates");
            if (sourceStates instanceof Map<?, ?>) result.put("sourceStates", sourceStates);
            Object steps = checkpoint.get("steps");
            if (steps instanceof Map<?, ?> rawSteps) {
                Map<String, Object> safeSteps = new LinkedHashMap<>();
                rawSteps.forEach((key, value) -> {
                    if (!(value instanceof Map<?, ?> raw)) return;
                    Map<String, Object> safe = new LinkedHashMap<>();
                    for (String field : new String[]{"sourceKey", "sourceType", "status"}) {
                        if (raw.containsKey(field)) safe.put(field, raw.get(field));
                    }
                    if (raw.get("metadata") instanceof Map<?, ?> metadata) {
                        Map<String, Object> safeMetadata = new LinkedHashMap<>();
                        for (String field : new String[]{"scriptName", "variableId", "apiConfigId",
                                "kind", "stepId", "completedStepIds", "taskId", "traceId", "resultMode", "callId"}) {
                            if (metadata.containsKey(field)) safeMetadata.put(field, metadata.get(field));
                        }
                        if (!safeMetadata.isEmpty()) safe.put("metadata", safeMetadata);
                    }
                    safeSteps.put(String.valueOf(key), safe);
                });
                result.put("steps", safeSteps);
            }
        } catch (RuntimeException ignored) {
            result.put("checkpointAvailable", true);
        }
        return result;
    }

    /** 单独读取已完成订单结果，客户端超时后不必重复提交执行请求。 */
    @GetMapping("/executions/{traceId}/result")
    public R<?> getExecutionResult(@PathVariable String traceId,
                                   HttpServletRequest httpRequest) {
        ProjectAuthContext auth = ProjectAuthContext.from(httpRequest);
        if (auth == null || auth.getProjectId() == null) return R.fail(401, "Unauthorized project token");
        RuleExecutionStateService.State state = stateService.findByRootTraceId(traceId);
        if (!sameProject(state, auth.getProjectId())) return R.fail(404, "Execution result not found");
        if (state.resultJson() == null || state.resultJson().isBlank() || !isTerminalSuccess(state)) {
            return R.fail(202, "Execution result is not ready");
        }
        return R.ok(JSON.parseObject(state.resultJson()));
    }

    /** 使用原订单入参按 trace 恢复可重试执行；服务端校验摘要以防止跨订单恢复。 */
    @PostMapping("/executions/{traceId}/resume")
    public R<?> resumeExecution(@PathVariable String traceId,
                                @RequestBody(required = false) Map<String, Object> params,
                                HttpServletRequest httpRequest) {
        ProjectAuthContext auth = ProjectAuthContext.from(httpRequest);
        if (auth == null || auth.getProjectId() == null) return R.fail(401, "Unauthorized project token");
        RuleExecutionStateService.State state = stateService.findByRootTraceId(traceId);
        if (!sameProject(state, auth.getProjectId())) return R.fail(404, "Execution state not found");
        if (state.resultJson() != null && !state.resultJson().isBlank() && isTerminalSuccess(state)) {
            return R.ok(JSON.parseObject(state.resultJson()));
        }
        var publishedQuery = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RulePublished>()
                .eq(RulePublished::getDefinitionId, state.definitionId()).eq(RulePublished::getStatus, 1);
        if (state.currentRevisionId() != null) {
            publishedQuery.eq(RulePublished::getRevisionId, state.currentRevisionId());
        } else {
            publishedQuery.last("LIMIT 1");
        }
        RulePublished published = publishedMapper.selectOne(publishedQuery);
        if (published == null) return R.fail(404, "Published rule not found");
        Map<String, Object> resumeParams = params == null ? idempotencyService.requestParams(state) : params;
        if (resumeParams == null) return R.fail(409, "订单检查点未保存原始入参，请提供原订单入参恢复");
        RuleIdempotencyService.Decision decision;
        try {
            decision = idempotencyService.resumeByTrace(state, published,
                    resumeParams);
        } catch (IllegalArgumentException error) {
            return R.fail(409, error.getMessage());
        }
        if (!decision.isClaimed()) return R.fail(409, "相同订单正在其他节点恢复执行");
        try {
            RuleResult result = idempotencyService.withDecision(decision,
                    () -> executeService.executePublished(published,
                            resumeParams,
                            auth.getProjectId(), "RUNTIME_RESUME", auth, true, true));
            idempotencyService.complete(decision, result, null);
            return R.ok(result);
        } catch (RuntimeException error) {
            idempotencyService.release(decision, error.getMessage());
            return R.fail(500, "恢复执行失败: " + error.getMessage());
        }
    }

    private boolean sameProject(RuleExecutionStateService.State state, Long projectId) {
        return state != null && state.projectId() != null && state.projectId().equals(projectId);
    }

    private boolean isTerminalSuccess(RuleExecutionStateService.State state) {
        return state != null && "SUCCEEDED".equals(state.status());
    }

    /**
     * 按规则执行返回中的 callId 取一次 API 外数调用的原始请求/响应。
     * 只允许同一项目的业务令牌读取，避免把控制台日志查询接口暴露给业务侧。
     */
    @GetMapping("/external-calls/{callId}")
    public R<Map<String, Object>> getExternalCallPayload(@PathVariable String callId,
                                                           HttpServletRequest httpRequest) {
        ProjectAuthContext auth = ProjectAuthContext.from(httpRequest);
        if (auth == null || auth.getProjectId() == null) return R.fail(401, "Unauthorized project token");
        Map<String, Object> payload = runtimeCallLogService.payloadByCallId(callId, auth.getProjectId());
        if (payload == null || payload.isEmpty()) return R.fail(404, "External call not found");
        return R.ok(payload);
    }
}
