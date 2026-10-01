package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rule_list_change_batch")
public class RuleListChangeBatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long listId;
    private String sourceType;
    private String status;
    private Integer totalCount;
    private Integer addCount;
    private Integer updateCount;
    private Integer deleteCount;
    private Integer duplicateCount;
    private Integer invalidCount;
    private String contentDigest;
    private Long approvalRequestId;
    private String createdBy;
    private String appliedBy;
    private LocalDateTime appliedTime;
    private String terminalMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
