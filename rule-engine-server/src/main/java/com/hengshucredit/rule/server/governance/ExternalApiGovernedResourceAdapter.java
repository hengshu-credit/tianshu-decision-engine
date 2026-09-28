package com.hengshucredit.rule.server.governance;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.server.mapper.RuleDataObjectMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalDatasourceMapper;
import com.hengshucredit.rule.server.service.ExternalApiConfigValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ExternalApiGovernedResourceAdapter extends SimpleEntityGovernedResourceAdapter<RuleExternalApiConfig> {
    private final GovernanceSecretCodec secrets;
    private final RuleExternalDatasourceMapper datasourceMapper;
    private final RuleDataObjectMapper dataObjectMapper;

    public ExternalApiGovernedResourceAdapter(EntityStore<RuleExternalApiConfig> store, GovernanceSecretCodec secrets,
                                              RuleExternalDatasourceMapper datasourceMapper,
                                              RuleDataObjectMapper dataObjectMapper) {
        super(GovernanceResourceTypes.EXTERNAL_API, RuleExternalApiConfig.class, store,
                RuleExternalApiConfig::getId, RuleExternalApiConfig::setId,
                RuleExternalApiConfig::getStatus, RuleExternalApiConfig::setStatus,
                Set.of("datasourceId", "apiCode", "apiName", "requestMethod", "endpointUrl"),
                Set.of("authApiConfig", "headerConfig", "asyncCallbackConfig"), secrets);
        this.secrets = secrets;
        this.datasourceMapper = datasourceMapper;
        this.dataObjectMapper = dataObjectMapper;
    }

    @Override
    public List<GovernanceIssue> validate(ResourceSnapshot draft) {
        List<GovernanceIssue> issues = new ArrayList<>(super.validate(draft));
        try {
            RuleExternalApiConfig config = JSON.parseObject(JSON.toJSONString(secrets.restore(draft)), RuleExternalApiConfig.class);
            ExternalApiConfigValidator.validate(config);
            RuleExternalDatasource datasource = datasourceMapper.selectById(config.getDatasourceId());
            RuleDataObject requestObject = config.getRequestObjectId() == null ? null
                    : dataObjectMapper.selectById(config.getRequestObjectId());
            RuleDataObject responseObject = config.getResponseObjectId() == null ? null
                    : dataObjectMapper.selectById(config.getResponseObjectId());
            ExternalApiConfigValidator.validateReferences(config, datasource, requestObject, responseObject);
        } catch (RuntimeException e) {
            issues.add(GovernanceIssue.error("EXTERNAL_API_CONFIG_INVALID", e.getMessage(),
                    GovernanceResourceTypes.EXTERNAL_API, null, "$"));
        }
        return issues;
    }
}
