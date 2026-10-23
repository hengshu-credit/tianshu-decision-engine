package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.server.service.RuleExternalApiConfigService;

/**
 * 外数数据源审批删除必须复用直接删除路径的级联清理，避免留下接口配置和运行时缓存。
 */
public class ExternalDatasourceGovernedResourceAdapter
        extends SimpleEntityGovernedResourceAdapter<RuleExternalDatasource> {
    private final RuleExternalApiConfigService apiConfigService;
    private final GovernanceSecretCodec secrets;
    @jakarta.annotation.Resource private com.hengshucredit.rule.server.service.RuleVariableService variableService;
    @jakarta.annotation.Resource private com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper dataObjectFieldMapper;

    public ExternalDatasourceGovernedResourceAdapter(EntityStore<RuleExternalDatasource> store,
                                                     RuleExternalApiConfigService apiConfigService,
                                                     GovernanceSecretCodec secretCodec) {
        super(GovernanceResourceTypes.EXTERNAL_DATASOURCE, RuleExternalDatasource.class, store,
                RuleExternalDatasource::getId, RuleExternalDatasource::setId,
                RuleExternalDatasource::getStatus, RuleExternalDatasource::setStatus,
                java.util.Set.of("datasourceCode", "datasourceName", "protocol"),
                java.util.Set.of("authConfig"), secretCodec);
        this.apiConfigService = apiConfigService;
        this.secrets = secretCodec;
    }

    @Override
    public java.util.List<ResourceDependencyRef> collectDependencies(ResourceSnapshot draft) {
        return DataObjectGovernanceReferences.owners(dependenciesIn(
                com.hengshucredit.rule.server.artifact.CanonicalJson.write(secrets.restore(draft))), dataObjectFieldMapper);
    }

    @Override
    public java.util.List<GovernanceIssue> validate(ResourceSnapshot draft) {
        var issues = new java.util.ArrayList<>(super.validate(draft));
        try {
            RuleExternalDatasource datasource = com.alibaba.fastjson.JSON.parseObject(
                    com.alibaba.fastjson.JSON.toJSONString(secrets.restore(draft)), RuleExternalDatasource.class);
            Long projectId = "GLOBAL".equals(datasource.getScope()) ? 0L : datasource.getProjectId();
            com.hengshucredit.rule.server.service.ExternalApiAuthConfig.validate(datasource.getAuthConfig(),
                    variableService == null ? java.util.Map.of() : variableService.buildRefScriptNameMap(projectId));
        } catch (RuntimeException error) {
            issues.add(GovernanceIssue.error("EXTERNAL_DATASOURCE_AUTH_INVALID", error.getMessage(), resourceType(), null, "$.authConfig"));
        }
        return issues;
    }

    @Override
    public AppliedResource apply(ApprovalApplyContext context) {
        AppliedResource applied = super.apply(context);
        if ("DELETE".equalsIgnoreCase(context.action()) && applied.resourceId() != null) {
            apiConfigService.deleteByDatasourceId(applied.resourceId());
        }
        return applied;
    }
}
