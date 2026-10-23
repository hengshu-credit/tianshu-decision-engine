package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.ApiResponse;

import com.hengshucredit.rule.model.dto.ConsolePasswordResetRequest;
import com.hengshucredit.rule.model.dto.ConsoleUserPermissionRequest;
import com.hengshucredit.rule.model.dto.ConsoleUserSaveRequest;
import com.hengshucredit.rule.server.security.RequirePermission;
import com.hengshucredit.rule.server.service.ConsoleAccountManagementService;
import com.hengshucredit.rule.server.service.ConsoleOperatorResolver;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rule/console/accounts")
public class ConsoleAccountController {

    @Resource
    private ConsoleAccountManagementService accountService;
    @Resource
    private ConsoleOperatorResolver operatorResolver;

    @GetMapping("/project-options")
    @RequirePermission(value = "account:role-assign", anyOf = {"account:manage"})
    public ApiResponse<List<com.hengshucredit.rule.server.mapper.ConsoleUserProjectMapper.ProjectOption>> projectOptions() {
        return ApiResponse.ok(accountService.projectOptions());
    }

    @GetMapping
    @RequirePermission(value = "account:view",
            anyOf = {"account:manage", "account:approve", "account:role-assign",
                    "account:password-reset"})
    public ApiResponse<List<ConsoleAccountManagementService.ConsoleUserDetail>> list() {
        return ApiResponse.ok(accountService.listUsers());
    }

    @GetMapping("/{id}")
    @RequirePermission(value = "account:view",
            anyOf = {"account:manage", "account:approve", "account:role-assign",
                    "account:password-reset"})
    public ApiResponse<ConsoleAccountManagementService.ConsoleUserDetail> detail(
            @PathVariable Long id) {
        return ApiResponse.ok(accountService.getUser(id));
    }

    @PostMapping
    @RequirePermission("account:manage")
    public ApiResponse<ConsoleAccountManagementService.ConsoleUserDetail> save(
            @RequestBody ConsoleUserSaveRequest request) {
        return ApiResponse.ok(accountService.saveUser(
                request, operatorResolver.resolve()));
    }

    @PutMapping("/{id}/permissions")
    @RequirePermission(value = "account:role-assign",
            anyOf = {"account:manage"})
    public ApiResponse<ConsoleAccountManagementService.ConsoleUserDetail> permissions(
            @PathVariable Long id,
            @RequestBody ConsoleUserPermissionRequest request) {
        return ApiResponse.ok(accountService.saveUserPermissions(
                id, request, operatorResolver.resolve()));
    }

    @PutMapping("/{id}/status")
    @RequirePermission("account:manage")
    public ApiResponse<Void> status(@PathVariable Long id,
                               @RequestParam Integer status) {
        accountService.changeUserStatus(id, status, operatorResolver.resolve());
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/password")
    @RequirePermission(value = "account:password-reset",
            anyOf = {"account:manage"})
    public ApiResponse<Void> resetPassword(
            @PathVariable Long id,
            @RequestBody ConsolePasswordResetRequest request) {
        accountService.resetPassword(id,
                request == null ? null : request.getPassword(),
                operatorResolver.resolve());
        return ApiResponse.ok();
    }
}
