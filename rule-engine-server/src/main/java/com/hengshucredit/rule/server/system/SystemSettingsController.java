package com.hengshucredit.rule.server.system;

import com.hengshucredit.rule.model.ApiResponse;
import com.hengshucredit.rule.model.PlatformCode;
import com.hengshucredit.rule.server.audit.ConsoleOperationLogService;
import com.hengshucredit.rule.server.consolelogin.ConsoleMailService;
import com.hengshucredit.rule.server.security.RequirePermission;
import com.hengshucredit.rule.server.service.ConsoleOperatorResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class SystemSettingsController {
    private final SystemSettingsService settings;
    private final SystemSettingsAccess access;
    private final SystemBrandAssetService assets;
    private final ConsoleOperatorResolver operators;
    private final ConsoleOperationLogService logs;
    private final ConsoleMailService mail;
    private final com.hengshucredit.rule.server.governance.GovernanceApprovalService approvals;
    @org.springframework.beans.factory.annotation.Autowired
    private com.hengshucredit.rule.server.consolelogin.ConsoleAuthRateLimiter rateLimiter;
    public SystemSettingsController(SystemSettingsService settings, SystemSettingsAccess access, SystemBrandAssetService assets,
                                    ConsoleOperatorResolver operators, ConsoleOperationLogService logs, ConsoleMailService mail,
                                    com.hengshucredit.rule.server.governance.GovernanceApprovalService approvals) {
        this.settings = settings; this.access = access; this.assets = assets; this.operators = operators; this.logs = logs; this.mail = mail; this.approvals = approvals;
    }

    @GetMapping("/api/auth/console/brand")
    public ApiResponse<Map<String, Object>> brand() { return ApiResponse.ok(settings.publicBrand()); }

    @GetMapping("/api/auth/console/brand/assets/{id}")
    public ResponseEntity<byte[]> asset(@PathVariable String id) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noCache())
                .header("X-Content-Type-Options", "nosniff").body(assets.publicAsset(id));
    }

    @GetMapping("/api/rule/system/settings")
    @RequirePermission("system:view")
    public ApiResponse<Map<String, Object>> get(HttpServletRequest request) {
        access.require(request, "system:view");
        Map<String, Object> value = new LinkedHashMap<>();
        for (String group : new String[]{"brand", "mail", "access", "codes"}) value.put(group, settings.get(group));
        value.put("deploymentManaged", Map.of("mail", settings.deploymentManaged("mail"), "access", settings.deploymentManaged("access")));
        value.put("codeCatalog", Arrays.stream(PlatformCode.values()).map(code -> Map.of("code", code.code(), "message", code.message(), "httpStatus", code.httpStatus())).toList());
        value.put("states", approvals.resourceStates(SystemSettingsService.RESOURCE_TYPE, java.util.List.of(2L, 3L, 4L), operators.resolve()));
        return ApiResponse.ok(value);
    }

    @PutMapping("/api/rule/system/settings/brand")
    @RequirePermission("system:edit")
    public ApiResponse<Map<String, Object>> saveBrand(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        access.require(request, "system:edit"); return ApiResponse.ok(settings.saveBrand(body, operators.resolve()));
    }

    @PostMapping("/api/rule/system/settings/assets")
    @RequirePermission("system:edit")
    public ApiResponse<Map<String, String>> upload(@RequestParam MultipartFile file, HttpServletRequest request) throws java.io.IOException {
        access.require(request, "system:edit");
        if (file.isEmpty() || file.getSize() > 1024 * 1024) throw new IllegalArgumentException("图片大小应在 1 MB 以内");
        return ApiResponse.ok(Map.of("id", assets.upload(file.getBytes(), operators.resolve())));
    }

    @PostMapping("/api/rule/system/settings/mail/test")
    @RequirePermission("system:mail-test")
    public ApiResponse<Void> test(@RequestBody MailTest body, HttpServletRequest request) {
        access.require(request, "system:mail-test"); access.require(request, "system:edit");
        if (body == null) throw new IllegalArgumentException("邮件测试参数不能为空");
        rateLimiter.check("system-mail-test", operators.resolve(), request.getRemoteAddr());
        com.hengshucredit.rule.server.governance.ResourceSnapshot snapshot = null;
        if (body.draftId() != null) {
            var draft = approvals.editableWorkspace(SystemSettingsService.RESOURCE_TYPE, 2L, operators.resolve());
            if (draft == null || !body.draftId().equals(draft.id()) || !java.util.Objects.equals(body.snapshotDigest(), draft.snapshotDigest())) throw new IllegalArgumentException("邮件草稿已变化，请刷新后再测试");
            snapshot = approvals.editableCredentialSnapshot(SystemSettingsService.RESOURCE_TYPE, 2L, operators.resolve());
        }
        mail.test(body.recipient(), body.config(), snapshot); return ApiResponse.ok();
    }

    @GetMapping("/api/rule/system/settings/history")
    @RequirePermission("system:audit")
    public ApiResponse<com.baomidou.mybatisplus.core.metadata.IPage<com.hengshucredit.rule.model.entity.ConsoleOperationLog>> history(
            @RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "20") int pageSize, HttpServletRequest request) {
        access.require(request, "system:audit");
        return ApiResponse.ok(logs.systemChanges(pageNum, pageSize));
    }
    public record MailTest(String recipient, Map<String, Object> config, Long draftId, String snapshotDigest) { }
}
