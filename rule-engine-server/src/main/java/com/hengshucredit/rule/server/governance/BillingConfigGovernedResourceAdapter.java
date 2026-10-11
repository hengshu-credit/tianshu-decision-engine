package com.hengshucredit.rule.server.governance;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.RuleBillingConfig;
import com.hengshucredit.rule.model.entity.RuleDbDatasource;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleDefinitionRef;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.mapper.RuleBillingConfigMapper;
import com.hengshucredit.rule.server.mapper.RuleDbDatasourceMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionRefMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalApiConfigMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalDatasourceMapper;
import com.hengshucredit.rule.server.mapper.RuleProjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class BillingConfigGovernedResourceAdapter
        implements GovernedResourceAdapter {

    private static final Set<String> BILLING_TARGETS = Set.of(
            "ENGINE", "API", "DB");
    private static final Set<String> CHARGE_TYPES = Set.of(
            "COUNT", "SUCCESS", "DURATION", "FIXED");

    private final RuleBillingConfigMapper billingMapper;
    private final RuleProjectMapper projectMapper;
    private final RuleDefinitionMapper definitionMapper;
    private final RuleDefinitionRefMapper definitionRefMapper;
    private final RuleExternalApiConfigMapper apiMapper;
    private final RuleExternalDatasourceMapper externalDatasourceMapper;
    private final RuleDbDatasourceMapper dbMapper;

    public BillingConfigGovernedResourceAdapter(
            RuleBillingConfigMapper billingMapper,
            RuleProjectMapper projectMapper,
            RuleDefinitionMapper definitionMapper,
            RuleDefinitionRefMapper definitionRefMapper,
            RuleExternalApiConfigMapper apiMapper,
            RuleExternalDatasourceMapper externalDatasourceMapper,
            RuleDbDatasourceMapper dbMapper) {
        this.billingMapper = billingMapper;
        this.projectMapper = projectMapper;
        this.definitionMapper = definitionMapper;
        this.definitionRefMapper = definitionRefMapper;
        this.apiMapper = apiMapper;
        this.externalDatasourceMapper = externalDatasourceMapper;
        this.dbMapper = dbMapper;
    }

    @Override
    public String resourceType() {
        return GovernanceResourceTypes.BILLING_CONFIG;
    }

    @Override
    public ResourceSnapshot loadEffective(Long resourceId) {
        RuleBillingConfig config = billingMapper.selectById(resourceId);
        if (config == null) {
            throw new IllegalArgumentException(
                    "计费配置不存在: " + resourceId);
        }
        return normalizeDraft(new ResourceSnapshot(
                CanonicalJson.write(config),
                effectiveStatus(config.getStatus()), null, null));
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
        put(value, "billingCode", source.get("billingCode"));
        put(value, "billingName", source.get("billingName"));
        value.put("billingTarget",
                upper(source.get("billingTarget"), "ENGINE"));
        putLong(value, "targetRefId", source.get("targetRefId"));
        value.put("chargeType",
                upper(source.get("chargeType"), "COUNT"));
        value.put("unitPrice", decimalValue(
                source.get("unitPrice"), BigDecimal.ZERO));
        value.put("currency", defaultString(
                source.get("currency"), "CNY"));
        put(value, "effectiveTime", source.get("effectiveTime"));
        put(value, "expireTime", source.get("expireTime"));
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
        String code = stringValue(value.get("billingCode"));
        String name = stringValue(value.get("billingName"));
        String target = stringValue(value.get("billingTarget"));
        Long targetRefId = longValue(value.get("targetRefId"));
        String chargeType = stringValue(value.get("chargeType"));
        BigDecimal unitPrice = decimalValue(value.get("unitPrice"), null);
        String currency = stringValue(value.get("currency"));
        Integer status = integerValue(value.get("status"));
        String normalizedAction = upper(action, "UPDATE");
        List<GovernanceIssue> issues = new ArrayList<>();

        required(issues, code, "BILLING_CODE_REQUIRED",
                "计费编码不能为空", id, "$.billingCode");
        required(issues, name, "BILLING_NAME_REQUIRED",
                "计费名称不能为空", id, "$.billingName");
        required(issues, currency, "BILLING_CURRENCY_REQUIRED",
                "币种不能为空", id, "$.currency");
        if (!"GLOBAL".equals(scope) && !"PROJECT".equals(scope)) {
            issues.add(error("BILLING_SCOPE_INVALID",
                    "作用范围只能是全局或项目", id, "$.scope"));
        }
        if ("PROJECT".equals(scope)) {
            if (projectId == null || projectId <= 0) {
                issues.add(error("BILLING_PROJECT_REQUIRED",
                        "项目级计费配置必须选择所属项目", id,
                        "$.projectId"));
            } else if (projectMapper.selectById(projectId) == null) {
                issues.add(error("BILLING_PROJECT_NOT_FOUND",
                        "所属项目不存在", id, "$.projectId"));
            }
        }
        if (!BILLING_TARGETS.contains(target)) {
            issues.add(error("BILLING_TARGET_INVALID",
                    "计费对象类型不受支持", id, "$.billingTarget"));
        }
        if (!CHARGE_TYPES.contains(chargeType)) {
            issues.add(error("BILLING_CHARGE_TYPE_INVALID",
                    "计费方式不受支持", id, "$.chargeType"));
        }
        if (unitPrice == null || unitPrice.signum() < 0) {
            issues.add(error("BILLING_UNIT_PRICE_INVALID",
                    "计费单价必须是大于或等于零的数值", id,
                    "$.unitPrice"));
        }
        if (status == null || (status != 0 && status != 1)) {
            issues.add(error("BILLING_STATUS_INVALID",
                    "计费配置状态只能是启用或停用", id, "$.status"));
        }
        validateTimeRange(value, id, issues);

        RuleBillingConfig current = id == null
                ? null : billingMapper.selectById(id);
        if (id != null && current == null) {
            issues.add(error("BILLING_CONFIG_NOT_FOUND",
                    "计费配置已不存在，请刷新后重试", id, "$"));
        } else if (current != null) {
            if (!Objects.equals(current.getProjectId(), projectId)
                    || !Objects.equals(current.getScope(), scope)
                    || !Objects.equals(current.getBillingCode(), code)) {
                issues.add(error("BILLING_IDENTITY_CHANGED",
                        "计费编码和作用范围创建后不可修改", id, "$"));
            }
            if ("UPDATE".equals(normalizedAction)
                    && !Objects.equals(current.getStatus(), status)) {
                issues.add(error("BILLING_STATUS_ACTION_REQUIRED",
                        "启用或停用计费配置请使用对应生命周期动作",
                        id, "$.status"));
            }
        }

        if (hasText(code) && ("GLOBAL".equals(scope)
                || "PROJECT".equals(scope))) {
            LambdaQueryWrapper<RuleBillingConfig> query =
                    new LambdaQueryWrapper<RuleBillingConfig>()
                            .eq(RuleBillingConfig::getScope, scope)
                            .eq(RuleBillingConfig::getProjectId,
                                    "GLOBAL".equals(scope) ? 0L : projectId)
                            .eq(RuleBillingConfig::getBillingCode, code)
                            .ne(RuleBillingConfig::getStatus, -1);
            if (id != null) query.ne(RuleBillingConfig::getId, id);
            Long count = billingMapper.selectCount(query);
            if (count != null && count > 0) {
                issues.add(error("BILLING_CODE_DUPLICATE",
                        "同一作用范围内计费编码已存在", id,
                        "$.billingCode"));
            }
        }
        if (BILLING_TARGETS.contains(target) && targetRefId != null) {
            validateTarget(scope, projectId, target, targetRefId,
                    id, issues);
        }
        return issues;
    }

    @Override
    public List<ResourceDependencyRef> collectDependencies(
            ResourceSnapshot draft) {
        Map<String, Object> value = CanonicalJson.readMap(
                draft.snapshotJson());
        List<ResourceDependencyRef> dependencies = new ArrayList<>();
        Long projectId = longValue(value.get("projectId"));
        if ("PROJECT".equals(value.get("scope"))
                && projectId != null && projectId > 0) {
            dependencies.add(new ResourceDependencyRef(
                    GovernanceResourceTypes.PROJECT, projectId,
                    GovernanceResourceTypes.PROJECT, "$.projectId",
                    "BELONGS_TO", true));
        }
        Long targetId = longValue(value.get("targetRefId"));
        String targetType = targetResourceType(
                stringValue(value.get("billingTarget")));
        if (targetId != null && targetId > 0 && targetType != null) {
            dependencies.add(new ResourceDependencyRef(
                    targetType, targetId, targetType,
                    "$.targetRefId", "BILLS", true));
        }
        return dependencies;
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
        RuleBillingConfig config = JSON.parseObject(
                context.snapshot().snapshotJson(),
                RuleBillingConfig.class);
        String action = upper(context.action(), "UPDATE");
        String status = switch (action) {
            case "DRAFT_CREATE", "DRAFT_UPDATE" -> "DRAFT";
            case "CREATE", "ENABLE", "RESTORE" -> "ACTIVE";
            case "DISABLE" -> "DISABLED";
            case "DELETE", "DRAFT_DELETE" -> "DELETED";
            default -> effectiveStatus(config.getStatus());
        };
        config.setStatus("ACTIVE".equals(status) ? 1
                : "DELETED".equals(status) ? -1 : 0);
        if (context.insertRoot()) {
            config.setId(null);
            if (billingMapper.insert(config) != 1
                    || config.getId() == null) {
                throw new IllegalStateException("计费配置创建失败");
            }
        } else {
            if (context.resourceId() == null) {
                throw new IllegalArgumentException(
                        "非创建操作必须指定计费配置 ID");
            }
            config.setId(context.resourceId());
            if (billingMapper.updateById(config) != 1) {
                throw new IllegalStateException(
                        "计费配置不存在或已被并发修改");
            }
        }
        return new AppliedResource(config.getId(),
                context.nextVersionNo(), status, null);
    }

    private void validateTimeRange(Map<String, Object> value,
                                   Long id,
                                   List<GovernanceIssue> issues) {
        try {
            LocalDateTime effective = localDateTime(
                    value.get("effectiveTime"));
            LocalDateTime expire = localDateTime(value.get("expireTime"));
            if (effective != null && expire != null
                    && effective.isAfter(expire)) {
                issues.add(error("BILLING_TIME_RANGE_INVALID",
                        "生效时间不能晚于失效时间", id,
                        "$.expireTime"));
            }
        } catch (RuntimeException invalid) {
            issues.add(error("BILLING_TIME_FORMAT_INVALID",
                    "生效或失效时间格式无效", id, "$.effectiveTime"));
        }
    }

    private void validateTarget(String scope, Long projectId,
                                String target, Long targetId,
                                Long configId,
                                List<GovernanceIssue> issues) {
        if ("ENGINE".equals(target)) {
            RuleDefinition definition = definitionMapper.selectById(targetId);
            if (definition == null) {
                targetNotFound(configId, issues);
            } else if (!visibleRule(scope, projectId, definition)) {
                targetScopeMismatch(configId, issues);
            }
            return;
        }
        if ("API".equals(target)) {
            RuleExternalApiConfig api = apiMapper.selectById(targetId);
            RuleExternalDatasource datasource = api == null ? null
                    : externalDatasourceMapper.selectById(
                    api.getDatasourceId());
            if (api == null || datasource == null) {
                targetNotFound(configId, issues);
            } else if (!visible(scope, projectId, datasource.getScope(),
                    datasource.getProjectId())) {
                targetScopeMismatch(configId, issues);
            }
            return;
        }
        RuleDbDatasource datasource = dbMapper.selectById(targetId);
        if (datasource == null) {
            targetNotFound(configId, issues);
        } else if (!visible(scope, projectId, datasource.getScope(),
                datasource.getProjectId())) {
            targetScopeMismatch(configId, issues);
        }
    }

    private boolean visibleRule(String scope, Long projectId,
                                RuleDefinition definition) {
        boolean globalRule = "GLOBAL".equals(definition.getScope())
                || Long.valueOf(0L).equals(definition.getProjectId());
        if ("GLOBAL".equals(scope)) return globalRule;
        if (Objects.equals(projectId, definition.getProjectId())
                && !globalRule) return true;
        if (!globalRule || projectId == null) return false;
        Long count = definitionRefMapper.selectCount(
                new LambdaQueryWrapper<RuleDefinitionRef>()
                        .eq(RuleDefinitionRef::getDefinitionId,
                                definition.getId())
                        .eq(RuleDefinitionRef::getProjectId, projectId));
        return count != null && count > 0;
    }

    private boolean visible(String ownerScope, Long ownerProjectId,
                            String targetScope, Long targetProjectId) {
        if ("GLOBAL".equals(ownerScope)) {
            return "GLOBAL".equals(targetScope)
                    || Long.valueOf(0L).equals(targetProjectId);
        }
        return "GLOBAL".equals(targetScope)
                || Long.valueOf(0L).equals(targetProjectId)
                || Objects.equals(ownerProjectId, targetProjectId);
    }

    private void targetNotFound(Long id, List<GovernanceIssue> issues) {
        issues.add(error("BILLING_TARGET_NOT_FOUND",
                "指定的计费对象不存在", id, "$.targetRefId"));
    }

    private void targetScopeMismatch(Long id,
                                     List<GovernanceIssue> issues) {
        issues.add(error("BILLING_TARGET_SCOPE_MISMATCH",
                "计费对象不属于当前作用范围", id, "$.targetRefId"));
    }

    private String targetResourceType(String target) {
        return switch (target == null ? "" : target) {
            case "ENGINE" -> GovernanceResourceTypes.RULE;
            case "API" -> GovernanceResourceTypes.EXTERNAL_API;
            case "DB" -> GovernanceResourceTypes.DATABASE;
            default -> null;
        };
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

    private LocalDateTime localDateTime(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        return LocalDateTime.parse(String.valueOf(value));
    }

    private String effectiveStatus(Integer status) {
        if (Integer.valueOf(-1).equals(status)) return "DELETED";
        return Integer.valueOf(0).equals(status)
                ? "DISABLED" : "ACTIVE";
    }

    private void put(Map<String, Object> target, String key, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            target.put(key, value);
        }
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

    private String defaultString(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private long defaultLong(Object value, long fallback) {
        Long parsed = longValue(value);
        return parsed == null ? fallback : parsed;
    }

    private int defaultInteger(Object value, int fallback) {
        Integer parsed = integerValue(value);
        return parsed == null ? fallback : parsed;
    }

    private BigDecimal decimalValue(Object value, BigDecimal fallback) {
        if (value == null || String.valueOf(value).isBlank()) return fallback;
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException invalid) {
            return null;
        }
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
