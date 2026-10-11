package com.hengshucredit.rule.server.controller.mgmt;

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
import com.hengshucredit.rule.server.common.R;
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

    @PostMapping("/execute-validated")
    @RequirePermission("rule:edit")
    public R<ApiScenarioExecutionResponse> executeValidated(@PathVariable Long definitionId,
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
            return new ApiScenarioExecutionResponse(execution, responseSnapshot, validation);
        });
    }

    @GetMapping
    public R<List<RuleApiDocScenario>> list(@PathVariable Long definitionId) {
        return execute(() -> scenarioService.listByDefinition(definitionId));
    }

    @PostMapping
    public R<RuleApiDocScenario> create(@PathVariable Long definitionId,
                                        @RequestBody ApiDocScenarioSaveRequest request) {
        return execute(() -> scenarioService.create(definitionId, request));
    }

    @PutMapping("/{scenarioId:\\d+}")
    public R<RuleApiDocScenario> update(@PathVariable Long definitionId,
                                        @PathVariable Long scenarioId,
                                        @RequestBody ApiDocScenarioSaveRequest request) {
        return execute(() -> scenarioService.update(definitionId, scenarioId, request));
    }

    @PostMapping("/{scenarioId:\\d+}/copy")
    public R<RuleApiDocScenario> copy(@PathVariable Long definitionId,
                                      @PathVariable Long scenarioId,
                                      @RequestBody(required = false) ApiDocScenarioCopyRequest request) {
        return execute(() -> scenarioService.copy(definitionId, scenarioId,
                request == null ? null : request.getScenarioName()));
    }

    @PutMapping("/sort")
    public R<Void> sort(@PathVariable Long definitionId,
                        @RequestBody(required = false) ApiDocScenarioSortRequest request) {
        return executeVoid(() -> scenarioService.sort(definitionId,
                request == null ? null : request.getScenarioIds()));
    }

    @DeleteMapping("/{scenarioId:\\d+}")
    public R<Void> delete(@PathVariable Long definitionId, @PathVariable Long scenarioId) {
        return executeVoid(() -> scenarioService.delete(definitionId, scenarioId));
    }

    @PostMapping("/execute")
    @SuppressWarnings("unchecked")
    public R<RuleResult> execute(@PathVariable Long definitionId,
                                 @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> params = body != null && body.get("params") instanceof Map
                ? (Map<String, Object>) body.get("params") : Collections.emptyMap();
        Long projectId = body == null || body.get("projectId") == null
                ? null : Long.valueOf(body.get("projectId").toString());
        return R.ok(executeService.testExecute(
                definitionId, params, projectId));
    }

    private <T> R<T> execute(Supplier<T> action) {
        try {
            return R.ok(action.get());
        } catch (IllegalArgumentException e) {
            return R.fail(400, e.getMessage());
        }
    }

    private R<Void> executeVoid(Runnable action) {
        try {
            action.run();
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.fail(400, e.getMessage());
        }
    }
}
