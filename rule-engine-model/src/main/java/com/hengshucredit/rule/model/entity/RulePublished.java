package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("rule_published")
public class RulePublished {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String ruleCode;
    private Long definitionId;
    private Long revisionId;
    private Long artifactId;
    private String artifactDigest;
    /** 规则所属项目编码 */
    private String projectCode;
    /** 规则所属项目 ID，仅用于同步给全局客户端生成项目级 trace。 */
    @TableField(exist = false)
    private Long projectId;
    private Integer version;
    @TableField(exist = false)
    private Long versionBindingId;
    @TableField(exist = false)
    private Long bindingGeneration;
    @TableField(exist = false)
    private boolean imported;
    @TableField(exist = false)
    private java.util.Map<String, Long> importBindings;
    private String modelType;
    private String compiledScript;
    private String compiledType;
    private String modelJson;
    /** 已发布的规则契约快照，包含开放接口和幂等配置。 */
    private String openApiConfigJson;
    /** 仅用于同步给客户端，不映射数据库字段 */
    @TableField(exist = false)
    private List<String> outputScriptNames;
    /** 客户端本地执行使用的制品函数定义；空集合表示已验证无外部函数，null表示旧同步协议。 */
    @TableField(exist = false)
    private List<RuleFunction> frozenFunctions;
    @TableField(exist = false)
    private boolean requiresServerExecution;
    @TableField(exist = false)
    private String serverExecutionReason;
    private Integer status;
    private String publishBy;
    private LocalDateTime publishTime;
    private LocalDateTime offlineTime;
}
