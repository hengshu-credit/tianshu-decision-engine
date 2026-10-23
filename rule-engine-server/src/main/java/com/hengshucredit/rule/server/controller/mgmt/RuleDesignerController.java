package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.ApiResponse;

import com.hengshucredit.rule.model.dto.RuleDesignerCompileRequest;
import com.hengshucredit.rule.model.dto.RuleDesignerCompileTaskResponse;
import com.hengshucredit.rule.model.dto.RuleDesignerDraftRequest;
import com.hengshucredit.rule.model.dto.RuleDraftSaveResponse;
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
    @Resource private com.hengshucredit.rule.server.service.RuleCompileCompletionNotifier completionNotifier;

    @PostMapping("/{id}/designer/compile")
    public ApiResponse<RuleDesignerCompileTaskResponse> compile(@PathVariable Long id,
                                                      @RequestBody RuleDesignerCompileRequest request) {
        return ApiResponse.ok(designerService.submitCompile(id, request));
    }

    @GetMapping("/{id}/designer/compile/{taskId}")
    public ApiResponse<RuleDesignerCompileTaskResponse> compileStatus(@PathVariable Long id,
                                                             @PathVariable String taskId) {
        return ApiResponse.ok(designerService.getCompileTask(id, taskId));
    }

    @GetMapping("/{id}/designer/compile/{taskId}/completion")
    public org.springframework.web.context.request.async.DeferredResult<ApiResponse<Boolean>> completion(
            @PathVariable Long id, @PathVariable String taskId) {
        designerService.getCompileTask(id, taskId); // Resource authorization precedes subscription.
        var result = completionNotifier.await(taskId);
        try {
            var status = designerService.getCompileTask(id, taskId).getStatus();
            if ("SUCCEEDED".equals(status) || "FAILED".equals(status)) completionNotifier.completed(taskId);
        } catch (RuntimeException error) {
            result.setErrorResult(error);
        }
        return result;
    }

    @PostMapping("/{id}/designer/drafts")
    public ApiResponse<RuleDraftSaveResponse> save(@PathVariable Long id,
                                        @RequestBody RuleDesignerDraftRequest request) {
        return ApiResponse.ok(designerService.save(id, request));
    }

    @DeleteMapping("/{id}/revisions/{revisionId}")
    public ApiResponse<Void> delete(@PathVariable Long id, @PathVariable Long revisionId,
                           @RequestParam(required = false) Integer lockVersion) {
        lifecycleService.deleteDraft(id, revisionId, lockVersion);
        return ApiResponse.ok();
    }
}
