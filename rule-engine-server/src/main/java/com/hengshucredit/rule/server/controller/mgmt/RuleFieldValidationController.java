package com.hengshucredit.rule.server.controller.mgmt;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hengshucredit.rule.model.entity.RuleFieldValidation;
import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.service.RuleFieldValidationService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/rule/field-validation")
public class RuleFieldValidationController {

    @Resource
    private RuleFieldValidationService service;

    @GetMapping("/list")
    public R<IPage<RuleFieldValidation>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String validationType,
            @RequestParam(required = false) String keyword) {
        return R.ok(service.pageList(pageNum, pageSize, projectId, scope, validationType, keyword));
    }

    @GetMapping("/available")
    public R<List<RuleFieldValidation>> available(@RequestParam(required = false) Long projectId) {
        return R.ok(service.listAvailable(projectId));
    }

    @PostMapping
    public R<RuleFieldValidation> create(@RequestBody RuleFieldValidation rule) {
        return R.ok(service.createRule(rule));
    }

    @PutMapping
    public R<Void> update(@RequestBody RuleFieldValidation rule) {
        service.updateRule(rule);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.deleteRule(id);
        return R.ok();
    }
}
