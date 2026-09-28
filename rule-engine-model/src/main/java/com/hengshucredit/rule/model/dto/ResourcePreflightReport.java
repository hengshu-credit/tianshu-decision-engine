package com.hengshucredit.rule.model.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;

/** 变量、外数、数据库和模型共用的资源级可执行性检查结果。 */
@Data
public class ResourcePreflightReport {
    private String resourceType;
    private Long resourceId;
    /** 报告生成时间；用于页面判断报告是否已过期。 */
    private LocalDateTime checkedAt;
    /** 检查范围固定为已保存配置，避免把预检误解为真实连通性测试。 */
    private String checkScope;
    private boolean valid;
    private List<RuleValidationIssue> errors = new ArrayList<>();
    private List<RuleValidationIssue> warnings = new ArrayList<>();
}
