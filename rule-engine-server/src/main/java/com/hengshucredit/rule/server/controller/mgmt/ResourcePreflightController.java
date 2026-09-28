package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.dto.ResourcePreflightReport;
import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.service.ResourcePreflightService;
import com.hengshucredit.rule.server.security.RequirePermission;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rule/preflight")
public class ResourcePreflightController {
    @Resource private ResourcePreflightService service;

    @GetMapping("/{resourceType}/{id:\\d+}")
    @RequirePermission("rule:view")
    public R<ResourcePreflightReport> check(@PathVariable String resourceType, @PathVariable Long id) {
        try {
            ResourcePreflightReport report = switch (resourceType.trim().toUpperCase(java.util.Locale.ROOT)) {
                case "API", "EXTERNAL_API" -> service.externalApi(id);
                case "DB", "DATABASE" -> service.database(id);
                case "VARIABLE" -> service.variable(id);
                case "MODEL" -> service.model(id);
                default -> throw new IllegalArgumentException("资源类型不受支持: " + resourceType);
            };
            return R.ok(report);
        } catch (IllegalArgumentException error) {
            return R.fail(400, error.getMessage());
        }
    }
}
