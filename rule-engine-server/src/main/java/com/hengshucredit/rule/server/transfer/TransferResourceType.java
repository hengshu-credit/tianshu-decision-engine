package com.hengshucredit.rule.server.transfer;

/** 离线包白名单；运行日志、名单数据及环境鉴权凭据不能作为迁移资源。 */
public enum TransferResourceType {
    PROJECT("rule_project", "projectCode", "projectName", "project"),
    RULE("rule_definition", "ruleCode", "ruleName", "rule"),
    VARIABLE("rule_variable", "varCode", "varLabel", "field"),
    DATA_OBJECT("rule_data_object", "objectCode", "objectLabel", "field"),
    FUNCTION("rule_function", "funcCode", "funcName", "function"),
    DATABASE("rule_db_datasource", "datasourceCode", "datasourceName", "database"),
    EXTERNAL_DATASOURCE("rule_external_datasource", "datasourceCode", "datasourceName", "datasource"),
    EXTERNAL_API("rule_external_api_config", "apiCode", "apiName", "datasource"),
    LIST_LIBRARY("rule_list_library", "listCode", "listName", "field"),
    LIST_RECORD_BATCH("rule_list_record_batch", "batchCode", "batchCode", "field"),
    MODEL("rule_model", "modelCode", "modelName", "model"),
    EXPERIMENT("rule_experiment", "experimentCode", "experimentName", "experiment"),
    FIELD_VALIDATION("rule_field_validation", "validationCode", "validationName", "field"),
    RULE_VERSION("rule_version_binding", "versionNo", "versionNo", "rule");

    public final String table;
    public final String codeField;
    public final String nameField;
    public final String permissionModule;

    TransferResourceType(String table, String codeField, String nameField, String permissionModule) {
        this.table = table;
        this.codeField = codeField;
        this.nameField = nameField;
        this.permissionModule = permissionModule;
    }
}
