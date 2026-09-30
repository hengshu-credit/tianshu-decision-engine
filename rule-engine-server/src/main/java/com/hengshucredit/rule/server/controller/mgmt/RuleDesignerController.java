package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.dto.RuleDesignerCompileRequest;
import com.hengshucredit.rule.model.dto.RuleDesignerCompileTaskResponse;
import com.hengshucredit.rule.model.dto.RuleDesignerDraftRequest;
import com.hengshucredit.rule.model.dto.RuleDraftSaveResponse;
import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.security.RequirePermission;
import com.hengshucredit.rule.server.service.RuleDesignerService;
import com.hengshucredit.rule.server.service.RuleLifecycleService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rule/definition")
@RequirePermission("rule:edit")
public class RuleDesignerController {
    @Resource private RuleDesignerService designerService;
    @Resource private RuleLifecycleService lifecycleService;

    @PostMapping("/{id}/designer/compile")
    public R<RuleDesignerCompileTaskResponse> compile(@PathVariable Long id,
                                                      @RequestBody RuleDesignerCompileRequest request) {
        return R.ok(designerService.submitCompile(id, request));
    }

    @GetMapping("/{id}/designer/compile/{taskId}")
    public R<RuleDesignerCompileTaskResponse> compileStatus(@PathVariable Long id,
                                                             @PathVariable String taskId) {
        return R.ok(designerService.getCompileTask(id, taskId));
    }

    @PostMapping("/{id}/designer/drafts")
    public R<RuleDraftSaveResponse> save(@PathVariable Long id,
                                        @RequestBody RuleDesignerDraftRequest request) {
        return R.ok(designerService.save(id, request));
    }

    @DeleteMapping("/{id}/revisions/{revisionId}")
    public R<Void> delete(@PathVariable Long id, @PathVariable Long revisionId,
                           @RequestParam(required = false) Integer lockVersion) {
        lifecycleService.deleteDraft(id, revisionId, lockVersion);
        return R.ok();
    }
}
