package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("governance_approval_event")
public class GovernanceApprovalEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long requestId;
    private String action;
    private String fromStatus;
    private String toStatus;
    private String actor;
    private String comment;
    private String detailsJson;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
