package com.hengshucredit.rule.server.governance;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.server.mapper.RuleBillingConfigMapper;
import com.hengshucredit.rule.model.entity.RuleDbDatasource;
import com.hengshucredit.rule.model.entity.RuleDefinition;
import com.hengshucredit.rule.model.entity.RuleExperiment;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.mapper.RuleDataObjectMapper;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldOptionMapper;
import com.hengshucredit.rule.server.mapper.RuleDbDatasourceMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionContentMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionInputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionOutputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDefinitionRefMapper;
import com.hengshucredit.rule.server.mapper.RuleExperimentMapper;
import com.hengshucredit.rule.server.mapper.RuleExperimentGroupMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalApiConfigMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalDatasourceMapper;
import com.hengshucredit.rule.server.mapper.RuleFieldValidationMapper;
import com.hengshucredit.rule.server.mapper.RuleFunctionMapper;
import com.hengshucredit.rule.server.mapper.RuleModelInputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleModelMapper;
import com.hengshucredit.rule.server.mapper.RuleModelOutputFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleListLibraryMapper;
import com.hengshucredit.rule.server.mapper.RuleProjectMapper;
import com.hengshucredit.rule.server.mapper.RuleVariableMapper;
import com.hengshucredit.rule.server.mapper.RuleVariableOptionMapper;
import com.hengshucredit.rule.server.service.RuleLifecycleService;
import com.hengshucredit.rule.server.service.RuleDraftService;
import com.hengshucredit.rule.server.service.RuleListChangeBatchService;
import com.hengshucredit.rule.server.publish.RuleFunctionPushService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

@Configuration
public class GovernanceResourceAdapterConfiguration {

    @Bean
    public GovernedResourceAdapter variableGovernanceAdapter(
            RuleVariableMapper mapper,
            RuleVariableOptionMapper optionMapper,
            GovernanceSecretCodec secretCodec,
            VariableSourceReferenceValidator sourceValidator) {
        return new VariableGovernedResourceAdapter(
                store(mapper), optionMapper, secretCodec,
                sourceValidator);
    }

    @Bean
    public GovernedResourceAdapter dataObjectGovernanceAdapter(
            RuleDataObjectMapper mapper,
            RuleDataObjectFieldMapper fieldMapper,
            RuleDataObjectFieldOptionMapper optionMapper,
            GovernanceSecretCodec secretCodec,
            RuleVariableMapper variableMapper) {
        return new DataObjectGovernedResourceAdapter(
                store(mapper), fieldMapper, optionMapper,
                secretCodec, variableMapper, mapper);
    }

    @Bean
    public GovernedResourceAdapter modelGovernanceAdapter(
            RuleModelMapper mapper,
            RuleModelInputFieldMapper inputMapper,
            RuleModelOutputFieldMapper outputMapper,
            GovernanceSecretCodec secretCodec) {
        return new ModelGovernedResourceAdapter(
                store(mapper), inputMapper, outputMapper,
                secretCodec);
    }

    @Bean
    public GovernedResourceAdapter externalDatasourceGovernanceAdapter(
            RuleExternalDatasourceMapper mapper,
            GovernanceSecretCodec secretCodec) {
        return adapter(GovernanceResourceTypes.EXTERNAL_DATASOURCE,
                RuleExternalDatasource.class, mapper,
                RuleExternalDatasource::getId,
                RuleExternalDatasource::setId,
                RuleExternalDatasource::getStatus,
                RuleExternalDatasource::setStatus,
                Set.of("datasourceCode", "datasourceName",
                        "protocol"),
                Set.of("authConfig"), secretCodec);
    }

    @Bean
    public GovernedResourceAdapter externalApiGovernanceAdapter(
            RuleExternalApiConfigMapper mapper,
            GovernanceSecretCodec secretCodec) {
        return new ExternalApiGovernedResourceAdapter(store(mapper), secretCodec);
    }

    @Bean
    public GovernedResourceAdapter databaseGovernanceAdapter(
            RuleDbDatasourceMapper mapper,
            GovernanceSecretCodec secretCodec) {
        return adapter(GovernanceResourceTypes.DATABASE,
                RuleDbDatasource.class, mapper,
                RuleDbDatasource::getId, RuleDbDatasource::setId,
                RuleDbDatasource::getStatus,
                RuleDbDatasource::setStatus,
                Set.of("datasourceCode", "datasourceName", "dbType"),
                Set.of("password", "sshPassword", "sshPrivateKey",
                        "sshPassphrase"),
                secretCodec);
    }

    @Bean
    public GovernedResourceAdapter functionGovernanceAdapter(
            RuleFunctionMapper mapper,
            GovernanceSecretCodec secretCodec,
            RuleFunctionPushService functionPushService) {
        return new FunctionGovernedResourceAdapter(
                store(mapper), secretCodec, functionPushService);
    }

    @Bean
    public GovernedResourceAdapter ruleGovernanceAdapter(
            RuleDefinitionMapper mapper,
            GovernanceSecretCodec secretCodec,
            RuleLifecycleService lifecycleService,
            RuleDraftService draftService,
            RuleDefinitionContentMapper contentMapper,
            RuleDefinitionInputFieldMapper inputMapper,
            RuleDefinitionOutputFieldMapper outputMapper,
            RuleDataObjectFieldMapper dataObjectFieldMapper) {
        return new RuleGovernedResourceAdapter(
                store(mapper), secretCodec, lifecycleService,
                draftService,
                contentMapper, inputMapper, outputMapper,
                dataObjectFieldMapper);
    }

    @Bean
    public GovernedResourceAdapter ruleProjectBindingGovernanceAdapter(
            RuleDefinitionRefMapper refMapper,
            RuleDefinitionMapper definitionMapper,
            RuleProjectMapper projectMapper) {
        return new RuleProjectBindingGovernedResourceAdapter(
                refMapper, definitionMapper, projectMapper);
    }

    @Bean
    public GovernedResourceAdapter listLibraryGovernanceAdapter(
            RuleListLibraryMapper mapper,
            RuleProjectMapper projectMapper) {
        return new RuleListLibraryGovernedResourceAdapter(
                mapper, projectMapper);
    }

    @Bean
    public GovernedResourceAdapter listRecordBatchGovernanceAdapter(
            RuleListChangeBatchService batchService) {
        return new RuleListRecordBatchGovernedResourceAdapter(
                batchService);
    }

    @Bean
    public GovernedResourceAdapter fieldValidationGovernanceAdapter(
            RuleFieldValidationMapper mapper,
            RuleProjectMapper projectMapper,
            RuleDefinitionInputFieldMapper inputFieldMapper) {
        return new FieldValidationGovernedResourceAdapter(
                mapper, projectMapper, inputFieldMapper);
    }

    @Bean
    public GovernedResourceAdapter billingConfigGovernanceAdapter(
            RuleBillingConfigMapper mapper,
            RuleProjectMapper projectMapper,
            RuleDefinitionMapper definitionMapper,
            RuleDefinitionRefMapper definitionRefMapper,
            RuleExternalApiConfigMapper apiMapper,
            RuleExternalDatasourceMapper externalDatasourceMapper,
            RuleDbDatasourceMapper dbMapper) {
        return new BillingConfigGovernedResourceAdapter(
                mapper, projectMapper, definitionMapper,
                definitionRefMapper, apiMapper,
                externalDatasourceMapper, dbMapper);
    }

    @Bean
    public GovernedResourceAdapter experimentGovernanceAdapter(
            RuleExperimentMapper mapper,
            RuleExperimentGroupMapper groupMapper,
            GovernanceSecretCodec secretCodec) {
        return new ExperimentGovernedResourceAdapter(
                store(mapper), groupMapper, secretCodec);
    }

    @Bean
    public GovernedResourceAdapter projectGovernanceAdapter(
            RuleProjectMapper mapper,
            GovernanceSecretCodec secretCodec) {
        return adapter(GovernanceResourceTypes.PROJECT,
                RuleProject.class, mapper,
                RuleProject::getId, RuleProject::setId,
                RuleProject::getStatus,
                RuleProject::setStatus,
                Set.of("projectCode", "projectName"),
                Set.of(), secretCodec);
    }

    private <T> GovernedResourceAdapter adapter(
            String resourceType,
            Class<T> entityType,
            BaseMapper<T> mapper,
            Function<T, Long> idGetter,
            BiConsumer<T, Long> idSetter,
            Function<T, Integer> statusGetter,
            BiConsumer<T, Integer> statusSetter,
            Set<String> requiredKeys,
            Set<String> secretKeys,
            GovernanceSecretCodec secretCodec) {
        return new SimpleEntityGovernedResourceAdapter<>(
                resourceType, entityType, store(mapper),
                idGetter, idSetter, statusGetter, statusSetter,
                requiredKeys, secretKeys, secretCodec);
    }

    private <T> SimpleEntityGovernedResourceAdapter.EntityStore<T>
    store(BaseMapper<T> mapper) {
        return new SimpleEntityGovernedResourceAdapter.EntityStore<>() {
            @Override
            public T load(Long id) {
                return mapper.selectById(id);
            }

            @Override
            public void insert(T entity) {
                if (mapper.insert(entity) != 1) {
                    throw new IllegalStateException("资源创建失败");
                }
            }

            @Override
            public void update(T entity) {
                if (mapper.updateById(entity) != 1) {
                    throw new IllegalStateException(
                            "资源不存在或已被并发修改");
                }
            }
        };
    }
}
