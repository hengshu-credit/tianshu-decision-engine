package com.bjjw.rule.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("rule_engine.rule_billing_config")
public class RuleBillingConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String scope;
    private String billingCode;
    private String billingName;
    private String billingTarget;
    private Long targetRefId;
    private String chargeType;
    private BigDecimal unitPrice;
    private String currency;
    private LocalDateTime effectiveTime;
    private LocalDateTime expireTime;
    private String description;
    private Integer status;
    @TableField(exist = false)
    private String projectName;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
