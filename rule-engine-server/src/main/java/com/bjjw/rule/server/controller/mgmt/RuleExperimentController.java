package com.bjjw.rule.server.controller.mgmt;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjjw.rule.model.dto.RuleExperimentExecuteRequest;
import com.bjjw.rule.model.dto.RuleExperimentExecuteResult;
import com.bjjw.rule.model.entity.RuleExperiment;
import com.bjjw.rule.server.common.R;
import com.bjjw.rule.server.service.RuleExperimentService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

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
