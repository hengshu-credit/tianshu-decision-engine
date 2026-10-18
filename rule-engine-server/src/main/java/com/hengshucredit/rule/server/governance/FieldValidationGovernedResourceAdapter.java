package com.hengshucredit.rule.server.governance;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleFieldValidation;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.mapper.RuleDefinitionInputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleFieldValidationMapper;
import com.hengshucredit.rule.server.mapper.RuleProjectMapper;
import com.hengshucredit.rule.server.service.BuiltinFieldValidationCatalog;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public class FieldValidationGovernedResourceAdapter
        implements GovernedResourceAdapter {

    private static final Set<String> VALIDATION_TYPES = Set.of(
            "REQUIRED", "REGEX", "MIN_VALUE", "MAX_VALUE",
            "MIN_LENGTH", "MAX_LENGTH", "IN", "NOT_IN");

    private final RuleFieldValidationMapper validationMapper;
    private final RuleProjectMapper projectMapper;
    private final RuleDefinitionInputFieldMapper inputFieldMapper;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.hengshucredit.rule.server.service.FieldValidationReferenceScanner referenceScanner;

    public FieldValidationGovernedResourceAdapter(
            RuleFieldValidationMapper validationMapper,
            RuleProjectMapper projectMapper,
            RuleDefinitionInputFieldMapper inputFieldMapper) {
        this.validationMapper = validationMapper;
        this.projectMapper = projectMapper;
        this.inputFieldMapper = inputFieldMapper;
    }

    @Override
    public String resourceType() {
        return GovernanceResourceTypes.FIELD_VALIDATION;
    }

    @Override
    public ResourceSnapshot loadEffective(Long resourceId) {
        RuleFieldValidation rule = validationMapper.selectById(resourceId);
        if (rule == null) {
            throw new IllegalArgumentException(
                    "字段校验规则不存在: " + resourceId);
        }
        return normalizeDraft(new ResourceSnapshot(
                CanonicalJson.write(rule), effectiveStatus(rule.getStatus()),
                null, null));
    }

    @Override
    public ResourceSnapshot normalizeDraft(ResourceSnapshot draft) {
        Map<String, Object> source = CanonicalJson.readMap(
                draft == null ? null : draft.snapshotJson());
        Map<String, Object> value = new LinkedHashMap<>();
        putLong(value, "id", source.get("id"));
        String scope = upper(source.get("scope"), "PROJECT");
        value.put("projectId", "GLOBAL".equals(scope)
                ? 0L : defaultLong(source.get("projectId"), 0L));
        value.put("scope", scope);
        put(value, "validationCode", source.get("validationCode"));
        put(value, "validationName", source.get("validationName"));
        value.put("validationType",
                upper(source.get("validationType"), ""));
        put(value, "validationValue", source.get("validationValue"));
        put(value, "errorMessage", source.get("errorMessage"));
        put(value, "description", source.get("description"));
        value.put("status", defaultInteger(source.get("status"), 1));
        String status = draft == null || draft.effectiveStatus() == null
                ? effectiveStatus(defaultInteger(source.get("status"), 1))
                : draft.effectiveStatus();
        return new ResourceSnapshot(CanonicalJson.write(value), status,
                null, null);
    }

    @Override
    public List<GovernanceIssue> validate(ResourceSnapshot draft) {
        return validate(draft, "UPDATE");
    }

    @Override
    public List<GovernanceIssue> validate(ResourceSnapshot draft,
                                          String action) {
        Map<String, Object> value = CanonicalJson.readMap(
                draft.snapshotJson());
        Long id = longValue(value.get("id"));
        Long projectId = longValue(value.get("projectId"));
        String scope = stringValue(value.get("scope"));
        String code = stringValue(value.get("validationCode"));
        String name = stringValue(value.get("validationName"));
        String type = stringValue(value.get("validationType"));
        String validationValue = stringValue(value.get("validationValue"));
        String errorMessage = stringValue(value.get("errorMessage"));
        Integer status = integerValue(value.get("status"));
        String normalizedAction = upper(action, "UPDATE");
        List<GovernanceIssue> issues = new ArrayList<>();

        required(issues, code, "FIELD_VALIDATION_CODE_REQUIRED",
                "校验编码不能为空", id, "$.validationCode");
        required(issues, name, "FIELD_VALIDATION_NAME_REQUIRED",
                "校验名称不能为空", id, "$.validationName");
        required(issues, errorMessage,
                "FIELD_VALIDATION_MESSAGE_REQUIRED",
                "失败提示不能为空", id, "$.errorMessage");
        if (!VALIDATION_TYPES.contains(type)) {
            issues.add(error("FIELD_VALIDATION_TYPE_INVALID",
                    "校验类型不受支持", id, "$.validationType"));
        } else if (!validValue(type, validationValue)) {
            issues.add(error("FIELD_VALIDATION_VALUE_INVALID",
                    "校验值与当前校验类型不匹配", id,
                    "$.validationValue"));
        }
        if (!"GLOBAL".equals(scope) && !"PROJECT".equals(scope)) {
            issues.add(error("FIELD_VALIDATION_SCOPE_INVALID",
                    "作用范围只能是全局或项目", id, "$.scope"));
        }
        if (status == null || (status != 0 && status != 1)) {
            issues.add(error("FIELD_VALIDATION_STATUS_INVALID",
                    "字段校验状态只能是启用或停用", id, "$.status"));
        }
        if ("PROJECT".equals(scope)) {
            if (projectId == null || projectId <= 0) {
                issues.add(error("FIELD_VALIDATION_PROJECT_REQUIRED",
                        "项目级字段校验必须选择所属项目", id,
                        "$.projectId"));
            } else if (projectMapper.selectById(projectId) == null) {
                issues.add(error("FIELD_VALIDATION_PROJECT_NOT_FOUND",
                        "所属项目不存在", id, "$.projectId"));
            }
        }

        RuleFieldValidation current = id == null
                ? null : validationMapper.selectById(id);
        if (id != null && current == null) {
            issues.add(error("FIELD_VALIDATION_NOT_FOUND",
                    "字段校验规则已不存在，请刷新后重试", id, "$"));
        } else if (current != null) {
            if (BuiltinFieldValidationCatalog.isBuiltin(current)) {
                issues.add(error("FIELD_VALIDATION_BUILTIN_IMMUTABLE",
                        "系统内置校验规则不可变更", id, "$"));
            } else if (!Objects.equals(current.getProjectId(), projectId)
                    || !Objects.equals(current.getScope(), scope)
                    || !Objects.equals(current.getValidationCode(), code)) {
                issues.add(error("FIELD_VALIDATION_IDENTITY_CHANGED",
                        "校验编码和作用范围创建后不可修改", id, "$"));
            }
            if ("UPDATE".equals(normalizedAction)
                    && !Objects.equals(current.getStatus(), status)) {
                issues.add(error(
                        "FIELD_VALIDATION_STATUS_ACTION_REQUIRED",
                        "启用或停用字段校验请使用对应生命周期动作",
                        id, "$.status"));
            }
        } else if (BuiltinFieldValidationCatalog.isReservedCode(code)) {
            issues.add(error("FIELD_VALIDATION_CODE_RESERVED",
                    "校验编码为系统内置规则保留", id,
                    "$.validationCode"));
        }

        if (hasText(code) && ("GLOBAL".equals(scope)
                || "PROJECT".equals(scope))) {
            LambdaQueryWrapper<RuleFieldValidation> query =
                    new LambdaQueryWrapper<RuleFieldValidation>()
                            .eq(RuleFieldValidation::getScope, scope)
                            .eq(RuleFieldValidation::getProjectId,
                                    "GLOBAL".equals(scope) ? 0L : projectId)
                            .eq(RuleFieldValidation::getValidationCode, code)
                            .ne(RuleFieldValidation::getStatus, -1);
            if (id != null) query.ne(RuleFieldValidation::getId, id);
            Long count = validationMapper.selectCount(query);
            if (count != null && count > 0) {
                issues.add(error("FIELD_VALIDATION_CODE_DUPLICATE",
                        "同一作用范围内校验编码已存在", id,
                        "$.validationCode"));
            }
        }

        if (id != null && ("DISABLE".equals(normalizedAction)
                || "DELETE".equals(normalizedAction)) && referenced(id)) {
            issues.add(error("FIELD_VALIDATION_IN_USE",
                    "字段校验规则已被规则、实验或外数 API 引用，不能停用或删除",
                    id, "$"));
        }
        return issues;
    }

    @Override
    public List<ResourceDependencyRef> collectDependencies(
            ResourceSnapshot draft) {
        Map<String, Object> value = CanonicalJson.readMap(
                draft.snapshotJson());
        Long projectId = longValue(value.get("projectId"));
        if (!"PROJECT".equals(value.get("scope"))
                || projectId == null || projectId <= 0) {
            return List.of();
        }
        return List.of(new ResourceDependencyRef(
                GovernanceResourceTypes.PROJECT, projectId,
                GovernanceResourceTypes.PROJECT, "$.projectId",
                "BELONGS_TO", true));
    }

    @Override
    public ResourceDiff diff(ResourceSnapshot left,
                             ResourceSnapshot right) {
        return JsonResourceDiff.compare(left, right);
    }

    @Override
    public boolean supportsDraftWorkspace() { return true; }

    @Override
    public AppliedResource apply(ApprovalApplyContext context) {
        RuleFieldValidation rule = JSON.parseObject(
                context.snapshot().snapshotJson(),
                RuleFieldValidation.class);
        String action = upper(context.action(), "UPDATE");
        if (("DELETE".equals(action) || "DRAFT_DELETE".equals(action)) && context.resourceId() != null && referenced(context.resourceId())) {
            throw new IllegalArgumentException("字段校验规则仍被规则、实验或外数 API 引用，请先删除下游配置");
        }
        String status = switch (action) {
            case "DRAFT_CREATE", "DRAFT_UPDATE" -> "DRAFT";
            case "CREATE", "ENABLE", "RESTORE" -> "ACTIVE";
            case "DISABLE" -> "DISABLED";
            case "DELETE", "DRAFT_DELETE" -> "DELETED";
            default -> effectiveStatus(rule.getStatus());
        };
        rule.setStatus("ACTIVE".equals(status) ? 1
                : "DELETED".equals(status) ? -1 : 0);
        if (context.insertRoot()) {
            rule.setId(null);
            if (validationMapper.insert(rule) != 1
                    || rule.getId() == null) {
                throw new IllegalStateException("字段校验规则创建失败");
            }
        } else {
            if (context.resourceId() == null) {
                throw new IllegalArgumentException(
                        "非创建操作必须指定字段校验规则 ID");
            }
            rule.setId(context.resourceId());
            if (validationMapper.updateById(rule) != 1) {
                throw new IllegalStateException(
                        "字段校验规则不存在或已被并发修改");
            }
        }
        return new AppliedResource(rule.getId(), context.nextVersionNo(),
                status, null);
    }

    private boolean referenced(Long id) {
        if (referenceScanner != null) return referenceScanner.referenced(id);
        List<RuleDefinitionInputField> fields = inputFieldMapper.selectList(
                new LambdaQueryWrapper<RuleDefinitionInputField>()
                        .inSql(RuleDefinitionInputField::getDefinitionId,
                                "SELECT id FROM rule_definition WHERE status <> -1")
                        .isNotNull(RuleDefinitionInputField
                                ::getValidationRuleIds));
        for (RuleDefinitionInputField field : fields == null
                ? List.<RuleDefinitionInputField>of() : fields) {
            if (!hasText(field.getValidationRuleIds())) continue;
            try {
                List<Long> ids = JSON.parseArray(
                        field.getValidationRuleIds(), Long.class);
                if (ids != null && ids.contains(id)) return true;
            } catch (RuntimeException invalidLegacyValue) {
                throw new IllegalArgumentException(
                        "规则输入字段包含无效的校验规则引用", invalidLegacyValue);
            }
        }
        return false;
    }

    private boolean validValue(String type, String value) {
        if ("REQUIRED".equals(type)) return true;
        if (!hasText(value)) return false;
        try {
            String trimmed = value.trim();
            if ("REGEX".equals(type)) Pattern.compile(value);
            if ("MIN_VALUE".equals(type) || "MAX_VALUE".equals(type)) {
                new BigDecimal(trimmed);
            }
            if ("MIN_LENGTH".equals(type) || "MAX_LENGTH".equals(type)) {
                if (Integer.parseInt(trimmed) < 0) return false;
            }
            if ("IN".equals(type) || "NOT_IN".equals(type)) {
                List<?> values = trimmed.startsWith("[")
                        ? JSON.parseArray(trimmed)
                        : List.of(trimmed.split(","));
                return values.stream().anyMatch(item -> item != null
                        && !String.valueOf(item).trim().isEmpty());
            }
            return true;
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private void required(List<GovernanceIssue> issues, String value,
                          String code, String message, Long resourceId,
                          String path) {
        if (!hasText(value)) {
            issues.add(error(code, message, resourceId, path));
        }
    }

    private GovernanceIssue error(String code, String message,
                                  Long resourceId, String path) {
        return GovernanceIssue.error(code, message, resourceType(),
                resourceId, path);
    }

    private String effectiveStatus(Integer status) {
        if (Integer.valueOf(-1).equals(status)) return "DELETED";
        return Integer.valueOf(0).equals(status)
                ? "DISABLED" : "ACTIVE";
    }

    private void put(Map<String, Object> target, String key, Object value) {
        if (value != null) target.put(key, value);
    }

    private void putLong(Map<String, Object> target,
                         String key, Object value) {
        Long parsed = longValue(value);
        if (parsed != null) target.put(key, parsed);
    }

    private String upper(Object value, String fallback) {
        String text = stringValue(value);
        return hasText(text) ? text.trim().toUpperCase(Locale.ROOT)
                : fallback;
    }

    private long defaultLong(Object value, long fallback) {
        Long parsed = longValue(value);
        return parsed == null ? fallback : parsed;
    }

    private int defaultInteger(Object value, int fallback) {
        Integer parsed = integerValue(value);
        return parsed == null ? fallback : parsed;
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null || String.valueOf(value).isBlank()) return null;
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value == null || String.valueOf(value).isBlank()) return null;
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
