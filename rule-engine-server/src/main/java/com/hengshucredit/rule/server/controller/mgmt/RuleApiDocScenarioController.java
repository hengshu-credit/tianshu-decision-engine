package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.ApiResponse;

import com.hengshucredit.rule.model.dto.ApiDocScenarioCopyRequest;
import com.hengshucredit.rule.model.dto.ApiDocScenarioSaveRequest;
import com.hengshucredit.rule.model.dto.ApiDocScenarioSortRequest;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.dto.ApiScenarioExecutionRequest;
import com.hengshucredit.rule.model.dto.ApiScenarioExecutionResponse;
import com.hengshucredit.rule.model.dto.ResolvedField;
import com.hengshucredit.rule.model.dto.RuleTestSchemaRequest;
import com.hengshucredit.rule.model.dto.TestExpectationResult;
import com.hengshucredit.rule.model.entity.RuleApiDocScenario;
import com.hengshucredit.rule.server.service.RuleApiDocScenarioService;
import com.hengshucredit.rule.server.service.RuleExecuteService;
import com.hengshucredit.rule.server.service.RuleTestExpectationService;
import com.hengshucredit.rule.server.service.RuleTestSchemaService;
import com.hengshucredit.rule.server.security.RequirePermission;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/rule/definition/{definitionId:\\d+}/api-scenarios")
public class RuleApiDocScenarioController {

    @Resource
    private RuleApiDocScenarioService scenarioService;

    @Resource
    private RuleExecuteService executeService;
    @Resource
    private RuleTestExpectationService expectationService;
    @Resource
    private RuleTestSchemaService testSchemaService;
    @Resource
    private com.hengshucredit.rule.server.service.RuleRegressionEvidenceService regressionEvidence;

    @GetMapping("/reports")
    @RequirePermission("rule:view")
    public ApiResponse<com.baomidou.mybatisplus.core.metadata.IPage<com.hengshucredit.rule.model.entity.RuleRegressionEvidence>> reports(
            @PathVariable Long definitionId,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") int pageNum,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int pageSize) {
        return execute(() -> { scenarioService.requireDefinitionAccess(definitionId); return regressionEvidence.history(definitionId, pageNum, pageSize); });
    }

    @GetMapping("/reports/{reportId:\\d+}")
    @RequirePermission("rule:view")
    public ApiResponse<com.hengshucredit.rule.model.entity.RuleRegressionEvidence> reportDetail(
            @PathVariable Long definitionId, @PathVariable Long reportId) {
        return execute(() -> { scenarioService.requireDefinitionAccess(definitionId); return regressionEvidence.detail(definitionId, reportId); });
    }

    public record RegressionCheckRequest(Long revisionId) { }

    @PostMapping("/reports/check")
    @RequirePermission("rule:edit")
    public ApiResponse<com.hengshucredit.rule.model.dto.RuleRegressionGate> checkReports(
            @PathVariable Long definitionId, @RequestBody RegressionCheckRequest request) {
        return execute(() -> {
            scenarioService.requireDefinitionAccess(definitionId);
            if (request == null || request.revisionId() == null) throw new IllegalArgumentException("请选择已保存的草稿或版本");
            return regressionEvidence.checkRevision(definitionId, request.revisionId());
        });
    }

    @PostMapping("/execute-validated")
    @RequirePermission("rule:edit")
    public ApiResponse<ApiScenarioExecutionResponse> executeValidated(@PathVariable Long definitionId,
            @RequestBody ApiScenarioExecutionRequest request) {
        return execute(() -> {
            if (request == null) throw new IllegalArgumentException("测试请求不能为空");
            RuleApiDocScenario stored = request.getScenarioId() == null ? null
                    : scenarioService.getForExecution(definitionId, request.getScenarioId());
            String input = request.getRequestJson() != null ? request.getRequestJson()
                    : stored == null ? null : stored.getRequestJson();
            String config = request.getExpectationJson() != null ? request.getExpectationJson()
                    : stored == null ? null : stored.getExpectationJson();
            String expected = request.getExpectedResponseJson() != null ? request.getExpectedResponseJson()
                    : stored == null ? null : stored.getResponseJson();
            Map<String, Object> params = expectationService.requestParams(input);
            Long projectId = request.getProjectId() != null ? request.getProjectId() : expectationService.requestProjectId(input);
            com.hengshucredit.rule.server.service.RuleTestTargetResolver.Target target = request.getExecutionSource() == null
                    ? null : executeService.resolveTestTarget(definitionId, request.getExecutionSource(), null, null, projectId);
            var run = regressionEvidence.begin(definitionId, stored, target, input, config, expected, projectId);
            try {
                RuleResult execution = target == null ? executeService.testExecute(definitionId, params, projectId)
                        : executeService.testExecuteTarget(target, params);
                String responseSnapshot = expectationService.writeExecutionSnapshot(execution);
                List<ResolvedField> outputs = List.of();
                String schemaError = null;
                if (expectationService.needsOutputSchema(config) && execution.isSuccess()) {
                    try {
                        if (target != null) outputs = testSchemaService.outputFields(target);
                        else {
                            RuleTestSchemaRequest schemaRequest = new RuleTestSchemaRequest();
                            schemaRequest.setTargetType("RULE");
                            schemaRequest.setTargetId(definitionId);
                            schemaRequest.setProjectId(projectId);
                            outputs = testSchemaService.build(schemaRequest).getOutputs();
                        }
                    } catch (RuntimeException unavailable) {
                        schemaError = unavailable.getMessage();
                    }
                }
                TestExpectationResult validation = expectationService.evaluate(config, expected, responseSnapshot, outputs);
                if (schemaError != null) {
                    validation.setStatus("INVALID");
                    validation.setMessage("输出字段结构读取失败，无法校验预期：" + schemaError);
                }
                String reportId = regressionEvidence.complete(run, execution, validation);
                return new ApiScenarioExecutionResponse(execution, responseSnapshot, validation, reportId);
            } catch (RuntimeException failure) {
                regressionEvidence.fail(run, failure);
                throw failure;
            }
        });
    }

    @GetMapping
    public ApiResponse<List<RuleApiDocScenario>> list(@PathVariable Long definitionId) {
        return execute(() -> scenarioService.listByDefinition(definitionId));
    }

    @PostMapping
    public ApiResponse<RuleApiDocScenario> create(@PathVariable Long definitionId,
                                        @RequestBody ApiDocScenarioSaveRequest request) {
        return execute(() -> scenarioService.create(definitionId, request));
    }

    @PutMapping("/{scenarioId:\\d+}")
    public ApiResponse<RuleApiDocScenario> update(@PathVariable Long definitionId,
                                        @PathVariable Long scenarioId,
                                        @RequestBody ApiDocScenarioSaveRequest request) {
        return execute(() -> scenarioService.update(definitionId, scenarioId, request));
    }

    @PostMapping("/{scenarioId:\\d+}/copy")
    public ApiResponse<RuleApiDocScenario> copy(@PathVariable Long definitionId,
                                      @PathVariable Long scenarioId,
                                      @RequestBody(required = false) ApiDocScenarioCopyRequest request) {
        return execute(() -> scenarioService.copy(definitionId, scenarioId,
                request == null ? null : request.getScenarioName()));
    }

    @PutMapping("/sort")
    public ApiResponse<Void> sort(@PathVariable Long definitionId,
                        @RequestBody(required = false) ApiDocScenarioSortRequest request) {
        return executeVoid(() -> scenarioService.sort(definitionId,
                request == null ? null : request.getScenarioIds()));
    }

    @DeleteMapping("/{scenarioId:\\d+}")
    public ApiResponse<Void> delete(@PathVariable Long definitionId, @PathVariable Long scenarioId) {
        return executeVoid(() -> scenarioService.delete(definitionId, scenarioId));
    }

    @PostMapping("/execute")
    @SuppressWarnings("unchecked")
    public ApiResponse<RuleResult> execute(@PathVariable Long definitionId,
                                 @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> params = body != null && body.get("params") instanceof Map
                ? (Map<String, Object>) body.get("params") : Collections.emptyMap();
        Long projectId = body == null || body.get("projectId") == null
                ? null : Long.valueOf(body.get("projectId").toString());
        return ApiResponse.ok(executeService.testExecute(
                definitionId, params, projectId));
    }

    private <T> ApiResponse<T> execute(Supplier<T> action) {
        try {
            return ApiResponse.ok(action.get());
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.PARAMETER_INVALID, e.getMessage());
        }
    }

    private ApiResponse<Void> executeVoid(Runnable action) {
        try {
            action.run();
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.PARAMETER_INVALID, e.getMessage());
        }
    }
}
