package com.hengshucredit.rule.server.testdata;

/** 仅使用固定的资源表与归属字段构建用例目录查询。 */
public enum TestResource {
    RULE("rule_definition","rule_name","rule_code","rule_api_doc_scenario","definition_id","RULE"),
    VARIABLE("rule_variable","var_label","var_code","rule_variable_test_case","variable_id","VARIABLE"),
    MODEL("rule_model","model_name","model_code","rule_model_test_case","model_id","MODEL"),
    FUNCTION("rule_function","func_name","func_code",null,null,"FUNCTION"),
    API("rule_external_api_config","api_name","api_code","rule_api_test_case","api_id","EXTERNAL_API");
    final String table, label, code, cases, owner, accessType;
    TestResource(String table, String label, String code, String cases, String owner, String accessType) {
        this.table = table; this.label = label; this.code = code; this.cases = cases;
        this.owner = owner; this.accessType = accessType;
    }
}
