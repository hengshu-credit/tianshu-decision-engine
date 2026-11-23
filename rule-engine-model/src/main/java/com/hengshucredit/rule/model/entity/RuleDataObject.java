package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("rule_data_object")
public class RuleDataObject {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属项目ID，0表示全局 */
    private Long projectId;
    /** 作用域：GLOBAL-全局，PROJECT-项目级 */
    private String scope;
    private String objectCode;
    private String objectLabel;
    /** 脚本中的对象引用名，未指定时使用原样的对象编码。 */
    private String scriptName;
    public String getScriptName() {
        return scriptName == null || scriptName.isBlank() ? objectCode : scriptName;
    }

    private String objectType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long mockRuleId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String exampleMode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String exampleCaseBinding;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String exampleValue;
    /** 仅引用型叶子字段可按需取值；默认关闭。 */
    private Boolean lazyLoadReferences;
    private String sourceType;
    private String sourceContent;
    private Long parentObjectId;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
