package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.dto.RuleDesignerCompileRequest;
import com.hengshucredit.rule.model.dto.RuleDesignerCompileResponse;
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
    public R<RuleDesignerCompileResponse> compile(@PathVariable Long id,
                                                 @RequestBody RuleDesignerCompileRequest request) {
        return R.ok(designerService.compile(id, request));
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
