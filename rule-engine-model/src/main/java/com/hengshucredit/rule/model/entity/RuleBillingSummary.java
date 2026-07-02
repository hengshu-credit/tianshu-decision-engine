package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("rule_engine.rule_billing_summary")
public class RuleBillingSummary {
    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDate summaryDate;
    private Long projectId;
    private String projectCode;
    private String billingCode;
    private String billingTarget;
    private Long targetRefId;
    private Long totalCount;
    private Long successCount;
    private Long failCount;
    private BigDecimal totalQuantity;
    private BigDecimal totalAmount;
    private String currency;
    private BigDecimal avgCostTimeMs;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
