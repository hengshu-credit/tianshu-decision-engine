package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.dto.GovernanceDraftRequest;
import com.hengshucredit.rule.server.common.Result;
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
    public Result<?> workspace(@PathVariable String type, @PathVariable Long id) {
        return Result.ok(service.workspace(type, id, operators.resolve()));
    }
    @GetMapping("/events") @RequirePermission("")
    public Result<?> events(@PathVariable String type, @PathVariable Long id) { return Result.ok(service.history(type, id)); }

    @PostMapping("/drafts") @RequirePermission("")
    public Result<?> create(@PathVariable String type, @PathVariable Long id, @RequestBody GovernanceDraftRequest request) {
        return Result.ok(service.create(type, id, request, operators.resolve()));
    }
    @DeleteMapping("/{versionId}") @RequirePermission("")
    public Result<?> delete(@PathVariable String type, @PathVariable Long id, @PathVariable Long versionId,
                            @RequestParam Long expectedSnapshotId) {
        service.deleteVersion(type, id, versionId, expectedSnapshotId, operators.resolve());
        return Result.ok();
    }
    @GetMapping("/{versionId}/deletion-impact") @RequirePermission("")
    public Result<?> deletionImpact(@PathVariable String type, @PathVariable Long id, @PathVariable Long versionId) {
        return Result.ok(service.deletionImpact(type, id, versionId));
    }
}
