package com.hengshucredit.rule.server.security;

import com.hengshucredit.rule.server.consolelogin.RuleEngineConsoleLoginProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.Locale;

/** 在跨模块业务流程中按真实资源类型检查权限，不能仅依赖入口模块权限。 */
@Service
@RequiredArgsConstructor
public class ConsoleAccessGuard {
    private final RuleEngineConsoleLoginProperties properties;
    private final ConsolePermissionService permissions;

    public boolean can(String permission) {
        if (!properties.isEnabled()) return true;
        // SDK、后台任务没有控制台请求；它们由各自的项目鉴权链路负责。
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) return true;
        var session = attributes.getRequest().getSession(false);
        Object id = session == null ? null : session.getAttribute(properties.getSessionUserIdAttribute());
        return id instanceof Number number && permissions.hasPermission(number.longValue(), permission);
    }

    public void require(String... codes) {
        for (String code : codes) if (!can(code)) throw new ConsoleAccessDeniedException(code);
    }

    public void requireResource(String type, String action) { require(resourceModule(type) + ":" + action); }

    public static String resourceModule(String type) {
        if (type == null) throw new IllegalArgumentException("资源类型不能为空");
        return switch (type.trim().toUpperCase(Locale.ROOT)) {
            case "VARIABLE", "CONSTANT", "DATA_OBJECT", "DATA_OBJECT_FIELD", "DATA_FIELD", "FIELD_VALIDATION" -> "field";
            case "MODEL", "MODEL_OUTPUT" -> "model";
            case "EXTERNAL_DATASOURCE", "EXTERNAL_API", "API", "DATASOURCE" -> "datasource";
            case "DATABASE", "DB" -> "database";
            case "SFTP" -> "database";
            case "FUNCTION" -> "function";
            case "RULE", "RULE_VERSION", "RULE_PROJECT_BINDING" -> "rule";
            case "LIST", "LIST_LIBRARY", "LIST_RECORD_BATCH" -> "list";
            case "PROJECT" -> "project";
            case "BILLING_CONFIG" -> "billing";
            case "EXPERIMENT" -> "experiment";
            default -> throw new IllegalArgumentException("不支持的资源类型: " + type);
        };
    }
}
