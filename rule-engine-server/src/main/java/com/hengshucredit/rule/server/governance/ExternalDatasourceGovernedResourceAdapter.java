package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.server.service.RuleExternalApiConfigService;

/**
 * 外数数据源审批删除必须复用直接删除路径的级联清理，避免留下接口配置和运行时缓存。
 */
public class ExternalDatasourceGovernedResourceAdapter
        extends SimpleEntityGovernedResourceAdapter<RuleExternalDatasource> {
    private final RuleExternalApiConfigService apiConfigService;

    public ExternalDatasourceGovernedResourceAdapter(EntityStore<RuleExternalDatasource> store,
                                                     RuleExternalApiConfigService apiConfigService,
                                                     GovernanceSecretCodec secretCodec) {
        super(GovernanceResourceTypes.EXTERNAL_DATASOURCE, RuleExternalDatasource.class, store,
                RuleExternalDatasource::getId, RuleExternalDatasource::setId,
                RuleExternalDatasource::getStatus, RuleExternalDatasource::setStatus,
                java.util.Set.of("datasourceCode", "datasourceName", "protocol"),
                java.util.Set.of("authConfig"), secretCodec);
        this.apiConfigService = apiConfigService;
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
