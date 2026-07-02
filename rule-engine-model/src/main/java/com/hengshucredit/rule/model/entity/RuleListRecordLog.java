package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rule_engine.rule_list_record_log")
public class RuleListRecordLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long listId;
    private Long recordId;
    private String itemType;
    private String itemContent;
    private LocalDateTime effectiveTime;
    private LocalDateTime expireTime;
    private String reason;
    private String remark;
    private String operation;
    private String operator;

    @TableField(exist = false)
    private String changeContent;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
