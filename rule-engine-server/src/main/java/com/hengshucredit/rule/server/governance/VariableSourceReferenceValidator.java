package com.hengshucredit.rule.server.governance;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.model.entity.RuleDbDatasource;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.model.entity.RuleListLibrary;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.mapper.RuleDbDatasourceMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalApiConfigMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalDatasourceMapper;
import com.hengshucredit.rule.server.mapper.RuleListLibraryMapper;
import com.hengshucredit.rule.server.service.DatabaseQueryOptions;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class VariableSourceReferenceValidator {

    @Resource
    private RuleExternalApiConfigMapper apiConfigMapper;

    @Resource
    private RuleExternalDatasourceMapper externalDatasourceMapper;

    @Resource
    private RuleDbDatasourceMapper dbDatasourceMapper;

    @Resource
    private RuleListLibraryMapper listLibraryMapper;

    @Resource
    private com.hengshucredit.rule.server.derived.DerivedVariableValidator derivedVariableValidator;

    public VariableSourceCatalog catalog(String scope, Long projectId) {
        String normalizedScope = normalizeScope(scope, projectId);
        Map<Long, RuleExternalDatasource> compatibleApiSources =
                new LinkedHashMap<>();
        for (RuleExternalDatasource source : safeList(
                externalDatasourceMapper.selectList(null))) {
            if (isEnabled(source.getStatus()) && isCompatible(
                    normalizedScope, projectId,
                    source.getScope(), source.getProjectId())) {
                compatibleApiSources.put(source.getId(), source);
            }
        }

        List<VariableSourceOption> apiOptions = new ArrayList<>();
        for (RuleExternalApiConfig api : safeList(
                apiConfigMapper.selectList(null))) {
            RuleExternalDatasource source = compatibleApiSources.get(
                    api.getDatasourceId());
            if (!isEnabled(api.getStatus()) || source == null) {
                continue;
            }
            apiOptions.add(new VariableSourceOption(
                    api.getId(), api.getApiCode(), api.getApiName(),
                    source.getScope(), source.getProjectId(),
                    source.getId(), source.getDatasourceName()));
        }

        List<VariableSourceOption> databaseOptions = new ArrayList<>();
        for (RuleDbDatasource datasource : safeList(
                dbDatasourceMapper.selectList(null))) {
            if (!isEnabled(datasource.getStatus()) || !isCompatible(
                    normalizedScope, projectId,
                    datasource.getScope(), datasource.getProjectId())) {
                continue;
            }
            databaseOptions.add(new VariableSourceOption(
                    datasource.getId(), datasource.getDatasourceCode(),
                    datasource.getDatasourceName(), datasource.getScope(),
                    datasource.getProjectId(), null, null));
        }

        List<VariableSourceOption> listOptions = new ArrayList<>();
        for (RuleListLibrary library : safeList(
                listLibraryMapper.selectList(null))) {
            if (!isEnabled(library.getStatus()) || !isCompatible(
                    normalizedScope, projectId,
                    library.getScope(), library.getProjectId())) {
                continue;
            }
            listOptions.add(new VariableSourceOption(
                    library.getId(), library.getListCode(),
                    library.getListName(), library.getScope(),
                    library.getProjectId(), null, null));
        }

        Comparator<VariableSourceOption> ordering = Comparator
                .comparing((VariableSourceOption option) ->
                        text(option.name()))
                .thenComparing(option -> text(option.code()))
                .thenComparing(VariableSourceOption::id);
        apiOptions.sort(ordering);
        databaseOptions.sort(ordering);
        listOptions.sort(ordering);
        return new VariableSourceCatalog(
                List.copyOf(apiOptions),
                List.copyOf(databaseOptions),
                List.copyOf(listOptions));
    }

    public List<GovernanceIssue> validate(RuleVariable variable) {
        List<GovernanceIssue> issues = new ArrayList<>();
        if (variable == null || !isExternalSource(
                variable.getVarSource())) {
            return issues;
        }
        JSONObject config;
        try {
            config = JSON.parseObject(variable.getSourceConfig());
        } catch (RuntimeException exception) {
            issues.add(issue(variable,
                    "VARIABLE_SOURCE_CONFIG_INVALID",
                    "字段取值配置不是有效 JSON", "$.sourceConfig"));
            return issues;
        }
        if (config == null) {
            issues.add(issue(variable,
                    "VARIABLE_SOURCE_CONFIG_REQUIRED",
                    "请完成字段取值来源配置", "$.sourceConfig"));
            return issues;
        }
        switch (variable.getVarSource()) {
            case "API" -> validateApi(variable, config, issues);
            case "DB" -> validateDatabase(variable, config, issues);
            case "LIST" -> validateLists(variable, config, issues);
            case "DERIVED" -> {
                try {
                    derivedVariableValidator.validate(variable, config);
                } catch (IllegalArgumentException exception) {
                    issues.add(issue(variable, "VARIABLE_SOURCE_DERIVED_INVALID", exception.getMessage(), "$.sourceConfig"));
                }
            }
            default -> {
            }
        }
        return issues;
    }

    public void validateOrThrow(RuleVariable variable) {
        List<GovernanceIssue> issues = validate(variable);
        if (!issues.isEmpty()) {
            throw new IllegalArgumentException(issues.get(0).message());
        }
    }

    private void validateApi(RuleVariable variable, JSONObject config,
                             List<GovernanceIssue> issues) {
        Long apiId = longValue(config.get("apiConfigId"));
        if (apiId == null) {
            issues.add(issue(variable,
                    "VARIABLE_SOURCE_REFERENCE_REQUIRED",
                    "请选择外数 API", "$.sourceConfig.apiConfigId"));
            return;
        }
        RuleExternalApiConfig api = apiConfigMapper.selectById(apiId);
        if (api == null) {
            issues.add(issue(variable,
                    "VARIABLE_SOURCE_NOT_FOUND",
                    "所选外数 API 不存在或已删除",
                    "$.sourceConfig.apiConfigId"));
            return;
        }
        if (!isEnabled(api.getStatus())) {
            issues.add(issue(variable,
                    "VARIABLE_SOURCE_DISABLED",
                    "所选外数 API 已停用",
                    "$.sourceConfig.apiConfigId"));
            return;
        }
        RuleExternalDatasource source = externalDatasourceMapper
                .selectById(api.getDatasourceId());
        validateResource(variable, source == null ? null : source.getScope(),
                source == null ? null : source.getProjectId(),
                source == null ? null : source.getStatus(),
                source == null, "外数 API 所属数据源",
                "$.sourceConfig.apiConfigId", issues);
    }

    private void validateDatabase(RuleVariable variable, JSONObject config,
                                  List<GovernanceIssue> issues) {
        try {
            DatabaseQueryOptions.from(config, 1);
        } catch (IllegalArgumentException exception) {
            issues.add(issue(variable, "VARIABLE_SOURCE_QUERY_LIMIT_INVALID", exception.getMessage(), "$.sourceConfig"));
        }
        Long datasourceId = longValue(config.get("dbDatasourceId"));
        if (datasourceId == null) {
            datasourceId = longValue(config.get("datasourceId"));
        }
        if (datasourceId == null) {
            issues.add(issue(variable,
                    "VARIABLE_SOURCE_REFERENCE_REQUIRED",
                    "请选择数据库连接",
                    "$.sourceConfig.dbDatasourceId"));
            return;
        }
        RuleDbDatasource datasource = dbDatasourceMapper.selectById(
                datasourceId);
        validateResource(variable,
                datasource == null ? null : datasource.getScope(),
                datasource == null ? null : datasource.getProjectId(),
                datasource == null ? null : datasource.getStatus(),
                datasource == null, "数据库连接",
                "$.sourceConfig.dbDatasourceId", issues);
        if (config.getString("sql") == null
                || config.getString("sql").isBlank()) {
            issues.add(issue(variable,
                    "VARIABLE_SOURCE_SQL_REQUIRED",
                    "请填写只读查询 SQL", "$.sourceConfig.sql"));
        }
    }

    private void validateLists(RuleVariable variable, JSONObject config,
                               List<GovernanceIssue> issues) {
        JSONArray listIds = config.getJSONArray("listIds");
        if (listIds == null || listIds.isEmpty()) {
            issues.add(issue(variable,
                    "VARIABLE_SOURCE_REFERENCE_REQUIRED",
                    "请至少选择一个名单库",
                    "$.sourceConfig.listIds"));
            return;
        }
        for (int index = 0; index < listIds.size(); index++) {
            Long listId = longValue(listIds.get(index));
            if (listId == null) {
                issues.add(issue(variable,
                        "VARIABLE_SOURCE_REFERENCE_INVALID",
                        "名单库引用必须是有效 ID",
                        "$.sourceConfig.listIds[" + index + "]"));
                continue;
            }
            RuleListLibrary library = listLibraryMapper.selectById(
                    listId);
            validateResource(variable,
                    library == null ? null : library.getScope(),
                    library == null ? null : library.getProjectId(),
                    library == null ? null : library.getStatus(),
                    library == null, "名单库",
                    "$.sourceConfig.listIds[" + index + "]", issues);
        }
    }

    private void validateResource(RuleVariable variable,
                                  String sourceScope,
                                  Long sourceProjectId,
                                  Integer sourceStatus,
                                  boolean missing,
                                  String label,
                                  String path,
                                  List<GovernanceIssue> issues) {
        if (missing) {
            issues.add(issue(variable,
                    "VARIABLE_SOURCE_NOT_FOUND",
                    "所选" + label + "不存在或已删除", path));
            return;
        }
        if (!isEnabled(sourceStatus)) {
            issues.add(issue(variable,
                    "VARIABLE_SOURCE_DISABLED",
                    "所选" + label + "已停用", path));
            return;
        }
        String variableScope;
        try {
            variableScope = normalizeScope(variable.getScope(),
                    variable.getProjectId());
        } catch (IllegalArgumentException exception) {
            issues.add(issue(variable,
                    "VARIABLE_SCOPE_INVALID", exception.getMessage(),
                    "$.projectId"));
            return;
        }
        if (!isCompatible(variableScope, variable.getProjectId(),
                sourceScope, sourceProjectId)) {
            issues.add(issue(variable,
                    "VARIABLE_SOURCE_SCOPE_MISMATCH",
                    "所选" + label + "不属于全局或当前项目", path));
        }
    }

    private String normalizeScope(String scope, Long projectId) {
        String normalized = scope == null ? "" : scope.trim().toUpperCase();
        if (normalized.isEmpty()) {
            normalized = projectId == null || projectId <= 0
                    ? "GLOBAL" : "PROJECT";
        }
        if (!"GLOBAL".equals(normalized)
                && !"PROJECT".equals(normalized)) {
            throw new IllegalArgumentException("字段作用范围无效");
        }
        if ("PROJECT".equals(normalized)
                && (projectId == null || projectId <= 0)) {
            throw new IllegalArgumentException("项目级字段必须选择项目");
        }
        return normalized;
    }

    private boolean isCompatible(String targetScope, Long targetProjectId,
                                 String sourceScope, Long sourceProjectId) {
        boolean globalSource = "GLOBAL".equalsIgnoreCase(sourceScope)
                || sourceProjectId == null || sourceProjectId <= 0;
        if ("GLOBAL".equals(targetScope)) {
            return globalSource;
        }
        return globalSource || targetProjectId.equals(sourceProjectId);
    }

    private boolean isExternalSource(String source) {
        return "API".equals(source) || "DB".equals(source)
                || "LIST".equals(source) || "DERIVED".equals(source);
    }

    private boolean isEnabled(Integer status) {
        return Integer.valueOf(1).equals(status);
    }

    private GovernanceIssue issue(RuleVariable variable,
                                  String code, String message,
                                  String path) {
        return GovernanceIssue.error(code, message,
                GovernanceResourceTypes.VARIABLE,
                variable == null ? null : variable.getId(), path);
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String text(String value) {
        return value == null ? "" : value;
    }
}
