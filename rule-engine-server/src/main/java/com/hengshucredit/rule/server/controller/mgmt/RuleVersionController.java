package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.dto.RuleDesignerPublishRequest;
import com.hengshucredit.rule.model.entity.RuleRevision;
import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.security.RequirePermission;
import com.hengshucredit.rule.server.service.RuleGovernanceSubmissionService;
import com.hengshucredit.rule.server.service.RuleVersionBindingService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/rule/definition")
public class RuleVersionController {
    @Resource private RuleVersionBindingService versionService;
    @Resource private RuleGovernanceSubmissionService submissionService;
    @GetMapping("/{id}/published-versions")
    @RequirePermission("rule:view")
    public R<List<Map<String, Object>>> versions(@PathVariable Long id) {
        return R.ok(versionService.publishedVersions(id));
    }
    @PostMapping("/{id}/designer/publish")
    @RequirePermission("rule:submit")
    public R<Map<String, Object>> publish(@PathVariable Long id, @RequestBody RuleDesignerPublishRequest request) {
        RuleRevision revision = submissionService.submitDesigner(id, request);
        return R.ok(Map.of("revision", revision, "approvalRequestId", revision.getGovernanceRequestId()));
    }
}
