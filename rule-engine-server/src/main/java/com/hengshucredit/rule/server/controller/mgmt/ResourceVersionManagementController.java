package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.ApiResponse;

import com.hengshucredit.rule.model.dto.GovernanceDraftRequest;
import com.hengshucredit.rule.server.governance.*;
import com.hengshucredit.rule.server.security.RequirePermission;
import com.hengshucredit.rule.server.service.ConsoleOperatorResolver;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rule/resource-versions/{type}/{id}")
public class ResourceVersionManagementController {
    @Resource private ResourceVersionManagementService service;
    @Resource private ConsoleOperatorResolver operators;

    @GetMapping @RequirePermission("")
    public ApiResponse<?> workspace(@PathVariable String type, @PathVariable Long id) {
        return ApiResponse.ok(service.workspace(type, id, operators.resolve()));
    }
    @GetMapping("/events") @RequirePermission("")
    public ApiResponse<?> events(@PathVariable String type, @PathVariable Long id) { return ApiResponse.ok(service.history(type, id)); }

    @PostMapping("/drafts") @RequirePermission("")
    public ApiResponse<?> create(@PathVariable String type, @PathVariable Long id, @RequestBody GovernanceDraftRequest request) {
        return ApiResponse.ok(service.create(type, id, request, operators.resolve()));
    }
    @DeleteMapping("/{versionId}") @RequirePermission("")
    public ApiResponse<?> delete(@PathVariable String type, @PathVariable Long id, @PathVariable Long versionId,
                            @RequestParam Long expectedSnapshotId) {
        service.deleteVersion(type, id, versionId, expectedSnapshotId, operators.resolve());
        return ApiResponse.ok();
    }
    @GetMapping("/{versionId}/deletion-impact") @RequirePermission("")
    public ApiResponse<?> deletionImpact(@PathVariable String type, @PathVariable Long id, @PathVariable Long versionId) {
        return ApiResponse.ok(service.deletionImpact(type, id, versionId));
    }
}
