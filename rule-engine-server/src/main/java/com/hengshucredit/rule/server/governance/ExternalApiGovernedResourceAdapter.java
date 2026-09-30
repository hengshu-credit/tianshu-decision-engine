package com.hengshucredit.rule.server.governance;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDataObjectMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalDatasourceMapper;
import com.hengshucredit.rule.server.service.ExternalApiConfigValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ExternalApiGovernedResourceAdapter extends SimpleEntityGovernedResourceAdapter<RuleExternalApiConfig> {
    @jakarta.annotation.Resource private com.hengshucredit.rule.server.service.RuleBillingService billingService;
    @jakarta.annotation.Resource private com.hengshucredit.rule.server.service.RuleVariableService variableService;
    @jakarta.annotation.Resource private RuleDataObjectFieldMapper dataObjectFieldMapper;
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
                Set.of("authApiConfig", "headerConfig", "asyncCallbackConfig", "signatureSecret"), secrets);
        this.secrets = secrets;
        this.datasourceMapper = datasourceMapper;
        this.dataObjectMapper = dataObjectMapper;
    }

    @Override
    public List<ResourceDependencyRef> collectDependencies(ResourceSnapshot draft) {
        return DataObjectGovernanceReferences.owners(super.collectDependencies(draft), dataObjectFieldMapper);
    }

    @Override
    public ResourceSnapshot normalizeDraft(ResourceSnapshot draft) {
        var snapshot = com.hengshucredit.rule.server.artifact.CanonicalJson.readMap(draft.snapshotJson());
        if (snapshot.get("executionConfig") instanceof String text && !text.isBlank()) {
            snapshot.put("executionConfig", JSON.parseObject(text));
        }
        if (snapshot.get("executionConfig") != null && variableService != null && snapshot.get("datasourceId") != null) {
            var source = datasourceMapper.selectById(Long.valueOf(String.valueOf(snapshot.get("datasourceId"))));
            if (source != null) {
                var execution = JSON.parseObject(JSON.toJSONString(snapshot.get("executionConfig")));
                com.hengshucredit.rule.server.service.ExternalApiExecutionConfig.bindReferences(execution, variableService.buildRefScriptNameMap(source.getProjectId()));
                snapshot.put("executionConfig", execution);
            }
        }
        return super.normalizeDraft(new ResourceSnapshot(com.hengshucredit.rule.server.artifact.CanonicalJson.write(snapshot),
                draft.effectiveStatus(), draft.secretPayloadCiphertext(), draft.secretDigest()));
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
            if (config.getExecutionConfig() != null && variableService != null) {
                com.hengshucredit.rule.server.service.ExternalApiExecutionConfig.bindReferences(
                        JSON.parseObject(config.getExecutionConfig()), variableService.buildRefScriptNameMap(datasource.getProjectId()));
            }
        } catch (RuntimeException e) {
            issues.add(GovernanceIssue.error("EXTERNAL_API_CONFIG_INVALID", e.getMessage(),
                    GovernanceResourceTypes.EXTERNAL_API, null, "$"));
        }
        return issues;
    }

    @Override
    public AppliedResource apply(ApprovalApplyContext context) {
        AppliedResource applied = super.apply(context);
        RuleExternalApiConfig api = JSON.parseObject(JSON.toJSONString(secrets.restore(context.snapshot())), RuleExternalApiConfig.class);
        api.setId(applied.resourceId());
        api.setStatus("ACTIVE".equals(applied.effectiveStatus()) ? 1 : 0);
        if (billingService != null) billingService.syncApiBilling(api, datasourceMapper.selectById(api.getDatasourceId()));
        return applied;
    }
}
