package com.hengshucredit.rule.server.openapi;

/** 只供开放接口读取的发布配置投影，不进入 SDK 同步实体。 */
public class PublishedOpenApiContract {
    private Long definitionId;
    private String ruleCode;
    private String projectCode;
    private Integer version;
    private Integer status;
    private String openApiConfigJson;

    public Long getDefinitionId() {
        return definitionId;
    }

    public void setDefinitionId(Long definitionId) {
        this.definitionId = definitionId;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public void setProjectCode(String projectCode) {
        this.projectCode = projectCode;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getOpenApiConfigJson() {
        return openApiConfigJson;
    }

    public void setOpenApiConfigJson(String openApiConfigJson) {
        this.openApiConfigJson = openApiConfigJson;
    }
}
