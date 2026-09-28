package com.hengshucredit.rule.server.controller.sync;

import com.hengshucredit.rule.model.dto.RuleExperimentExecuteRequest;
import com.hengshucredit.rule.model.dto.RuleExperimentExecuteResult;
import com.hengshucredit.rule.model.dto.RuleExecutionStatus;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.service.RuleExperimentService;
import com.hengshucredit.rule.server.service.RuleExecutionStateService;
import com.hengshucredit.rule.server.service.RuleExperimentExecutionStateService;
import com.hengshucredit.rule.server.service.RuleDefinitionService;
import com.hengshucredit.rule.server.service.RuleRuntimeCallLogService;
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
        if (state.resultJson() != null) status.setResult(JSON.parseObject(state.resultJson(), com.hengshucredit.rule.model.dto.RuleResult.class));
        return R.ok(status);
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
