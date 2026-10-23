package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.ApiResponse;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hengshucredit.rule.model.dto.RuleExperimentExecuteRequest;
import com.hengshucredit.rule.model.dto.RuleExperimentExecuteResult;
import com.hengshucredit.rule.model.entity.RuleExperiment;
import com.hengshucredit.rule.model.entity.RuleExperimentExecutionLog;
import com.hengshucredit.rule.model.entity.RuleExperimentVersion;
import com.hengshucredit.rule.server.governance.GovernanceRequestView;
import com.hengshucredit.rule.server.governance.GovernedProjectionMutation;
import com.hengshucredit.rule.server.governance.LegacyGovernanceRestoreService;
import com.hengshucredit.rule.server.security.RequirePermission;
import com.hengshucredit.rule.server.service.ConsoleOperatorResolver;
import com.hengshucredit.rule.server.service.RuleExperimentService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rule/experiment")
public class RuleExperimentController {

    @Resource
    private RuleExperimentService experimentService;

    @Resource
    private LegacyGovernanceRestoreService legacyRestoreService;

    @Resource
    private ConsoleOperatorResolver operatorResolver;

    @GetMapping("/list")
    public ApiResponse<IPage<RuleExperiment>> list(@RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                                         @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
                                         @RequestParam(required = false) Long projectId,
                                         @RequestParam(required = false) String projectCode,
                                         @RequestParam(required = false) String projectName,
                                         @RequestParam(required = false) Integer status,
                                         @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(experimentService.pageExperiments(pageNum, pageSize, projectId, projectCode, projectName,
                status, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<RuleExperiment> get(@PathVariable Long id) {
        RuleExperiment experiment = experimentService.getDetail(id);
        return experiment == null ? ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.RESOURCE_NOT_FOUND, "分流实验不存在") : ApiResponse.ok(experiment);
    }

    @GetMapping("/logs")
    public ApiResponse<IPage<RuleExperimentExecutionLog>> logs(@RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                                                     @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
                                                     @RequestParam(required = false) Long experimentId,
                                                     @RequestParam(required = false) String experimentCode,
                                                     @RequestParam(required = false) String requestKey,
                                                     @RequestParam(required = false) String traceId,
                                                     @RequestParam(required = false) String stage,
                                                     @RequestParam(required = false) String groupCode,
                                                     @RequestParam(required = false) Integer success) {
        return ApiResponse.ok(experimentService.pageExecutionLogs(pageNum, pageSize, experimentId, experimentCode,
                requestKey, traceId, stage, groupCode, success));
    }

    @PostMapping
    @GovernedProjectionMutation
    public ApiResponse<RuleExperiment> save(@RequestBody RuleExperiment experiment) {
        try {
            return ApiResponse.ok(experimentService.saveExperiment(experiment));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e);
        }
    }

    @DeleteMapping("/{id}")
    @GovernedProjectionMutation
    public ApiResponse<Void> delete(@PathVariable Long id) {
        experimentService.deleteExperiment(id);
        return ApiResponse.ok();
    }

    @GetMapping("/versions/{experimentId}")
    public ApiResponse<List<RuleExperimentVersion>> listVersions(@PathVariable Long experimentId) {
        return ApiResponse.ok(experimentService.listVersions(experimentId));
    }

    @GetMapping("/version/{experimentId}/{version}")
    public ApiResponse<RuleExperimentVersion> getVersion(@PathVariable Long experimentId, @PathVariable Integer version) {
        RuleExperimentVersion snapshot = experimentService.getVersion(experimentId, version);
        return snapshot == null ? ApiResponse.fail(com.hengshucredit.rule.model.PlatformCode.RESOURCE_NOT_FOUND, "Version not found") : ApiResponse.ok(snapshot);
    }

    @GetMapping("/versionCompare/{experimentId}")
    public ApiResponse<Map<String, Object>> compareVersions(@PathVariable Long experimentId,
                                                  @RequestParam Integer leftVersion,
                                                  @RequestParam Integer rightVersion) {
        try {
            return ApiResponse.ok(experimentService.compareVersions(experimentId, leftVersion, rightVersion));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e);
        }
    }

    @PostMapping("/rollback/{experimentId}/{version}")
    @RequirePermission("experiment:edit")
    public ApiResponse<GovernanceRequestView> rollback(
            @PathVariable Long experimentId,
            @PathVariable Integer version,
            @RequestParam(defaultValue = "NEW") String restoreMode) {
        try {
            return ApiResponse.ok(GovernanceRequestView.from(
                    legacyRestoreService.restoreExperiment(
                            experimentId, version, restoreMode,
                            operatorResolver.resolve())));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e);
        }
    }

    @PostMapping("/execute/{experimentCode}")
    public ApiResponse<RuleExperimentExecuteResult> execute(@PathVariable String experimentCode,
                                                  @RequestBody(required = false) RuleExperimentExecuteRequest request) {
        try {
            return ApiResponse.ok(experimentService.execute(experimentCode, request));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e);
        }
    }
}
