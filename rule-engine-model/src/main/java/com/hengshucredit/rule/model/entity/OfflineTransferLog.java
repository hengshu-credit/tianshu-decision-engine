package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rule_engine.offline_transfer_log")
public class OfflineTransferLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String operationType;
    private String status;
    private String operator;
    private String packageDigest;
    private String rootsJson;
    private Integer resourceCount;
    private String contentJson;
    private String errorMessage;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
