package com.hengshucredit.rule.server.controller.mgmt;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hengshucredit.rule.model.dto.RuleExperimentExecuteRequest;
import com.hengshucredit.rule.model.dto.RuleExperimentExecuteResult;
import com.hengshucredit.rule.model.entity.RuleExperiment;
import com.hengshucredit.rule.model.entity.RuleExperimentExecutionLog;
import com.hengshucredit.rule.model.entity.RuleExperimentVersion;
import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.service.RuleExperimentService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rule/experiment")
public class RuleExperimentController {

    @Resource
    private RuleExperimentService experimentService;

    @GetMapping("/list")
    public R<IPage<RuleExperiment>> list(@RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                                         @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
                                         @RequestParam(required = false) Long projectId,
                                         @RequestParam(required = false) Integer status,
                                         @RequestParam(required = false) String keyword) {
        return R.ok(experimentService.pageExperiments(pageNum, pageSize, projectId, status, keyword));
    }

    @GetMapping("/{id}")
    public R<RuleExperiment> get(@PathVariable Long id) {
        RuleExperiment experiment = experimentService.getDetail(id);
        return experiment == null ? R.fail("分流实验不存在") : R.ok(experiment);
    }

    @GetMapping("/logs")
    public R<IPage<RuleExperimentExecutionLog>> logs(@RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                                                     @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
                                                     @RequestParam(required = false) Long experimentId,
                                                     @RequestParam(required = false) String experimentCode,
                                                     @RequestParam(required = false) String requestKey,
                                                     @RequestParam(required = false) String stage,
                                                     @RequestParam(required = false) String groupCode,
                                                     @RequestParam(required = false) Integer success) {
        return R.ok(experimentService.pageExecutionLogs(pageNum, pageSize, experimentId, experimentCode,
                requestKey, stage, groupCode, success));
    }

    @PostMapping
    public R<RuleExperiment> save(@RequestBody RuleExperiment experiment) {
        try {
            return R.ok(experimentService.saveExperiment(experiment));
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        experimentService.deleteExperiment(id);
        return R.ok();
    }

    @GetMapping("/versions/{experimentId}")
    public R<List<RuleExperimentVersion>> listVersions(@PathVariable Long experimentId) {
        return R.ok(experimentService.listVersions(experimentId));
    }

    @GetMapping("/version/{experimentId}/{version}")
    public R<RuleExperimentVersion> getVersion(@PathVariable Long experimentId, @PathVariable Integer version) {
        RuleExperimentVersion snapshot = experimentService.getVersion(experimentId, version);
        return snapshot == null ? R.fail("Version not found") : R.ok(snapshot);
    }

    @GetMapping("/versionCompare/{experimentId}")
    public R<Map<String, Object>> compareVersions(@PathVariable Long experimentId,
                                                  @RequestParam Integer leftVersion,
                                                  @RequestParam Integer rightVersion) {
        try {
            return R.ok(experimentService.compareVersions(experimentId, leftVersion, rightVersion));
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    @PostMapping("/rollback/{experimentId}/{version}")
    public R<Void> rollback(@PathVariable Long experimentId, @PathVariable Integer version) {
        try {
            experimentService.rollbackToVersion(experimentId, version);
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    @PostMapping("/execute/{experimentCode}")
    public R<RuleExperimentExecuteResult> execute(@PathVariable String experimentCode,
                                                  @RequestBody(required = false) RuleExperimentExecuteRequest request) {
        try {
            return R.ok(experimentService.execute(experimentCode, request));
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }
}
