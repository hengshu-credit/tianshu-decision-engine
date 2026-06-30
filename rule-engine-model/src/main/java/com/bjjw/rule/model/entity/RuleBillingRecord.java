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
@TableName("rule_engine.rule_billing_record")
public class RuleBillingRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String projectCode;
    private String billingCode;
    private String billingName;
    private String billingTarget;
    private Long targetRefId;
    private String requestId;
    private String ruleCode;
    private String apiCode;
    private String datasourceCode;
    private Integer success;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private String currency;
    private Long costTimeMs;
    private String errorMessage;
    private LocalDateTime occurTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
