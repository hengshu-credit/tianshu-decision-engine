package com.hengshucredit.rule.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Data
@NoArgsConstructor
public class RuleValidationIssue {
    private String severity;
    private String code;
    /** 面向业务人员的校验标题，避免页面按 code 猜测文案。 */
    private String title;
    private String path;
    private String resourceType;
    private Long resourceId;
    private String message;
    /** 修复后应执行的下一步，例如“重新编译并提交预检”。 */
    private String nextAction;
    private Long revisionId;
    private String refType;
    private Map<String, Object> details = new LinkedHashMap<>();

    public RuleValidationIssue(String severity, String code, String path, String resourceType,
                               Long resourceId, String message) {
        this(severity, code, path, resourceType, resourceId, message,
                null, null, Collections.emptyMap());
    }

    public RuleValidationIssue(String severity, String code, String path, String resourceType,
                               Long resourceId, String message, Long revisionId, String refType,
                               Map<String, Object> details) {
        this.severity = severity;
        this.code = code;
        this.path = path;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.message = message;
        this.revisionId = revisionId;
        this.refType = refType;
        setDetails(details);
    }

    public static RuleValidationIssue error(String code, String path, String message) {
        return new RuleValidationIssue("ERROR", code, path, null, null, message)
                .withTitle(defaultTitle(code)).withNextAction(defaultNextAction(code));
    }

    public static RuleValidationIssue error(String code, String path, String resourceType,
                                            Long resourceId, String message) {
        return new RuleValidationIssue("ERROR", code, path, resourceType, resourceId, message)
                .withTitle(defaultTitle(code)).withNextAction(defaultNextAction(code));
    }

    public static RuleValidationIssue warning(String code, String path, String message) {
        return new RuleValidationIssue("WARNING", code, path, null, null, message)
                .withTitle(defaultTitle(code)).withNextAction(defaultNextAction(code));
    }

    public RuleValidationIssue withTitle(String title) {
        this.title = title;
        return this;
    }

    public RuleValidationIssue withNextAction(String nextAction) {
        this.nextAction = nextAction;
        return this;
    }

    private static String defaultTitle(String code) {
        if (code == null || code.isBlank()) return "配置校验问题";
        return switch (code) {
            case "COMPILE_FAILED" -> "编译未通过";
            case "BREAKING_SCHEMA_CHANGE" -> "存在破坏性字段变更";
            case "REFERENCE_MISSING", "MISSING_REFERENCE" -> "存在失效引用";
            case "DEPENDENCY_CHANGED" -> "依赖版本发生变化";
            default -> "配置校验问题";
        };
    }

    private static String defaultNextAction(String code) {
        if (code != null && code.contains("COMPILE")) return "修复配置后重新编译，再执行发布前校验";
        if (code != null && (code.contains("REFERENCE") || code.contains("DEPENDENCY"))) return "按引用位置修复依赖，再执行发布前校验";
        return "按问题位置修复配置，再执行发布前校验";
    }

    public RuleValidationIssue withRevisionId(Long revisionId) {
        this.revisionId = revisionId;
        return this;
    }

    public RuleValidationIssue withReference(String refType, Long referenceId) {
        this.refType = refType;
        this.resourceId = referenceId;
        return this;
    }

    public RuleValidationIssue withReference(Long referenceId, String refType) {
        return withReference(refType, referenceId);
    }

    public RuleValidationIssue withSafeDetail(String key, Object value) {
        requireSafeDetailKey(key);
        if (details == null) {
            details = new LinkedHashMap<>();
        }
        details.put(key, value);
        return this;
    }

    public Map<String, Object> getDetails() {
        if (details == null || details.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(details);
    }

    public void setDetails(Map<String, Object> details) {
        LinkedHashMap<String, Object> safeDetails = new LinkedHashMap<>();
        if (details != null) {
            for (Map.Entry<String, Object> entry : details.entrySet()) {
                requireSafeDetailKey(entry.getKey());
                safeDetails.put(entry.getKey(), entry.getValue());
            }
        }
        this.details = safeDetails;
    }

    private static void requireSafeDetailKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("诊断详情键不能为空");
        }
        String normalized = key.replace("_", "").replace("-", "")
                .toLowerCase(Locale.ROOT);
        if (normalized.contains("token") || normalized.contains("secret")
                || normalized.contains("password") || normalized.contains("rawbody")) {
            throw new IllegalArgumentException("诊断详情禁止包含敏感信息");
        }
        if (!(normalized.endsWith("id") || normalized.endsWith("type")
                || normalized.endsWith("path") || normalized.contains("revision")
                || normalized.contains("digest") || normalized.contains("summary"))) {
            throw new IllegalArgumentException("诊断详情键不在安全白名单中");
        }
    }
}
