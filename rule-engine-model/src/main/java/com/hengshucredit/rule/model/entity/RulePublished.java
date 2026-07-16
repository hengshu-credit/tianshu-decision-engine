package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("rule_engine.rule_published")
public class RulePublished {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String ruleCode;
    private Long definitionId;
    /** 规则所属项目编码 */
    private String projectCode;
    private Integer version;
    private String modelType;
    private String compiledScript;
    private String compiledType;
    private String modelJson;
    /** 仅用于同步给客户端，不映射数据库字段 */
    @TableField(exist = false)
    private List<String> outputScriptNames;
    private Integer status;
    private String publishBy;
    private LocalDateTime publishTime;
    private LocalDateTime offlineTime;
}
