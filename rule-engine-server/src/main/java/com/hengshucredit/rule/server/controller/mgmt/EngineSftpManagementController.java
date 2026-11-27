package com.hengshucredit.rule.server.controller.mgmt;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hengshucredit.rule.model.ApiResponse;
import com.hengshucredit.rule.server.security.RequirePermission;
import com.hengshucredit.rule.server.service.ConsoleAccountManagementService;
import com.hengshucredit.rule.server.service.ConsoleOperatorResolver;
import com.hengshucredit.rule.server.sftp.EngineSftpAccounts;
import com.hengshucredit.rule.server.sftp.EngineSftpProtection;
import com.hengshucredit.rule.server.system.SystemSettingsAccess;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rule/sftp/engine")
public class EngineSftpManagementController {
    private final EngineSftpAccounts accounts;
    private final EngineSftpProtection protection;
    private final ConsoleAccountManagementService management;
    private final ConsoleOperatorResolver operator;
    private final SystemSettingsAccess access;

    @GetMapping("/accounts") @RequirePermission("account:manage")
    public ApiResponse<IPage<EngineSftpAccounts.Account>> accounts(HttpServletRequest request,
            @RequestParam(defaultValue="") String search,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) {
        access.require(request,"account:manage");
        return ApiResponse.ok(accounts.list(search,page,size));
    }

    public record Access(EngineSftpAccounts.AccessMode mode, String publicKey, boolean revoke, long revision) {}
    @PutMapping("/accounts/{id}") @RequirePermission("account:manage")
    public ApiResponse<Void> account(HttpServletRequest request,@PathVariable long id,@RequestBody Access data) {
        access.require(request,"account:manage");
        management.configureSftp(id,data.mode(),data.publicKey(),data.revoke(),data.revision(),operator.resolve());
        return ApiResponse.ok();
    }

    @GetMapping("/policy") @RequirePermission("system:edit")
    public ApiResponse<EngineSftpProtection.Policy> policy(HttpServletRequest request) {
        access.require(request,"system:edit");
        return ApiResponse.ok(protection.policy());
    }

    @PutMapping("/policy") @RequirePermission("system:edit")
    public ApiResponse<EngineSftpProtection.Policy> policy(HttpServletRequest request,@RequestBody EngineSftpProtection.Policy policy) {
        access.require(request,"system:edit");
        return ApiResponse.ok(protection.save(policy));
    }

    @GetMapping("/bans") @RequirePermission("system:edit")
    public ApiResponse<IPage<EngineSftpProtection.Ban>> bans(HttpServletRequest request,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) {
        access.require(request,"system:edit");
        return ApiResponse.ok(protection.bans(page,size));
    }

    public record Release(String subject,long blockedUntil) {}
    @PostMapping("/unblock") @RequirePermission("system:edit")
    public ApiResponse<Void> unblock(HttpServletRequest request,@RequestBody Release ban) {
        access.require(request,"system:edit");
        protection.unblock(ban.subject(),ban.blockedUntil());
        return ApiResponse.ok();
    }
}
