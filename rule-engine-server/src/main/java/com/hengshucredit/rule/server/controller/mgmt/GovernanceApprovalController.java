package com.hengshucredit.rule.server.controller.mgmt;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hengshucredit.rule.model.dto.GovernanceApprovalQuery;
import com.hengshucredit.rule.model.dto.GovernanceDraftRequest;
import com.hengshucredit.rule.model.dto.GovernanceRestoreRequest;
import com.hengshucredit.rule.model.dto.GovernanceReviewRequest;
import com.hengshucredit.rule.model.dto.GovernanceSubmitRequest;
import com.hengshucredit.rule.model.entity.GovernanceApprovalRequest;
import com.hengshucredit.rule.server.common.Result;
import com.hengshucredit.rule.server.governance.GovernanceApprovalService;
import com.hengshucredit.rule.server.governance.GovernanceApprovalSummary;
import com.hengshucredit.rule.server.governance.GovernancePreflightReport;
import com.hengshucredit.rule.server.governance.GovernanceRequestDetail;
import com.hengshucredit.rule.server.governance.GovernanceRequestView;
import com.hengshucredit.rule.server.governance.GovernanceVersionView;
import com.hengshucredit.rule.server.security.RequirePermission;
import com.hengshucredit.rule.server.service.ConsoleOperatorResolver;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rule/governance")
public class GovernanceApprovalController {

    @Resource
    private GovernanceApprovalService approvalService;
    @Resource
    private ConsoleOperatorResolver operatorResolver;

    @PostMapping("/rules/{id}/delete")
    @RequirePermission("rule:edit")
    public Result<com.hengshucredit.rule.server.governance.GovernanceDeletionResult> deleteRule(@PathVariable Long id) {
        return Result.ok(approvalService.deleteRule(id, operatorResolver.resolve()));
    }

    @PostMapping("/resources/{type}/{id}/delete")
    @RequirePermission("")
    public Result<com.hengshucredit.rule.server.governance.GovernanceDeletionResult> deleteResource(
            @PathVariable String type, @PathVariable Long id) {
        return Result.ok(approvalService.deleteResource(type, id, operatorResolver.resolve()));
    }

    @GetMapping("/requests")
    @RequirePermission("approval:view")
    public Result<IPage<GovernanceRequestView>> requests(
            @ModelAttribute GovernanceApprovalQuery query) {
        return Result.ok(approvalService.page(
                query, operatorResolver.resolve()));
    }

    @GetMapping("/requests/summary")
    @RequirePermission("approval:view")
    public Result<GovernanceApprovalSummary> summary(
            @RequestParam(required = false) Long projectId) {
        return Result.ok(approvalService.summary(
                projectId, operatorResolver.resolve()));
    }

    @GetMapping("/requests/{id}")
    @RequirePermission("approval:view")
    public Result<GovernanceRequestDetail> detail(
            @PathVariable Long id) {
        return Result.ok(approvalService.detail(id));
    }

    @PostMapping("/drafts")
    @RequirePermission("")
    public Result<GovernanceRequestView> createDraft(
            @RequestBody GovernanceDraftRequest request) {
        return Result.ok(GovernanceRequestView.from(
                approvalService.createDraft(
                        request, operatorResolver.resolve())));
    }

    @PutMapping("/requests/{id}/draft")
    @RequirePermission("")
    public Result<GovernanceRequestView> saveDraft(
            @PathVariable Long id,
            @RequestBody GovernanceDraftRequest request) {
        return Result.ok(GovernanceRequestView.from(
                approvalService.saveDraft(
                        id, request, operatorResolver.resolve())));
    }

    @PostMapping("/requests/{id}/preflight")
    @RequirePermission("")
    public Result<GovernancePreflightReport> preflight(
            @PathVariable Long id) {
        return Result.ok(approvalService.preflight(
                id, operatorResolver.resolve()));
    }

    @PostMapping("/requests/{id}/submit")
    @RequirePermission("approval:submit")
    public Result<GovernanceRequestView> submit(
            @PathVariable Long id,
            @RequestBody(required = false)
            GovernanceSubmitRequest request) {
        return view(approvalService.submit(
                id, request, operatorResolver.resolve()));
    }

    @PostMapping("/requests/{id}/approve")
    @RequirePermission("approval:approve")
    public Result<GovernanceRequestView> approve(
            @PathVariable Long id,
            @RequestBody(required = false)
            GovernanceReviewRequest request) {
        return view(approvalService.approve(
                id, request, operatorResolver.resolve()));
    }

    @PostMapping("/requests/{id}/reject")
    @RequirePermission("approval:approve")
    public Result<GovernanceRequestView> reject(
            @PathVariable Long id,
            @RequestBody(required = false)
            GovernanceReviewRequest request) {
        return view(approvalService.reject(
                id, request, operatorResolver.resolve()));
    }

    @PostMapping("/requests/{id}/cancel")
    @RequirePermission("")
    public Result<GovernanceRequestView> cancel(
            @PathVariable Long id,
            @RequestBody(required = false)
            GovernanceReviewRequest request) {
        return view(approvalService.cancel(
                id, request, operatorResolver.resolve()));
    }

    @GetMapping("/workspaces")
    @RequirePermission("")
    public Result<IPage<GovernanceRequestView>> workspaces(@ModelAttribute GovernanceApprovalQuery query) {
        query.setTaskScope("MINE");
        String status = query.getStatus();
        query.setStatus(status == null ? "EDITING" : "ALL".equals(status) ? null
                : com.hengshucredit.rule.model.enums.GovernanceRequestStatus.valueOf(status).name());
        return Result.ok(approvalService.page(query, operatorResolver.resolve()));
    }

    @GetMapping("/workspaces/{type}/{id}")
    @RequirePermission("")
    public Result<GovernanceRequestView> workspace(@PathVariable String type, @PathVariable Long id) {
        return Result.ok(approvalService.editableWorkspace(type, id, operatorResolver.resolve()));
    }

    @GetMapping("/workspaces/draft-ids")
    @RequirePermission("")
    public Result<List<Long>> draftIds(@RequestParam String type, @RequestParam List<Long> ids) {
        return Result.ok(approvalService.draftResourceIds(type, ids));
    }

    @GetMapping("/workspaces/states")
    @RequirePermission("")
    public Result<List<com.hengshucredit.rule.server.governance.GovernanceResourceState>> states(
            @RequestParam String type, @RequestParam List<Long> ids) {
        return Result.ok(approvalService.resourceStates(type, ids, operatorResolver.resolve()));
    }

    @GetMapping("/resources/{type}/{id}/versions")
    @RequirePermission("approval:view")
    public Result<List<GovernanceVersionView>> versions(
            @PathVariable String type,
            @PathVariable Long id) {
        return Result.ok(approvalService.versions(type, id)
                .stream()
                .map(GovernanceVersionView::from)
                .toList());
    }

    @PostMapping("/resources/{type}/{id}/restore")
    @RequirePermission("")
    public Result<GovernanceRequestView> restore(
            @PathVariable String type,
            @PathVariable Long id,
            @RequestBody GovernanceRestoreRequest request) {
        if (request == null || request.getSourceVersionId() == null) {
            throw new IllegalArgumentException("历史版本 ID 不能为空");
        }
        return view(approvalService.createRestoreDraft(
                type, id, request.getSourceVersionId(), request.getRestoreMode(),
                operatorResolver.resolve()));
    }

    private Result<GovernanceRequestView> view(
            GovernanceApprovalRequest request) {
        return Result.ok(GovernanceRequestView.from(request));
    }
}
