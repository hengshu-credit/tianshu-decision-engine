package com.hengshucredit.rule.model.dto;

import lombok.Data;

/**
 * API接口文档导出DTO
 */
@Data
public class ApiDocDTO {
    /** 项目基本信息 */
    private ProjectInfo project;
    /** 规则列表 */
    private java.util.List<RuleInfo> rules;
    /** 项目已启用鉴权方式的安全摘要，不包含任何凭据 */
    private java.util.List<AuthenticationInfo> authentications;
    /** 变量列表 */
    private java.util.List<VariableInfo> variables;
    /** 数据对象列表 */
    private java.util.List<DataObjectInfo> dataObjects;
    /** 自定义函数列表 */
    private java.util.List<FunctionInfo> functions;

    @Data
    public static class ProjectInfo {
        private Long id;
        private String projectCode;
        private String projectName;
        private String description;
        private Integer status;
    }

    @Data
    public static class RuleInfo {
        private Long id;
        private String ruleCode;
        private String ruleName;
        private String modelType;
        private String modelTypeLabel;
        private String description;
        private Integer currentVersion;
        private Integer publishedVersion;
        /** 实际对外生效制品的修订和摘要；不包含任何密钥或完整契约秘密。 */
        private Long publishedRevisionId;
        private String publishedArtifactDigest;
        private Boolean openApiEnabled;
        /** 已发布开放接口契约投影；不包含响应 Header 的具体值。 */
        private java.util.Map<String, Object> openApiContract;
        /** 字段契约可信状态：VERIFIED/UNVERIFIED。 */
        private String schemaTrust;
        /** 字段快照校验诊断；不返回工作稿内容。 */
        private java.util.List<String> schemaDiagnostics;
        /** 由已发布字段快照生成的请求/响应 JSON Schema。 */
        private java.util.Map<String, Object> inputSchema;
        private java.util.Map<String, Object> outputSchema;
        private Integer status;
        private String statusLabel;
        /** 该规则的输入变量（完整定义） */
        private java.util.List<VariableInfo> inputVariables;
        /** 该规则的输出变量（完整定义） */
        private java.util.List<VariableInfo> outputVariables;
        /** 该规则的输入数据对象 */
        private java.util.List<DataObjectInfo> inputDataObjects;
        /** 该规则的输出数据对象 */
        private java.util.List<DataObjectInfo> outputDataObjects;
        /** 用户明确选择且与已发布版本匹配的 API 测试场景 */
        private java.util.List<ScenarioInfo> scenarios;
    }

    @Data
    public static class AuthenticationInfo {
        private String authName;
        private String authType;
        private String placement;
        private String parameterName;
        private Integer tokenTtlSeconds;
        private Integer tokenGraceSeconds;
    }

    @Data
    public static class ScenarioInfo {
        private Long id;
        private String scenarioName;
        private String description;
        private String requestJson;
        private String responseJson;
        private Integer outerCode;
        private String businessCodePath;
        private String businessCode;
        private Integer sortOrder;
        private Long revisionId;
        private String artifactDigest;
    }

    @Data
    public static class VariableInfo {
        private Long id;
        private String varCode;
        private String varLabel;
        private String varType;
        private String varTypeLabel;
        private String varSource;
        private String varSourceLabel;
        private String defaultValue;
        private String valueRange;
        private String exampleValue;
        private String description;
        private String scriptName;
        /** 输入字段没有默认值时为必填；输出字段始终为必填。 */
        private Boolean required;
    }

    @Data
    public static class DataObjectInfo {
        private Long id;
        private String objectCode;
        private String objectLabel;
        private String objectType;
        private String objectTypeLabel;
        private String sourceType;
        private String sourceTypeLabel;
        private String scriptName;
        private java.util.List<FieldInfo> fields;
    }

    @Data
    public static class FieldInfo {
        private Long id;
        private String varCode;
        private String varLabel;
        private String varType;
        private String varTypeLabel;
        private String scriptName;
        private String refObjectCode;
        private String parentVarCode;
    }

    @Data
    public static class FunctionInfo {
        private Long id;
        private String funcCode;
        private String funcName;
        private String description;
        private String paramsJson;
        private String returnType;
        private String implType;
        private String implTypeLabel;
    }
}
