package com.hengshucredit.rule.server.controller.sync;

import com.hengshucredit.rule.server.service.ExecutionDispatch;
import com.hengshucredit.rule.server.service.ExecutionQueue;
import com.hengshucredit.rule.server.auth.ProjectExecutionGuard;
import com.hengshucredit.rule.server.openapi.OpenApiContractService;

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
    @Resource private ExecutionQueue executionQueue;

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
    @Resource private OpenApiContractService openContracts;

    @PostMapping("/experiment/execute/{experimentCode}")
    public ApiResponse<RuleExperimentExecuteResult> executeExperiment(
            @PathVariable String experimentCode,
            @RequestBody(required = false) RuleExperimentExecuteRequest request,
            HttpServletRequest httpRequest) {
        return executeExperiment(experimentCode, request == null ? new RuleExperimentExecuteRequest() : request, httpRequest, null, ProjectAuthContext.from(httpRequest));
    }

    private ApiResponse<RuleExperimentExecuteResult> executeExperiment(String experimentCode, RuleExperimentExecuteRequest request,
                                                                      HttpServletRequest httpRequest, String resumeTrace, ProjectAuthContext auth) {
        if (auth == null || (!auth.isGlobalAccess() && auth.getProjectId() == null)) {
            return ApiResponse.fail(PlatformCode.TOKEN_INVALID, "Unauthorized project token");
        }
        RuleExperimentExecutionStateService.Decision activeDecision = null;
        boolean admitted = false;
        ProjectExecutionGuard.Permit permit = null;
        String acceptedTrace = null;
        try {
            var experiment = experimentService.getOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.hengshucredit.rule.model.entity.RuleExperiment>()
                    .eq(com.hengshucredit.rule.model.entity.RuleExperiment::getExperimentCode, experimentCode)
                    .eq(com.hengshucredit.rule.model.entity.RuleExperiment::getStatus, 1));
            if (!RuleExperimentService.availableToCaller(experiment, auth)) {
                return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.RESOURCE_NOT_FOUND, "Experiment not found");
            }
            Long executionProjectId = experiment.getProjectId() == null ? Long.valueOf(0) : experiment.getProjectId();
            String recoveryMode = com.hengshucredit.rule.server.openapi.OpenApiContract.IdempotencyConfig.requireRecoveryMode(
                    httpRequest.getParameter("externalRecoveryMode"));
            var dispatch = ExecutionDispatch.of(auth, request.getClientAppName(), true, recoveryMode);
            String digest = "FIXED".equals(request.getVersionMode()) ? experimentService.fixedFingerprint(experiment,request.getVersionId())
                    : experimentService.executionFingerprint(experiment);
            boolean submit = executionQueue != null && resumeTrace == null;
            var decision = submit
                    ? experimentStateService.enqueue(executionProjectId, experiment.getId(), experimentCode, request, digest,
                        dispatch)
                    : experimentStateService.begin(executionProjectId, experiment.getId(), experimentCode, request, digest, resumeTrace, dispatch);
            acceptedTrace = decision.getTraceId();
            activeDecision = decision.isClaimed() ? decision : null;
            if (decision.getStatus() == RuleExperimentExecutionStateService.Status.COMPLETED) {
                return ApiResponse.ok(decision.getResult());
            }
            if (decision.getStatus() == RuleExperimentExecutionStateService.Status.CONFLICT) {
                return ApiResponse.fail(PlatformCode.IDEMPOTENCY_CONFLICT, null);
            }
            if (decision.getStatus() == RuleExperimentExecutionStateService.Status.IN_PROGRESS) {
                var response = ApiResponse.<RuleExperimentExecuteResult>fail(PlatformCode.EXECUTION_IN_PROGRESS,null);
                response.setTraceId(acceptedTrace);
                return response;
            }
            if (submit) {
                admitted = executionQueue.enter(auth);
                if (!admitted) return executionQueue.defer(true, acceptedTrace);
                permit = executionQueue.permit(auth);
            }
            if (decision.getStatus() == RuleExperimentExecutionStateService.Status.QUEUED) {
                var claim = experimentStateService.claimAccepted(decision.getStateId(),decision.getAttemptNo(),acceptedTrace);
                if (claim == null) {
                    var existing = experimentStateService.byTrace(acceptedTrace);
                    if (existing != null && "SUCCEEDED".equals(existing.status()) && existing.resultJson() != null)
                        return ApiResponse.ok(RuleExperimentExecutionStateService.readResult(existing.resultJson()));
                    return ExecutionQueue.pending(acceptedTrace);
                }
                decision = claim;
                activeDecision = claim;
            }
            decision.setExternalRecoveryMode(submit ? decision.acceptedRecoveryMode() : recoveryMode);
            final var running = decision;
            RuleExperimentExecuteResult result = experimentStateService.withLease(running, () -> experimentService.executeForCaller(
                    experimentCode, auth, request, running.getTraceId()));
            experimentStateService.complete(decision, result);
            activeDecision = null;
            return ApiResponse.ok(result);
        } catch (RuleExperimentExecutionStateService.ConfigurationConflict mismatch) {
            if (activeDecision != null) experimentStateService.release(activeDecision, mismatch.getMessage());
            var response = ApiResponse.<RuleExperimentExecuteResult>fail(PlatformCode.RESOURCE_CONFLICT, mismatch.getMessage());
            response.setTraceId(acceptedTrace);
            return response;
        } catch (RuleExperimentExecutionStateService.StrategyInProgress running) {
            if (activeDecision != null) experimentStateService.awaitStrategy(activeDecision);
            return ExecutionQueue.pending(acceptedTrace);
        } catch (ProjectExecutionGuard.Rejected busy) {
            return acceptedTrace == null ? ApiResponse.fail(PlatformCode.CAPACITY_EXCEEDED,null)
                    : executionQueue.defer(true, acceptedTrace);
        } catch (IllegalArgumentException e) {
            if (activeDecision != null) experimentStateService.release(activeDecision, e.getMessage());
            var response = ApiResponse.<RuleExperimentExecuteResult>fail(e);
            response.setTraceId(acceptedTrace);
            return response;
        } catch (RuntimeException e) {
            if (activeDecision != null) experimentStateService.release(activeDecision, e.getMessage());
            var failed = ApiResponse.<RuleExperimentExecuteResult>fail(com.hengshucredit.rule.model.PlatformCode.SYSTEM_ERROR, "实验执行异常");
            failed.setTraceId(acceptedTrace);
            return failed;
        } finally {
            if (permit != null) permit.close();
            if (admitted) executionQueue.leave(auth);
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
        if (state == null) {
            var experiment = experimentByTrace(traceId, auth);
            if (experiment != null) {
                RuleExecutionStatus status = new RuleExecutionStatus();
                status.setTraceId(traceId);
                status.setStatus(experiment.status());
                status.setAttemptNo(experiment.attemptNo());
                status.setExpireTime(experiment.expireTime());
                status.setErrorMessage(experiment.error());
                if (experiment.resultJson() != null) {
                    var last = RuleExperimentExecutionStateService.readResult(experiment.resultJson());
                    var group = last.getProductionGroup();
                    if (group != null && group.getTraceId() != null) {
                        var child = stateService.findByRootTraceId(group.getTraceId());
                        if (child != null && java.util.Objects.equals(child.projectId(), experiment.projectId())
                                && ExecutionDispatch.sameCaller(auth, child.dispatchJson())) {
                            if ("SUCCEEDED".equals(experiment.status())
                                    && child.resultJson() != null)
                                status.setResult(
                                        stateService.completedResult(child));
                            else status.setIntermediate(intermediateState(child));
                        }
                    }
                }
                return ApiResponse.ok(status);
            }
        }
        if (!canReadState(auth, state, true)) {
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
            status.setResult(
                    stateService.completedResult(state));
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
        if (state == null) {
            var experiment = experimentByTrace(traceId, auth);
            if (experiment != null) return "SUCCEEDED".equals(experiment.status()) && experiment.resultJson() != null
                    ? ApiResponse.ok(RuleExperimentExecutionStateService.readResult(experiment.resultJson()))
                    : ApiResponse.fail(PlatformCode.RESULT_PENDING, "实验结果尚未就绪");
        }
        if (!canReadState(auth, state, false)) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.RESOURCE_NOT_FOUND, "Execution result not found");
        }
        if (state.resultJson() == null || state.resultJson().isBlank() || !isTerminalSuccess(state)) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.RESULT_PENDING, "Execution result is not ready");
        }
        return ApiResponse.ok(stateService.publicResult(state));
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
        if (state == null) {
            var experiment = experimentByTrace(traceId, auth);
            if (experiment != null) {
                if (experiment.request() == null) return ApiResponse.fail(PlatformCode.RESOURCE_CONFLICT, "历史实验未保存原请求，请按原订单号重试");
                if (params != null) experiment.request().setParams(params);
                var original = executionQueue == null ? auth : executionQueue.resumeIdentity(auth, experiment.projectId(), experiment.dispatchJson());
                if (original == null) return ApiResponse.fail(PlatformCode.PERMISSION_DENIED, null);
                return executeExperiment(experiment.experimentCode(), experiment.request(), httpRequest, traceId, original);
            }
        }
        if (!canReadState(auth, state, false)) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.RESOURCE_NOT_FOUND, "Execution state not found");
        }
        var executionAuth = executionQueue == null ? auth : executionQueue.resumeIdentity(auth, state.projectId(), state.dispatchJson());
        if (executionAuth == null) return ApiResponse.fail(PlatformCode.PERMISSION_DENIED, null);
        var originalDispatch = state.dispatch();
        if (originalDispatch != null && originalDispatch.openContract()) {
            try { openContracts.resolve(executionAuth.getAuthId(), executionAuth.getAuthCode(), state.ruleCode()); }
            catch (com.hengshucredit.rule.server.openapi.OpenApiException denied) { return ApiResponse.fail(PlatformCode.PERMISSION_DENIED, null); }
        }
        if (state.resultJson() != null && !state.resultJson().isBlank() && isTerminalSuccess(state)) {
            if (params != null && !idempotencyService.sameRequest(state, params)) return ApiResponse.fail(PlatformCode.IDEMPOTENCY_CONFLICT, null);
            return ApiResponse.ok(stateService.publicResult(state));
        }
        var groupOptions = state.groupOptions();
        if (state.parentExperimentTrace() != null && groupOptions == null) {
            var response = ApiResponse.fail(PlatformCode.RESOURCE_CONFLICT, "历史组订单缺少执行选项，请使用原实验追踪号恢复");
            response.setTraceId(state.parentExperimentTrace());
            return response;
        }
        var publishedQuery = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RulePublished>()
                .eq(RulePublished::getDefinitionId, state.definitionId()).eq(RulePublished::getStatus, 1);
        if (executionAuth.isInstitutionAccess()) publishedQuery.inSql(RulePublished::getDefinitionId,
                com.hengshucredit.rule.server.mapper.RulePublishedMapper.GLOBAL_DEFINITION);
        if (state.currentRevisionId() != null) {
            publishedQuery.eq(RulePublished::getRevisionId, state.currentRevisionId());
        } else {
            publishedQuery.last("LIMIT 1");
        }
        RulePublished published = publishedMapper.selectOne(publishedQuery);
        if (published == null) return ApiResponse.fail(PlatformCode.RULE_NOT_FOUND, "Published rule not found");
        if (state.artifactDigest() != null && !state.artifactDigest().equals(published.getArtifactDigest()))
            return ApiResponse.fail(PlatformCode.RESOURCE_CONFLICT, "原订单的策略制品已变化，请核对策略修订后恢复");
        Map<String, Object> resumeParams = params == null ? idempotencyService.requestParams(state) : params;
        if (resumeParams == null) return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.RESOURCE_CONFLICT, "订单检查点未保存原始入参，请提供原订单入参恢复");
        RuleIdempotencyService.Decision decision;
        try {
            String recoveryMode = com.hengshucredit.rule.server.openapi.OpenApiContract.IdempotencyConfig.requireRecoveryMode(
                    httpRequest.getParameter("externalRecoveryMode"));
            decision = idempotencyService.resumeByTrace(state, published, resumeParams);
            decision.setExternalRecoveryMode(recoveryMode);
        } catch (IllegalArgumentException error) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.RESOURCE_CONFLICT, error.getMessage());
        }
        if (!decision.isClaimed()) return ApiResponse.fail(PlatformCode.EXECUTION_IN_PROGRESS, "相同订单正在其他节点恢复执行");
        try {
            RuleResult result = idempotencyService.withDecision(decision, () -> {
                if (groupOptions != null) {
                    var saved = JSON.parseObject(state.dispatchJson());
                    return executeService.executePublishedWithOptions(published, resumeParams, state.projectId(), saved.getString("clientAppName"),
                            groupOptions, groupOptions.isDryRun() ? "EXPERIMENT_TEST" : "EXPERIMENT_LIVE", executionAuth).getResult();
                }
                return executeService.executePublished(published,
                            resumeParams,
                            state.projectId(), originalDispatch == null ? "RUNTIME_RESUME" : originalDispatch.clientAppName(), executionAuth,
                            originalDispatch == null || originalDispatch.traceEnabled(),
                            originalDispatch == null || (originalDispatch.recordTrace() == null ? originalDispatch.traceEnabled() : originalDispatch.recordTrace()));
            });
            idempotencyService.complete(decision, result, null);
            return ApiResponse.ok(result);
        } catch (RuntimeException error) {
            idempotencyService.release(decision, error.getMessage());
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.SYSTEM_ERROR, "恢复执行失败: " + error.getMessage());
        }
    }

    private RuleExperimentExecutionStateService.TraceRequest experimentByTrace(String trace, ProjectAuthContext auth) {
        if (experimentStateService == null) return null;
        var found = experimentStateService.byTrace(trace);
        return found != null && ExecutionDispatch.sameCaller(auth, found.dispatchJson())
                && (auth.isGlobalAccess() || java.util.Objects.equals(found.projectId(), auth.getProjectId()))
                ? found : null;
    }

    private boolean canReadState(ProjectAuthContext auth, RuleExecutionStateService.State state, boolean allowLegacyStatus) {
        if (state == null || !ExecutionDispatch.sameCaller(auth, state.dispatchJson())) return false;
        if (auth.isGlobalAccess() || java.util.Objects.equals(state.projectId(), auth.getProjectId())) return true;
        return allowLegacyStatus && !auth.isInstitutionAccess() && (state.projectId() == null || state.projectId() == 0)
                && definitionService.isDefinitionAvailableInProject(state.definitionId(), auth.getProjectId());
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
        if (auth.isInstitutionAccess()) return ApiResponse.fail(PlatformCode.PERMISSION_DENIED, null);
        Map<String, Object> payload = runtimeCallLogService.payloadByCallId(callId,
                auth.isGlobalAccess() ? null : auth.getProjectId());
        if (payload == null || payload.isEmpty()) return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.RESOURCE_NOT_FOUND, "External call not found");
        return ApiResponse.ok(payload);
    }
}
