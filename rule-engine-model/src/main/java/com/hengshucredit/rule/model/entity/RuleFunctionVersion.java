package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rule_function_version")
public class RuleFunctionVersion implements AccountOwnedRecord {
    @com.baomidou.mybatisplus.annotation.TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT, updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private Long actorUserId;
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long functionId;
    private Integer version;
    private Integer businessVersion;
    public Integer getDisplayVersion() { return businessVersion == null ? version : businessVersion; }
    private String functionJson;
    private String changeLog;
    private String publishBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime publishTime;
}
