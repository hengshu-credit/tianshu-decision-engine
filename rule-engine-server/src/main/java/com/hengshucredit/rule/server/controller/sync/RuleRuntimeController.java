package com.hengshucredit.rule.server.controller.sync;

import com.hengshucredit.rule.model.ApiResponse;

import com.hengshucredit.rule.model.dto.RuleExperimentExecuteRequest;
import com.hengshucredit.rule.model.dto.RuleExperimentExecuteResult;
import com.hengshucredit.rule.model.dto.RuleExecutionStatus;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.model.PlatformCode;
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
    public ApiResponse<RuleExperimentExecuteResult> executeExperiment(
            @PathVariable String experimentCode,
            @RequestBody(required = false) RuleExperimentExecuteRequest request,
            HttpServletRequest httpRequest) {
        ProjectAuthContext auth = ProjectAuthContext.from(httpRequest);
        if (auth == null || (!auth.isGlobalAccess() && auth.getProjectId() == null)) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.TOKEN_INVALID, "Unauthorized project token");
        }
        RuleExperimentExecutionStateService.Decision activeDecision = null;
        try {
            var experiment = experimentService.getOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.hengshucredit.rule.model.entity.RuleExperiment>()
                    .eq(com.hengshucredit.rule.model.entity.RuleExperiment::getExperimentCode, experimentCode)
                    .eq(com.hengshucredit.rule.model.entity.RuleExperiment::getStatus, 1));
            Long executionProjectId = auth.isGlobalAccess() ? experiment == null ? null : experiment.getProjectId()
                    : auth.getProjectId();
            if (experiment == null || !executionProjectId.equals(experiment.getProjectId())) {
                return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.RESOURCE_NOT_FOUND, "Experiment not found");
            }
            var decision = experimentStateService.begin(executionProjectId, experiment.getId(), experimentCode,
                    request, experimentService.executionFingerprint(experimentCode, executionProjectId), null);
            activeDecision = decision.isClaimed() ? decision : null;
            if (decision.getStatus() == RuleExperimentExecutionStateService.Status.COMPLETED) {
                return ApiResponse.ok(decision.getResult());
            }
            if (decision.getStatus() == RuleExperimentExecutionStateService.Status.CONFLICT) {
                return ApiResponse.fail(PlatformCode.IDEMPOTENCY_CONFLICT, null);
            }
            if (decision.getStatus() == RuleExperimentExecutionStateService.Status.IN_PROGRESS) {
                return ApiResponse.fail(PlatformCode.EXECUTION_IN_PROGRESS, null);
            }
            RuleExperimentExecuteResult result = experimentService.executeForProject(
                    experimentCode, executionProjectId, request, decision.getTraceId());
            experimentStateService.complete(decision, result);
            activeDecision = null;
            return ApiResponse.ok(result);
        } catch (IllegalArgumentException e) {
            if (activeDecision != null) experimentStateService.release(activeDecision, e.getMessage());
            return ApiResponse.fail(e);
        } catch (RuntimeException e) {
            if (activeDecision != null) experimentStateService.release(activeDecision, e.getMessage());
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.SYSTEM_ERROR, "实验执行异常");
        }
    }

    @GetMapping("/executions/{traceId}")
    public ApiResponse<RuleExecutionStatus> getExecutionStatus(@PathVariable String traceId,
                                                       HttpServletRequest httpRequest) {
        ProjectAuthContext auth = ProjectAuthContext.from(httpRequest);
        if (auth == null || (!auth.isGlobalAccess() && auth.getProjectId() == null)) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.TOKEN_INVALID, "Unauthorized project token");
        }
        RuleExecutionStateService.State state = stateService.findByRootTraceId(traceId);
        boolean projectAllowed;
        if (auth.isGlobalAccess()) {
            projectAllowed = state != null;
        } else if (state != null && state.projectId() != null && state.projectId() > 0) {
            projectAllowed = state.projectId().equals(auth.getProjectId());
        } else {
            projectAllowed = state != null && definitionService.isDefinitionAvailableInProject(
                    state.definitionId(), auth.getProjectId());
        }
        if (state == null || !projectAllowed) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.RESOURCE_NOT_FOUND, "Execution status not found");
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
        return ApiResponse.ok(status);
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
    public ApiResponse<?> getExecutionResult(@PathVariable String traceId,
                                   HttpServletRequest httpRequest) {
        ProjectAuthContext auth = ProjectAuthContext.from(httpRequest);
        if (auth == null || (!auth.isGlobalAccess() && auth.getProjectId() == null)) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.TOKEN_INVALID, "Unauthorized project token");
        }
        RuleExecutionStateService.State state = stateService.findByRootTraceId(traceId);
        if (state == null || (!auth.isGlobalAccess() && !sameProject(state, auth.getProjectId()))) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.RESOURCE_NOT_FOUND, "Execution result not found");
        }
        if (state.resultJson() == null || state.resultJson().isBlank() || !isTerminalSuccess(state)) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.RESULT_PENDING, "Execution result is not ready");
        }
        return ApiResponse.ok(JSON.parseObject(state.resultJson()));
    }

    /** 使用原订单入参按 trace 恢复可重试执行；服务端校验摘要以防止跨订单恢复。 */
    @PostMapping("/executions/{traceId}/resume")
    public ApiResponse<?> resumeExecution(@PathVariable String traceId,
                                @RequestBody(required = false) Map<String, Object> params,
                                HttpServletRequest httpRequest) {
        ProjectAuthContext auth = ProjectAuthContext.from(httpRequest);
        if (auth == null || (!auth.isGlobalAccess() && auth.getProjectId() == null)) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.TOKEN_INVALID, "Unauthorized project token");
        }
        RuleExecutionStateService.State state = stateService.findByRootTraceId(traceId);
        if (state == null || (!auth.isGlobalAccess() && !sameProject(state, auth.getProjectId()))) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.RESOURCE_NOT_FOUND, "Execution state not found");
        }
        if (state.resultJson() != null && !state.resultJson().isBlank() && isTerminalSuccess(state)) {
            return ApiResponse.ok(JSON.parseObject(state.resultJson()));
        }
        var publishedQuery = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RulePublished>()
                .eq(RulePublished::getDefinitionId, state.definitionId()).eq(RulePublished::getStatus, 1);
        if (state.currentRevisionId() != null) {
            publishedQuery.eq(RulePublished::getRevisionId, state.currentRevisionId());
        } else {
            publishedQuery.last("LIMIT 1");
        }
        RulePublished published = publishedMapper.selectOne(publishedQuery);
        if (published == null) return ApiResponse.fail(PlatformCode.RULE_NOT_FOUND, "Published rule not found");
        Map<String, Object> resumeParams = params == null ? idempotencyService.requestParams(state) : params;
        if (resumeParams == null) return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.RESOURCE_CONFLICT, "订单检查点未保存原始入参，请提供原订单入参恢复");
        RuleIdempotencyService.Decision decision;
        try {
            decision = idempotencyService.resumeByTrace(state, published,
                    resumeParams);
        } catch (IllegalArgumentException error) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.RESOURCE_CONFLICT, error.getMessage());
        }
        if (!decision.isClaimed()) return ApiResponse.fail(PlatformCode.EXECUTION_IN_PROGRESS, "相同订单正在其他节点恢复执行");
        try {
            RuleResult result = idempotencyService.withDecision(decision,
                    () -> executeService.executePublished(published,
                            resumeParams,
                            state.projectId(), "RUNTIME_RESUME", auth, true, true));
            idempotencyService.complete(decision, result, null);
            return ApiResponse.ok(result);
        } catch (RuntimeException error) {
            idempotencyService.release(decision, error.getMessage());
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.SYSTEM_ERROR, "恢复执行失败: " + error.getMessage());
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
    public ApiResponse<Map<String, Object>> getExternalCallPayload(@PathVariable String callId,
                                                           HttpServletRequest httpRequest) {
        ProjectAuthContext auth = ProjectAuthContext.from(httpRequest);
        if (auth == null || (!auth.isGlobalAccess() && auth.getProjectId() == null)) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.TOKEN_INVALID, "Unauthorized project token");
        }
        Map<String, Object> payload = runtimeCallLogService.payloadByCallId(callId,
                auth.isGlobalAccess() ? null : auth.getProjectId());
        if (payload == null || payload.isEmpty()) return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.RESOURCE_NOT_FOUND, "External call not found");
        return ApiResponse.ok(payload);
    }
}
