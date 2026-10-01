package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rule_list_change_item")
public class RuleListChangeItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long batchId;
    @TableField("source_row")
    private Integer rowNumber;
    private String operation;
    private Long targetRecordId;
    private String itemType;
    private String itemContent;
    private LocalDateTime effectiveTime;
    private LocalDateTime expireTime;
    private String reason;
    private String remark;
    private Integer targetStatus;
    private String validationStatus;
    private String validationMessage;
    private String baselineDigest;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
