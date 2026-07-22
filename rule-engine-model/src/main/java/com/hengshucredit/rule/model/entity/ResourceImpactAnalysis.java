package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rule_engine.resource_impact_analysis")
public class ResourceImpactAnalysis {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String analysisToken;
    private String resourceType;
    private Long resourceId;
    private String action;
    private String impactDigest;
    private String reportJson;
    private String status;
    private LocalDateTime expiresAt;
    private String createBy;
    private LocalDateTime createTime;
    private String confirmBy;
    private LocalDateTime confirmTime;
}
