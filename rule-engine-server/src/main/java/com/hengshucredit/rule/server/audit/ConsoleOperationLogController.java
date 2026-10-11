package com.hengshucredit.rule.server.audit;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hengshucredit.rule.model.entity.ConsoleOperationLog;
import com.hengshucredit.rule.server.common.Result;
import com.hengshucredit.rule.server.security.ConsoleAccessGuard;
import com.hengshucredit.rule.server.security.RequirePermission;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.Set;

@RestController
@RequestMapping("/api/rule/console")
@RequiredArgsConstructor
public class ConsoleOperationLogController {
    private final ConsoleOperationLogService logs;
    private final ConsoleAccessGuard access;

    @GetMapping("/operation-logs")
    @RequirePermission("account:audit")
    public Result<IPage<ConsoleOperationLog>> page(
            @RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String username, @RequestParam(required = false) String module,
            @RequestParam(required = false) String action, @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.ok(logs.page(pageNum, pageSize, username, module, action, outcome, keyword, startTime, endTime));
    }

    @GetMapping("/operation-logs/{id}")
    @RequirePermission("account:audit")
    public Result<ConsoleOperationLog> detail(@PathVariable Long id) { return Result.ok(logs.detail(id)); }

    @PostMapping("/activity")
    @RequirePermission("")
    public Result<Void> activity(@RequestBody Activity activity, HttpServletRequest request) {
        if (activity == null || !Set.of("PAGE_VIEW", "FIELD_VIEW", "FIELD_SELECT").contains(activity.action())) throw new IllegalArgumentException("不支持的操作事件");
        String page = ConsoleOperationAuditFilter.pagePath(activity.pagePath());
        if (page == null) throw new IllegalArgumentException("页面路径无效");
        ConsoleOperationPage location = ConsoleOperationPage.resolve(page);
        if (location == null) throw new IllegalArgumentException("不支持的页面");
        String module = location.module();
        if ("account".equals(module)) {
            if (!access.can("account:view") && !access.can("role:view") && !access.can("role:manage") && !access.can("account:audit")) access.require("account:view");
        } else if (!"dashboard".equals(module)) access.require(module + ":view");
        if (!"PAGE_VIEW".equals(activity.action())) access.requireResource(activity.resourceType(), "view");
        var state = ConsoleOperationAudit.state(request);
        if (state != null) {
            state.log.setAction(activity.action()); state.log.setModule(module); state.log.setPagePath(page);
            if ("PAGE_VIEW".equals(activity.action())) {
                // 对象由路由中的 ID 确认，名称由服务端读取；页面标题不冒充资源名称。
                state.page = location;
                if (activity.name() != null && activity.name().startsWith("页签 · ")) state.tab = ConsoleOperationAudit.limit(activity.name().substring(5).strip(), 40);
            } else {
                state.log.setTargetType(ConsoleOperationAudit.limit(activity.resourceType(), 64));
                state.log.setTargetId(ConsoleOperationAudit.limit(activity.resourceId(), 128));
                state.log.setTargetName(ConsoleOperationAudit.limit(activity.name(), 255));
                if (activity.version() != null && activity.version().matches("[A-Za-z0-9_.: -]{1,64}")) state.log.setTargetVersion(activity.version());
            }
        }
        return Result.ok();
    }

    public record Activity(String action, String pagePath, String resourceType, String resourceId, String name, String version) { }
}
