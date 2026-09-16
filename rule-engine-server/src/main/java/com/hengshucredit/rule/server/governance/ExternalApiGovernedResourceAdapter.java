package com.hengshucredit.rule.server.governance;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.server.service.ExternalApiConfigValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ExternalApiGovernedResourceAdapter extends SimpleEntityGovernedResourceAdapter<RuleExternalApiConfig> {
    private final GovernanceSecretCodec secrets;

    public ExternalApiGovernedResourceAdapter(EntityStore<RuleExternalApiConfig> store, GovernanceSecretCodec secrets) {
        super(GovernanceResourceTypes.EXTERNAL_API, RuleExternalApiConfig.class, store,
                RuleExternalApiConfig::getId, RuleExternalApiConfig::setId,
                RuleExternalApiConfig::getStatus, RuleExternalApiConfig::setStatus,
                Set.of("datasourceId", "apiCode", "apiName", "requestMethod", "endpointUrl"),
                Set.of("authApiConfig", "headerConfig", "asyncCallbackConfig"), secrets);
        this.secrets = secrets;
    }

    @Override
    public List<GovernanceIssue> validate(ResourceSnapshot draft) {
        List<GovernanceIssue> issues = new ArrayList<>(super.validate(draft));
        try {
            ExternalApiConfigValidator.validate(JSON.parseObject(JSON.toJSONString(secrets.restore(draft)), RuleExternalApiConfig.class));
        } catch (RuntimeException e) {
            issues.add(GovernanceIssue.error("EXTERNAL_API_CONFIG_INVALID", e.getMessage(),
                    GovernanceResourceTypes.EXTERNAL_API, null, "$"));
        }
        return issues;
    }
}
