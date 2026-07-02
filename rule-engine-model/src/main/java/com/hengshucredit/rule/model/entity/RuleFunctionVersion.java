package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rule_engine.rule_function_version")
public class RuleFunctionVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long functionId;
    private Integer version;
    private String functionJson;
    private String changeLog;
    private String publishBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime publishTime;
}
