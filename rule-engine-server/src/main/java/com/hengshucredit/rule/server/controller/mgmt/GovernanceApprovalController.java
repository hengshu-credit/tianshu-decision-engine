package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.ApiResponse;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hengshucredit.rule.model.dto.GovernanceApprovalQuery;
import com.hengshucredit.rule.model.dto.GovernanceDraftRequest;
import com.hengshucredit.rule.model.dto.GovernanceRestoreRequest;
import com.hengshucredit.rule.model.dto.GovernanceReviewRequest;
import com.hengshucredit.rule.model.dto.GovernanceSubmitRequest;
import com.hengshucredit.rule.model.entity.GovernanceApprovalRequest;
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
    public ApiResponse<com.hengshucredit.rule.server.governance.GovernanceDeletionResult> deleteRule(@PathVariable Long id) {
        return ApiResponse.ok(approvalService.deleteRule(id, operatorResolver.resolve()));
    }

    @PostMapping("/resources/{type}/{id}/delete")
    @RequirePermission("")
    public ApiResponse<com.hengshucredit.rule.server.governance.GovernanceDeletionResult> deleteResource(
            @PathVariable String type, @PathVariable Long id) {
        return ApiResponse.ok(approvalService.deleteResource(type, id, operatorResolver.resolve()));
    }

    @GetMapping("/requests")
    @RequirePermission("approval:view")
    public ApiResponse<IPage<GovernanceRequestView>> requests(
            @ModelAttribute GovernanceApprovalQuery query) {
        return ApiResponse.ok(approvalService.page(
                query, operatorResolver.resolve()));
    }

    @GetMapping("/requests/summary")
    @RequirePermission("approval:view")
    public ApiResponse<GovernanceApprovalSummary> summary(
            @RequestParam(required = false) Long projectId) {
        return ApiResponse.ok(approvalService.summary(
                projectId, operatorResolver.resolve()));
    }

    @GetMapping("/requests/{id}")
    @RequirePermission("approval:view")
    public ApiResponse<GovernanceRequestDetail> detail(
            @PathVariable Long id) {
        return ApiResponse.ok(approvalService.detail(id));
    }

    @PostMapping("/drafts")
    @RequirePermission("")
    public ApiResponse<GovernanceRequestView> createDraft(
            @RequestBody GovernanceDraftRequest request) {
        return ApiResponse.ok(GovernanceRequestView.from(
                approvalService.createDraft(
                        request, operatorResolver.resolve())));
    }

    @PutMapping("/requests/{id}/draft")
    @RequirePermission("")
    public ApiResponse<GovernanceRequestView> saveDraft(
            @PathVariable Long id,
            @RequestBody GovernanceDraftRequest request) {
        return ApiResponse.ok(GovernanceRequestView.from(
                approvalService.saveDraft(
                        id, request, operatorResolver.resolve())));
    }

    @PostMapping("/requests/{id}/preflight")
    @RequirePermission("")
    public ApiResponse<GovernancePreflightReport> preflight(
            @PathVariable Long id) {
        return ApiResponse.ok(approvalService.preflight(
                id, operatorResolver.resolve()));
    }

    @PostMapping("/requests/{id}/submit")
    @RequirePermission("approval:submit")
    public ApiResponse<GovernanceRequestView> submit(
            @PathVariable Long id,
            @RequestBody(required = false)
            GovernanceSubmitRequest request) {
        return view(approvalService.submit(
                id, request, operatorResolver.resolve()));
    }

    @PostMapping("/requests/{id}/approve")
    @RequirePermission("approval:approve")
    public ApiResponse<GovernanceRequestView> approve(
            @PathVariable Long id,
            @RequestBody(required = false)
            GovernanceReviewRequest request) {
        return view(approvalService.approve(
                id, request, operatorResolver.resolve()));
    }

    @PostMapping("/requests/{id}/reject")
    @RequirePermission("approval:approve")
    public ApiResponse<GovernanceRequestView> reject(
            @PathVariable Long id,
            @RequestBody(required = false)
            GovernanceReviewRequest request) {
        return view(approvalService.reject(
                id, request, operatorResolver.resolve()));
    }

    @PostMapping("/requests/{id}/cancel")
    @RequirePermission("")
    public ApiResponse<GovernanceRequestView> cancel(
            @PathVariable Long id,
            @RequestBody(required = false)
            GovernanceReviewRequest request) {
        return view(approvalService.cancel(
                id, request, operatorResolver.resolve()));
    }

    @GetMapping("/workspaces")
    @RequirePermission("")
    public ApiResponse<IPage<GovernanceRequestView>> workspaces(@ModelAttribute GovernanceApprovalQuery query) {
        query.setTaskScope("MINE");
        String status = query.getStatus();
        query.setStatus(status == null ? "EDITING" : "ALL".equals(status) ? null
                : com.hengshucredit.rule.model.enums.GovernanceRequestStatus.valueOf(status).name());
        return ApiResponse.ok(approvalService.page(query, operatorResolver.resolve()));
    }

    @GetMapping("/workspaces/{type}/{id}")
    @RequirePermission("")
    public ApiResponse<GovernanceRequestView> workspace(@PathVariable String type, @PathVariable Long id) {
        return ApiResponse.ok(approvalService.editableWorkspace(type, id, operatorResolver.resolve()));
    }

    @GetMapping("/workspaces/draft-ids")
    @RequirePermission("")
    public ApiResponse<List<Long>> draftIds(@RequestParam String type, @RequestParam List<Long> ids) {
        return ApiResponse.ok(approvalService.draftResourceIds(type, ids));
    }

    @GetMapping("/workspaces/states")
    @RequirePermission("")
    public ApiResponse<List<com.hengshucredit.rule.server.governance.GovernanceResourceState>> states(
            @RequestParam String type, @RequestParam List<Long> ids) {
        return ApiResponse.ok(approvalService.resourceStates(type, ids, operatorResolver.resolve()));
    }

    @GetMapping("/resources/{type}/{id}/versions")
    @RequirePermission("approval:view")
    public ApiResponse<List<GovernanceVersionView>> versions(
            @PathVariable String type,
            @PathVariable Long id) {
        return ApiResponse.ok(approvalService.versions(type, id)
                .stream()
                .map(GovernanceVersionView::from)
                .toList());
    }

    @PostMapping("/resources/{type}/{id}/restore")
    @RequirePermission("")
    public ApiResponse<GovernanceRequestView> restore(
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

    private ApiResponse<GovernanceRequestView> view(
            GovernanceApprovalRequest request) {
        return ApiResponse.ok(GovernanceRequestView.from(request));
    }
}
