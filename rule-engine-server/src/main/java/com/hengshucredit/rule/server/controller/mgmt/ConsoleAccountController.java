package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.dto.ConsolePasswordResetRequest;
import com.hengshucredit.rule.model.dto.ConsoleUserPermissionRequest;
import com.hengshucredit.rule.model.dto.ConsoleUserSaveRequest;
import com.hengshucredit.rule.server.common.Result;
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

    @GetMapping
    @RequirePermission(value = "account:view",
            anyOf = {"account:manage", "account:approve", "account:role-assign",
                    "account:password-reset"})
    public Result<List<ConsoleAccountManagementService.ConsoleUserDetail>> list() {
        return Result.ok(accountService.listUsers());
    }

    @GetMapping("/{id}")
    @RequirePermission(value = "account:view",
            anyOf = {"account:manage", "account:approve", "account:role-assign",
                    "account:password-reset"})
    public Result<ConsoleAccountManagementService.ConsoleUserDetail> detail(
            @PathVariable Long id) {
        return Result.ok(accountService.getUser(id));
    }

    @PostMapping
    @RequirePermission("account:manage")
    public Result<ConsoleAccountManagementService.ConsoleUserDetail> save(
            @RequestBody ConsoleUserSaveRequest request) {
        return Result.ok(accountService.saveUser(
                request, operatorResolver.resolve()));
    }

    @PutMapping("/{id}/permissions")
    @RequirePermission(value = "account:role-assign",
            anyOf = {"account:manage"})
    public Result<ConsoleAccountManagementService.ConsoleUserDetail> permissions(
            @PathVariable Long id,
            @RequestBody ConsoleUserPermissionRequest request) {
        return Result.ok(accountService.saveUserPermissions(
                id, request, operatorResolver.resolve()));
    }

    @PutMapping("/{id}/status")
    @RequirePermission("account:manage")
    public Result<Void> status(@PathVariable Long id,
                               @RequestParam Integer status) {
        accountService.changeUserStatus(id, status, operatorResolver.resolve());
        return Result.ok();
    }

    @PutMapping("/{id}/password")
    @RequirePermission(value = "account:password-reset",
            anyOf = {"account:manage"})
    public Result<Void> resetPassword(
            @PathVariable Long id,
            @RequestBody ConsolePasswordResetRequest request) {
        accountService.resetPassword(id,
                request == null ? null : request.getPassword(),
                operatorResolver.resolve());
        return Result.ok();
    }
}
