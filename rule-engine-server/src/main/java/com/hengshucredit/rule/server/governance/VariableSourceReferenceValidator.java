package com.hengshucredit.rule.server.governance;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.RuleDbDatasource;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.model.entity.RuleListLibrary;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.model.entity.RuleSftpDatasource;
import com.hengshucredit.rule.server.mapper.RuleDbDatasourceMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalApiConfigMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalDatasourceMapper;
import com.hengshucredit.rule.server.mapper.RuleListLibraryMapper;
import com.hengshucredit.rule.server.mapper.RuleSftpDatasourceMapper;
import com.hengshucredit.rule.server.service.DatabaseQueryOptions;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class VariableSourceReferenceValidator {
    private static final Set<String> LIST_COMBINATION_MODES = Set.of(
            "ANY_FIELD_ANY_LIST", "ALL_FIELDS_ANY_LIST",
            "ANY_FIELD_ALL_LISTS", "ALL_FIELDS_ALL_LISTS");
    private static final Set<String> LIST_MATCH_MODES = Set.of(
            "IN_LIST", "NOT_IN_LIST", "CONTAINED_IN_LIST", "NOT_CONTAINED_IN_LIST");
    private static final Set<String> LIST_ITEM_TYPES = Set.of(
            "MOBILE", "ID_CARD", "ADDRESS", "IP", "DEVICE",
            "NAME", "GPS", "EMAIL", "BANK_CARD", "OTHER");
    @Resource
    private com.hengshucredit.rule.server.security.ConsoleAccessGuard accessGuard;

    @Resource
    private RuleExternalApiConfigMapper apiConfigMapper;

    @Resource
    private RuleExternalDatasourceMapper externalDatasourceMapper;

    @Resource
    private RuleDbDatasourceMapper dbDatasourceMapper;

    @Resource
    private RuleListLibraryMapper listLibraryMapper;

    @Resource
    private RuleSftpDatasourceMapper sftpDatasourceMapper;

    @Resource
    private com.hengshucredit.rule.server.derived.DerivedVariableValidator derivedVariableValidator;
    @Resource private com.hengshucredit.rule.server.service.RuleVariableService variableService;

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
        LambdaQueryWrapper<RuleListLibrary> listQuery = new LambdaQueryWrapper<RuleListLibrary>()
                .eq(RuleListLibrary::getStatus, 1)
                .and(scopeQuery -> scopeQuery.eq(RuleListLibrary::getScope, "GLOBAL")
                        .or(projectId != null && projectId > 0,
                                project -> project.eq(RuleListLibrary::getScope, "PROJECT")
                                        .eq(RuleListLibrary::getProjectId, projectId)));
        for (RuleListLibrary library : safeList(
                listLibraryMapper.selectList(listQuery))) {
            if (!isEnabled(library.getStatus()) || !isCompatible(
                    normalizedScope, projectId,
                    library.getScope(), library.getProjectId())) {
                continue;
            }
            listOptions.add(new VariableSourceOption(
                    library.getId(), library.getListCode(),
                    library.getListName(), library.getScope(),
                    library.getProjectId(), null, null, library.getListType()));
        }

        List<VariableSourceOption> sftpOptions = new ArrayList<>();
        for (RuleSftpDatasource datasource : safeList(sftpDatasourceMapper == null ? null : sftpDatasourceMapper.selectList(null))) {
            if (!isEnabled(datasource.getStatus()) || !isCompatible(normalizedScope, projectId,
                    datasource.getScope(), datasource.getProjectId())) continue;
            sftpOptions.add(new VariableSourceOption(datasource.getId(), datasource.getDatasourceCode(),
                    datasource.getDatasourceName(), datasource.getScope(), datasource.getProjectId(), null, null));
        }

        Comparator<VariableSourceOption> ordering = Comparator
                .comparing((VariableSourceOption option) ->
                        text(option.name()))
                .thenComparing(option -> text(option.code()))
                .thenComparing(VariableSourceOption::id);
        apiOptions.sort(ordering);
        databaseOptions.sort(ordering);
        listOptions.sort(ordering);
        sftpOptions.sort(ordering);
        return new VariableSourceCatalog(
                accessGuard == null || accessGuard.can("datasource:view") ? List.copyOf(apiOptions) : List.of(),
                accessGuard == null || accessGuard.can("database:view") ? List.copyOf(databaseOptions) : List.of(),
                accessGuard == null || accessGuard.can("list:view") ? List.copyOf(listOptions) : List.of(),
                accessGuard == null || accessGuard.can("database:view") ? List.copyOf(sftpOptions) : List.of());
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
            case "SFTP" -> validateSftp(variable, config, issues);
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
        if (config.getJSONObject("requestOverrides") != null) {
            try {
                var definition = com.hengshucredit.rule.server.service.ExternalApiRequestPlan.specification(api);
                var fields = com.hengshucredit.rule.server.service.ExternalApiRequestPlan.fields(definition);
                if (definition != null && definition.getJSONArray("requestBranches") != null) {
                    for (Object branch : definition.getJSONArray("requestBranches")) fields.addAll(com.hengshucredit.rule.server.service.ExternalApiRequestPlan.fields(JSON.parseObject(JSON.toJSONString(branch))));
                }
                for (String key : config.getJSONObject("requestOverrides").keySet()) {
                    if (fields.stream().noneMatch(field -> key.equals(field.getString("id")) && field.getBooleanValue("overridable"))) throw new IllegalArgumentException("API 参数不允许覆盖或已被删除: " + key);
                }
                if (variableService != null) com.hengshucredit.rule.server.service.ExternalApiExecutionConfig.bindReferences(config.get("requestOverrides"), variableService.buildRefScriptNameMap(variable.getProjectId()));
            } catch (IllegalArgumentException exception) {
                issues.add(issue(variable, "API_PARAMETER_OVERRIDE_INVALID", exception.getMessage(), "$.sourceConfig.requestOverrides"));
            }
        }
        if (config.getJSONObject("inputOverrides") != null) {
            try {
                com.hengshucredit.rule.server.service.ExternalApiRequestPlan.validateInputOverrides(api, config.getJSONObject("inputOverrides"));
                if (variableService != null) com.hengshucredit.rule.server.service.ExternalApiExecutionConfig.bindReferences(
                        config.get("inputOverrides"), variableService.buildRefScriptNameMap(variable.getProjectId()));
            } catch (IllegalArgumentException exception) {
                issues.add(issue(variable, "API_INPUT_OVERRIDE_INVALID", exception.getMessage(), "$.sourceConfig.inputOverrides"));
            }
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
            DatabaseQueryOptions.from(config, 1000);
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
        String combinationMode = text(config.get("combinationMode")).toUpperCase(java.util.Locale.ROOT);
        if (!LIST_COMBINATION_MODES.contains(combinationMode)) {
            issues.add(issue(variable, "VARIABLE_SOURCE_LIST_COMBINATION_INVALID",
                    "名单组合模式无效", "$.sourceConfig.combinationMode"));
        }
        String matchMode = text(config.get("matchMode")).toUpperCase(java.util.Locale.ROOT);
        if (!LIST_MATCH_MODES.contains(matchMode)) {
            issues.add(issue(variable, "VARIABLE_SOURCE_LIST_MATCH_INVALID",
                    "名单匹配模式无效", "$.sourceConfig.matchMode"));
        }
        String returnMode = text(config.get("returnMode"));
        if (!Set.of("BOOLEAN", "NUMBER").contains(returnMode)) {
            issues.add(issue(variable, "VARIABLE_SOURCE_LIST_RETURN_INVALID",
                    "名单变量返回模式只能是 BOOLEAN 或 NUMBER", "$.sourceConfig.returnMode"));
        }
        JSONArray itemTypes = config.getJSONArray("itemTypes");
        if (itemTypes != null) {
            for (int index = 0; index < itemTypes.size(); index++) {
                String type = text(itemTypes.get(index)).toUpperCase(java.util.Locale.ROOT);
                if (!LIST_ITEM_TYPES.contains(type)) {
                    issues.add(issue(variable, "VARIABLE_SOURCE_LIST_ITEM_TYPE_INVALID",
                            "名单内容类型无效", "$.sourceConfig.itemTypes[" + index + "]"));
                }
            }
        }
        JSONArray operands = config.getJSONArray("queryOperands");
        if (operands == null || operands.isEmpty()) {
            issues.add(issue(variable, "VARIABLE_SOURCE_LIST_QUERY_REQUIRED",
                    "名单变量至少需要一个待匹配内容", "$.sourceConfig.queryOperands"));
        } else {
            for (int index = 0; index < operands.size(); index++) {
                Object raw = operands.get(index);
                if (!(raw instanceof JSONObject operand)) {
                    issues.add(issue(variable, "VARIABLE_SOURCE_LIST_QUERY_INVALID",
                            "待匹配内容必须是表达式对象", "$.sourceConfig.queryOperands[" + index + "]"));
                    continue;
                }
                validateListOperand(variable, operand, "$.sourceConfig.queryOperands[" + index + "]", issues);
            }
        }
    }

    private void validateListOperand(RuleVariable variable, JSONObject operand,
                                     String path, List<GovernanceIssue> issues) {
        String kind = text(operand.get("kind"));
        if (!Set.of("PATH", "REFERENCE", "LITERAL", "FUNCTION", "OPERATION",
                "ACCESS", "CAST", "ARRAY").contains(kind)) {
            issues.add(issue(variable, "VARIABLE_SOURCE_LIST_QUERY_KIND_INVALID",
                    "名单待匹配表达式类型不支持", path + ".kind"));
            return;
        }
        if ("REFERENCE".equals(kind)
                && (longValue(operand.get("refId")) == null || text(operand.get("refType")).isBlank())) {
            issues.add(issue(variable, "VARIABLE_SOURCE_LIST_QUERY_REFERENCE_INVALID",
                    "名单待匹配引用缺少 refType 或 refId", path));
        }
        if (("PATH".equals(kind) || "REFERENCE".equals(kind))
                && text(operand.get("code")).isBlank() && text(operand.get("value")).isBlank()) {
            issues.add(issue(variable, "VARIABLE_SOURCE_LIST_QUERY_PATH_REQUIRED",
                    "名单待匹配字段路径不能为空", path));
        }
        if ("FUNCTION".equals(kind)) validateListOperandArray(variable, operand.getJSONArray("args"), path + ".args", issues);
        if ("OPERATION".equals(kind)) validateListOperandArray(variable, operand.getJSONArray("terms"), path + ".terms", issues);
        if ("ARRAY".equals(kind)) validateListOperandArray(variable, operand.getJSONArray("items"), path + ".items", issues);
        if ("ACCESS".equals(kind)) {
            Object target = operand.get("target");
            Object accessor = operand.get("accessor");
            if (target instanceof JSONObject child) validateListOperand(variable, child, path + ".target", issues);
            else issues.add(issue(variable, "VARIABLE_SOURCE_LIST_QUERY_INVALID", "访问目标不能为空", path + ".target"));
            if (accessor instanceof JSONObject child) validateListOperand(variable, child, path + ".accessor", issues);
            else issues.add(issue(variable, "VARIABLE_SOURCE_LIST_QUERY_INVALID", "访问索引不能为空", path + ".accessor"));
        }
        if ("CAST".equals(kind) && operand.getJSONObject("operand") != null) {
            validateListOperand(variable, operand.getJSONObject("operand"), path + ".operand", issues);
        }
    }

    private void validateListOperandArray(RuleVariable variable, JSONArray values,
                                          String path, List<GovernanceIssue> issues) {
        if (values == null || values.isEmpty()) {
            issues.add(issue(variable, "VARIABLE_SOURCE_LIST_QUERY_INVALID",
                    "名单表达式子项不能为空", path));
            return;
        }
        for (int index = 0; index < values.size(); index++) {
            Object raw = values.get(index);
            if (raw instanceof JSONObject child) validateListOperand(variable, child, path + "[" + index + "]", issues);
            else issues.add(issue(variable, "VARIABLE_SOURCE_LIST_QUERY_INVALID",
                    "名单表达式子项必须是对象", path + "[" + index + "]"));
        }
    }

    private void validateSftp(RuleVariable variable, JSONObject config,
                              List<GovernanceIssue> issues) {
        Long datasourceId = longValue(config.get("sftpDatasourceId"));
        if (datasourceId == null) {
            issues.add(issue(variable, "VARIABLE_SOURCE_REFERENCE_REQUIRED", "请选择 SFTP 数据源",
                    "$.sourceConfig.sftpDatasourceId"));
            return;
        }
        RuleSftpDatasource datasource = sftpDatasourceMapper.selectById(datasourceId);
        validateResource(variable, datasource == null ? null : datasource.getScope(),
                datasource == null ? null : datasource.getProjectId(), datasource == null ? null : datasource.getStatus(),
                datasource == null, "SFTP数据源", "$.sourceConfig.sftpDatasourceId", issues);
        if (config.getString("remotePath") == null || config.getString("remotePath").isBlank()) {
            issues.add(issue(variable, "VARIABLE_SOURCE_PATH_REQUIRED", "请填写远端文件路径",
                    "$.sourceConfig.remotePath"));
        }
        boolean hasFileAddress = config.getString("fileAddress") != null
                && !config.getString("fileAddress").isBlank();
        String uploadPath = config.getString("uploadRemotePath");
        if (hasFileAddress && (uploadPath == null || uploadPath.isBlank())) {
            issues.add(issue(variable, "VARIABLE_SOURCE_UPLOAD_PATH_REQUIRED",
                    "配置业务文件地址时必须填写 SFTP 上传目标路径；未填写时请清空业务文件地址",
                    "$.sourceConfig.uploadRemotePath"));
        }
        validateIntegerRange(variable, config.get("uploadTimeoutMs"), 100, 300000,
                "业务文件上传超时必须在 100 到 300000 毫秒之间", "$.sourceConfig.uploadTimeoutMs", issues);
        String format = config.getString("outputFormat");
        if (format != null && !List.of("TEXT", "BASE64", "JSON").contains(format.toUpperCase())) {
            issues.add(issue(variable, "VARIABLE_SOURCE_FORMAT_INVALID", "SFTP输出格式仅支持 TEXT、BASE64 或 JSON",
                    "$.sourceConfig.outputFormat"));
        }
        validateIntegerRange(variable, config.get("retryCount"), 0, 5,
                "SFTP重试次数必须在 0 到 5 之间", "$.sourceConfig.retryCount", issues);
        validateIntegerRange(variable, config.get("retryBackoffMs"), 0, 60000,
                "SFTP重试退避必须在 0 到 60000 毫秒之间", "$.sourceConfig.retryBackoffMs", issues);
        validateIntegerRange(variable, config.get("cacheSeconds"), 0, 86400,
                "SFTP缓存时长必须在 0 到 86400 秒之间，0 表示不缓存", "$.sourceConfig.cacheSeconds", issues);
        Object multiplier = config.get("retryBackoffMultiplier");
        if (multiplier != null) {
            try {
                double value = Double.parseDouble(String.valueOf(multiplier));
                if (!Double.isFinite(value) || value < 1D || value > 5D) {
                    issues.add(issue(variable, "VARIABLE_SOURCE_RETRY_INVALID",
                            "SFTP退避倍数必须在 1 到 5 之间", "$.sourceConfig.retryBackoffMultiplier"));
                }
            } catch (NumberFormatException e) {
                issues.add(issue(variable, "VARIABLE_SOURCE_RETRY_INVALID",
                        "SFTP退避倍数必须是数字", "$.sourceConfig.retryBackoffMultiplier"));
            }
        }
    }

    private void validateIntegerRange(RuleVariable variable, Object raw, int min, int max,
                                     String message, String path, List<GovernanceIssue> issues) {
        if (raw == null) return;
        try {
            int value = Integer.parseInt(String.valueOf(raw));
            if (value < min || value > max) issues.add(issue(variable, "VARIABLE_SOURCE_RETRY_INVALID", message, path));
        } catch (NumberFormatException e) {
            issues.add(issue(variable, "VARIABLE_SOURCE_RETRY_INVALID", message, path));
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
                || "LIST".equals(source) || "SFTP".equals(source) || "DERIVED".equals(source);
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

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
