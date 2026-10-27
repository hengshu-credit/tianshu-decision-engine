package com.hengshucredit.rule.server.audit;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 页面定位只接受对象/版本身份参数，不接受测试输入、脚本或凭据。 */
public record ConsoleOperationPage(String module, String resourceType, String resourceId, String label) {
    private static final Set<String> IDS = Set.of("projectId", "ruleId", "definitionId", "sourceId", "versionId", "revisionId");
    private static final Map<String, String> MODULE_NAMES = Map.ofEntries(
            Map.entry("system", "系统配置"),
            Map.entry("project", "项目"), Map.entry("rule", "规则"), Map.entry("variable", "变量"),
            Map.entry("list", "名单"), Map.entry("model", "模型"), Map.entry("function", "函数"),
            Map.entry("database", "数据库"), Map.entry("datasource", "外数"), Map.entry("experiment", "分流实验"),
            Map.entry("billing", "账单"), Map.entry("approval", "审批"), Map.entry("transfer", "迁移"),
            Map.entry("log", "执行日志"), Map.entry("lineage", "血缘"), Map.entry("account", "账户"), Map.entry("dashboard", "看板"));

    public static String sanitize(String value) {
        if (value == null || !value.startsWith("/") || value.startsWith("//") || value.length() > 4096) return null;
        try {
            URI uri = URI.create(value);
            String path = uri.getRawPath();
            if (path == null || path.length() > 380 || path.matches(".*(?i:%0a|%0d|%00).*")) return null;
            StringBuilder result = new StringBuilder(path);
            for (var entry : parameters(value).entrySet()) {
                String pair = (result.indexOf("?") < 0 ? "?" : "&") + entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8);
                if (result.length() + pair.length() <= 512) result.append(pair);
            }
            return result.toString();
        } catch (IllegalArgumentException invalid) { return null; }
    }

    public static Map<String, String> parameters(String value) {
        Map<String, String> selected = new LinkedHashMap<>();
        if (value == null) return selected;
        try {
            String query = URI.create(value).getRawQuery();
            if (query == null) return selected;
            for (String pair : query.split("&")) {
                String[] parts = pair.split("=", 2);
                if (parts.length != 2) continue;
                String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8), text = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                boolean allowed = IDS.contains(key) && text.matches("[0-9]{1,19}")
                        || "version".equals(key) && text.matches("[0-9]{1,10}")
                        || "sourceType".equals(key) && Set.of("REVISION", "VERSION", "WORKSPACE").contains(text)
                        || "focus".equals(key) && text.matches("[A-Za-z][A-Za-z0-9_-]{0,31}");
                if (allowed) selected.put(key, text);
            }
        } catch (IllegalArgumentException ignored) { selected.clear(); }
        return selected;
    }

    public static ConsoleOperationPage resolve(String value) {
        String safe = sanitize(value);
        if (safe == null) return null;
        String[] parts = safe.split("\\?", 2)[0].substring(1).split("/");
        String section = parts[0];
        Map<String, String> query = parameters(safe);
        if ("designer".equals(section) && parts.length >= 3) {
            String label = switch (parts[1]) {
                case "table" -> "决策表设计页";
                case "tree" -> "决策树设计页";
                case "flow" -> "决策流设计页";
                case "ruleset" -> "规则集设计页";
                case "cross" -> "交叉表设计页";
                case "score" -> "评分卡设计页";
                case "cross-adv" -> "复杂交叉表设计页";
                case "score-adv" -> "复杂评分卡设计页";
                case "script" -> "QL脚本设计页";
                case "expression" -> "表达式设计页";
                default -> null;
            };
            return label == null ? null : new ConsoleOperationPage("rule", "RULE", id(parts[2]), label);
        }
        if ("test".equals(section)) return new ConsoleOperationPage("rule", "RULE", id(query.getOrDefault("definitionId", query.get("ruleId"))), "规则调试页");
        if ("sftp".equals(section)) {
            String label = parts.length == 1 ? "SFTP数据源配置页"
                    : parts.length == 2 && "logs".equals(parts[1]) ? "SFTP查询日志页" : null;
            // SFTP 与数据源接口共用数据库模块权限，页面本身不代表某个数据源 ID。
            return label == null ? null : new ConsoleOperationPage("database", "SFTP", null, label);
        }
        String name = MODULE_NAMES.get(section);
        if (name == null) return null;
        String module = "variable".equals(section) ? "field" : section;
        String type = switch (section) {
            case "system" -> "SYSTEM_SETTING";
            case "project" -> "PROJECT";
            case "rule" -> "RULE";
            case "list" -> "LIST_LIBRARY";
            case "model" -> "MODEL";
            case "database" -> "DATABASE";
            case "experiment" -> "EXPERIMENT";
            case "approval" -> "APPROVAL_REQUEST";
            case "datasource" -> parts.length > 1 && "api".equals(parts[1]) ? "EXTERNAL_API" : "EXTERNAL_DATASOURCE";
            default -> null;
        };
        String targetId = parts.length > 1 ? id(parts[parts.length - 1]) : null;
        String label = name + (targetId == null ? "管理页" : "详情页");
        if ("datasource".equals(section) && parts.length > 1) label = ("api".equals(parts[1]) ? "外数 API" : "外数数据源") + "详情页";
        if (parts.length > 1 && "new".equals(parts[parts.length - 1])) label = "新建" + name + "页";
        if ("dashboard".equals(section)) label = parts.length > 1 ? "看板设置页" : "数据看板";
        return new ConsoleOperationPage(module, type, targetId, label);
    }

    private static String id(String value) { return value != null && value.matches("[1-9][0-9]{0,18}") ? value : null; }
}
