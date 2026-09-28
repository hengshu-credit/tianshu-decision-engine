package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.model.dto.ResourcePreflightReport;
import com.hengshucredit.rule.model.dto.RuleValidationIssue;
import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.model.entity.RuleDbDatasource;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.mapper.RuleDataObjectMapper;
import com.hengshucredit.rule.server.mapper.RuleDbDatasourceMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalApiConfigMapper;
import com.hengshucredit.rule.server.mapper.RuleExternalDatasourceMapper;
import com.hengshucredit.rule.server.mapper.RuleModelMapper;
import com.hengshucredit.rule.server.mapper.RuleVariableMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

/** 资源页和发布前工作台共用的基础可执行性检查。 */
@Service
public class ResourcePreflightService {
    @Resource private RuleExternalApiConfigMapper apiConfigMapper;
    @Resource private RuleExternalDatasourceMapper externalDatasourceMapper;
    @Resource private RuleDataObjectMapper dataObjectMapper;
    @Resource private RuleDbDatasourceMapper dbDatasourceMapper;
    @Resource private RuleVariableMapper variableMapper;
    @Resource private RuleModelMapper modelMapper;

    public ResourcePreflightReport externalApi(Long id) {
        ResourcePreflightReport report = report("EXTERNAL_API", id);
        RuleExternalApiConfig config = apiConfigMapper.selectById(id);
        if (config == null) { error(report, "RESOURCE_NOT_FOUND", "$", "外数 API 不存在", "刷新资源后重试"); finish(report); return report; }
        RuleExternalDatasource datasource = config.getDatasourceId() == null
                ? null : externalDatasourceMapper.selectById(config.getDatasourceId());
        RuleDataObject requestObject = config.getRequestObjectId() == null
                ? null : dataObjectMapper.selectById(config.getRequestObjectId());
        RuleDataObject responseObject = config.getResponseObjectId() == null
                ? null : dataObjectMapper.selectById(config.getResponseObjectId());
        try {
            ExternalApiConfigValidator.validate(config);
        } catch (RuntimeException error) {
            error(report, "CONFIG_INVALID", "$", error.getMessage(), "修复接口配置后重新检查");
        }
        try {
            ExternalApiConfigValidator.validateReferences(config, datasource, requestObject, responseObject);
        } catch (RuntimeException error) {
            error(report, "REFERENCE_INVALID", "datasourceId", error.getMessage(), "修复数据源或数据对象引用后重新检查");
        }
        if (blank(config.getApiCode())) error(report, "API_CODE_REQUIRED", "apiCode", "接口编码不能为空", "填写接口编码");
        if (blank(config.getEndpointUrl())) error(report, "ENDPOINT_REQUIRED", "endpointUrl", "接口地址不能为空", "填写接口地址");
        if (datasource != null && !active(datasource.getStatus())) {
            error(report, "DATASOURCE_DISABLED", "datasourceId", "所属数据源未启用", "启用数据源后重新检查");
        }
        if (requestObject == null && config.getRequestObjectId() != null) {
            error(report, "REQUEST_OBJECT_NOT_FOUND", "requestObjectId", "请求数据对象不存在", "重新选择请求数据对象");
        }
        if (responseObject == null && config.getResponseObjectId() != null) {
            error(report, "RESPONSE_OBJECT_NOT_FOUND", "responseObjectId", "响应数据对象不存在", "重新选择响应数据对象");
        }
        warn(report, "REAL_CALL_REQUIRED", "test", "该检查不代表已访问供应商或完成连接测试", "执行无网络预览或测试调用");
        finish(report);
        return report;
    }

    public ResourcePreflightReport database(Long id) {
        ResourcePreflightReport report = report("DATABASE", id);
        RuleDbDatasource datasource = dbDatasourceMapper.selectById(id);
        if (datasource == null) { error(report, "RESOURCE_NOT_FOUND", "$", "数据库数据源不存在", "刷新资源后重试"); finish(report); return report; }
        if (!active(datasource.getStatus())) error(report, "DATASOURCE_DISABLED", "status", "数据库数据源未启用", "启用数据源后重新检查");
        String mode = upper(datasource.getConnectionMode(), "DIRECT");
        if ("JDBC_URL".equals(mode) && blank(datasource.getJdbcUrl())) {
            error(report, "JDBC_URL_REQUIRED", "jdbcUrl", "JDBC 模式必须填写连接地址", "填写 JDBC URL");
        }
        if (!"JDBC_URL".equals(mode)
                && (blank(datasource.getHost()) || datasource.getPort() == null || blank(datasource.getDatabaseName()))) {
            error(report, "CONNECTION_FIELDS_REQUIRED", "connection", "直连模式缺少主机、端口或数据库名", "补齐连接参数");
        }
        warn(report, "CONNECTION_TEST_REQUIRED", "connectionTest", "尚未在该报告中执行真实数据库连接测试", "执行只读连接测试");
        finish(report);
        return report;
    }

    public ResourcePreflightReport variable(Long id) {
        ResourcePreflightReport report = report("VARIABLE", id);
        RuleVariable variable = variableMapper.selectById(id);
        if (variable == null) { error(report, "RESOURCE_NOT_FOUND", "$", "变量不存在", "刷新资源后重试"); finish(report); return report; }
        if (!active(variable.getStatus())) error(report, "VARIABLE_DISABLED", "status", "变量未启用", "启用变量或改用其他引用");
        String source = upper(variable.getVarSource(), "");
        JSONObject config = parseConfig(report, variable.getSourceConfig());
        if (config == null) {
            finish(report);
            return report;
        }
        if ("API".equals(source)) {
            Long apiId = longValue(config.get("apiConfigId"));
            RuleExternalApiConfig api = apiId == null ? null : apiConfigMapper.selectById(apiId);
            if (api == null) error(report, "API_REFERENCE_NOT_FOUND", "sourceConfig.apiConfigId", "API 变量引用的接口不存在", "选择有效的外数接口");
            else if (!active(api.getStatus())) error(report, "API_REFERENCE_DISABLED", "sourceConfig.apiConfigId", "API 变量引用的接口未启用", "启用外数接口");
            if (blank(config.getString("resultPath"))) {
                warn(report, "API_RESULT_PATH_MISSING", "sourceConfig.resultPath", "API 变量未配置结果路径，将依赖默认取值规则", "补充明确的结果路径");
            }
        } else if ("DB".equals(source)) {
            Long dbId = longValue(config.get("dbDatasourceId"));
            if (dbId == null) dbId = longValue(config.get("datasourceId"));
            RuleDbDatasource db = dbId == null ? null : dbDatasourceMapper.selectById(dbId);
            if (db == null) error(report, "DB_REFERENCE_NOT_FOUND", "sourceConfig.datasourceId", "数据库变量引用的数据源不存在", "选择有效的数据库数据源");
            else if (!active(db.getStatus())) error(report, "DB_REFERENCE_DISABLED", "sourceConfig.datasourceId", "数据库变量引用的数据源未启用", "启用数据库数据源");
            String sql = config.getString("dbSql");
            if (blank(sql)) sql = config.getString("sql");
            if (blank(sql)) error(report, "DB_SQL_REQUIRED", "sourceConfig.dbSql", "数据库变量未配置查询 SQL", "填写只读查询 SQL");
            else if (!SqlQuerySupport.isReadOnlySelect(sql)) error(report, "DB_SQL_NOT_READ_ONLY", "sourceConfig.dbSql", "变量查询 SQL 必须是只读查询", "只保留 SELECT 或 WITH 查询");
        } else if (!SUPPORTED_SOURCES.contains(source)) {
            error(report, "SOURCE_UNSUPPORTED", "varSource", "变量来源类型不受支持: " + variable.getVarSource(), "选择受支持的变量来源");
        }
        finish(report);
        return report;
    }

    public ResourcePreflightReport model(Long id) {
        ResourcePreflightReport report = report("MODEL", id);
        RuleModel model = modelMapper.selectById(id);
        if (model == null) { error(report, "RESOURCE_NOT_FOUND", "$", "模型不存在", "刷新资源后重试"); finish(report); return report; }
        if (!active(model.getStatus())) error(report, "MODEL_DISABLED", "status", "模型未启用", "启用模型或改用其他模型");
        if (blank(model.getModelFormat())) error(report, "MODEL_FORMAT_REQUIRED", "modelFormat", "模型格式不能为空", "选择模型格式");
        if (blank(model.getModelContent()) && blank(model.getModelFileName())) {
            error(report, "MODEL_CONTENT_REQUIRED", "modelContent", "模型制品内容不能为空", "上传模型制品");
        }
        if (blank(model.getInputSchemaJson()) || blank(model.getOutputSchemaJson())) {
            warn(report, "MODEL_SCHEMA_MISSING", "schema", "模型输入或输出 Schema 尚未冻结", "补充并验证模型输入输出 Schema");
        }
        validateJson(report, "inputSchemaJson", model.getInputSchemaJson(), "模型输入 Schema 不是合法 JSON");
        validateJson(report, "outputSchemaJson", model.getOutputSchemaJson(), "模型输出 Schema 不是合法 JSON");
        validateJson(report, "modelConfig", model.getModelConfig(), "模型配置不是合法 JSON");
        finish(report);
        return report;
    }

    private static final Set<String> SUPPORTED_SOURCES = Set.of("INPUT", "API", "DB", "LIST", "DERIVED", "COMPUTED", "CONSTANT");

    private ResourcePreflightReport report(String type, Long id) {
        ResourcePreflightReport report = new ResourcePreflightReport();
        report.setResourceType(type);
        report.setResourceId(id);
        report.setCheckedAt(LocalDateTime.now());
        report.setCheckScope("SAVED_CONFIGURATION");
        return report;
    }
    private void finish(ResourcePreflightReport report) { report.setValid(report.getErrors().isEmpty()); }
    private RuleValidationIssue error(ResourcePreflightReport report, String code, String path, String message, String nextAction) {
        RuleValidationIssue issue = new RuleValidationIssue("ERROR", code, path, report.getResourceType(), report.getResourceId(), message)
                .withTitle("资源配置校验").withNextAction(nextAction);
        report.getErrors().add(issue); return issue;
    }
    private void warn(ResourcePreflightReport report, String code, String path, String message, String nextAction) {
        report.getWarnings().add(new RuleValidationIssue("WARNING", code, path, report.getResourceType(), report.getResourceId(), message)
                .withTitle("资源配置提示").withNextAction(nextAction));
    }
    private JSONObject parseConfig(ResourcePreflightReport report, String value) {
        if (blank(value)) return new JSONObject();
        try { return JSON.parseObject(value); }
        catch (RuntimeException error) { this.error(report, "SOURCE_CONFIG_INVALID", "sourceConfig", "来源配置不是合法 JSON", "修复来源配置 JSON"); return null; }
    }
    private void validateJson(ResourcePreflightReport report, String path, String value, String message) {
        if (blank(value)) return;
        try { JSON.parse(value); }
        catch (RuntimeException error) { this.error(report, "JSON_INVALID", path, message, "修复 JSON 后重新检查"); }
    }
    private boolean active(Integer status) { return status == null || status == 1; }
    private String upper(String value, String fallback) { return (value == null ? fallback : value).trim().toUpperCase(Locale.ROOT); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private Long longValue(Object value) { try { return value == null ? null : Long.valueOf(String.valueOf(value)); } catch (RuntimeException e) { return null; } }
}
