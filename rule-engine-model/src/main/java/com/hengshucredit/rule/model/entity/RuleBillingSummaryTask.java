package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("rule_billing_summary_task")
public class RuleBillingSummaryTask {
    @TableId(type = IdType.INPUT)
    private String taskId;
    private LocalDate summaryDate;
    private String status;
    private String applicant;
    private Integer summaryCount;
    private Long recordCount;
    private String errorMessage;
    private LocalDateTime requestedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
