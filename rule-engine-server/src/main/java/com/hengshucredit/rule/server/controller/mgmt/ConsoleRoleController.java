package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.dto.ConsoleRoleSaveRequest;
import com.hengshucredit.rule.model.entity.ConsolePermission;
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
@RequestMapping("/api/rule/console")
public class ConsoleRoleController {

    @Resource
    private ConsoleAccountManagementService accountService;
    @Resource
    private ConsoleOperatorResolver operatorResolver;

    @GetMapping("/roles")
    @RequirePermission(value = "role:view", anyOf = {"account:manage", "role:manage"})
    public Result<List<ConsoleAccountManagementService.ConsoleRoleDetail>> roles() {
        return Result.ok(accountService.listRoles());
    }

    @PostMapping("/roles")
    @RequirePermission("role:manage")
    public Result<ConsoleAccountManagementService.ConsoleRoleDetail> saveRole(
            @RequestBody ConsoleRoleSaveRequest request) {
        return Result.ok(accountService.saveRole(
                request, operatorResolver.resolve()));
    }

    @PutMapping("/roles/{id}/status")
    @RequirePermission("role:manage")
    public Result<Void> roleStatus(@PathVariable Long id,
                                   @RequestParam Integer status) {
        accountService.changeRoleStatus(id, status, operatorResolver.resolve());
        return Result.ok();
    }

    @GetMapping("/permissions")
    @RequirePermission(value = "role:view", anyOf = {"account:manage", "role:manage"})
    public Result<List<ConsolePermission>> permissions() {
        return Result.ok(accountService.listPermissions());
    }
}
