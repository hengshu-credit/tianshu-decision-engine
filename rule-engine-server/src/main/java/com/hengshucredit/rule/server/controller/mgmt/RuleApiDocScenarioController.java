package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.dto.ApiDocScenarioCopyRequest;
import com.hengshucredit.rule.model.dto.ApiDocScenarioSaveRequest;
import com.hengshucredit.rule.model.dto.ApiDocScenarioSortRequest;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.entity.RuleApiDocScenario;
import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.service.RuleApiDocScenarioService;
import com.hengshucredit.rule.server.service.RuleExecuteService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
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
        return R.ok(executeService.testExecute(definitionId, params));
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
