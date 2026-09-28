package com.hengshucredit.rule.model.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class ProjectWorkbenchDTO {
    private ProjectSummary project;
    private Metrics metrics;
    private List<CheckItem> checks = new ArrayList<>();
    private RecentExecution recentExecution;
    private List<String> warnings = new ArrayList<>();
    /** 全局规则预热状态，供项目工作台显示发布后的运行可用性。 */
    private Map<String, Object> warmup;

    @Data
    public static class ProjectSummary {
        private Long id;
        private String projectCode;
        private String projectName;
        private String description;
        private Integer status;
    }

    @Data
    public static class Metrics {
        private Long fieldCount;
        private Long dataSourceCount;
        private Long enabledDataSourceCount;
        private Long externalDataSourceCount;
        private Long enabledExternalDataSourceCount;
        private Long databaseDataSourceCount;
        private Long enabledDatabaseDataSourceCount;
        private Long modelCount;
        private Long ruleCount;
        private Long draftRuleCount;
        private Long publishedRuleCount;
        private Long testScenarioCount;
        private Long pendingApprovalCount;
        private Long recentExecutionCount;
        private Long recentSuccessCount;
        private Double recentSuccessRate;
    }

    @Data
    public static class CheckItem {
        private String code;
        private String title;
        private String status;
        private String reason;
        private String actionCode;
        private String actionLabel;

        public CheckItem() {
        }

        public CheckItem(String code, String title, String status,
                         String reason, String actionCode,
                         String actionLabel) {
            this.code = code;
            this.title = title;
            this.status = status;
            this.reason = reason;
            this.actionCode = actionCode;
            this.actionLabel = actionLabel;
        }
    }

    @Data
    public static class RecentExecution {
        private String traceId;
        private String ruleCode;
        private Integer ruleVersion;
        private Integer success;
        private Long executeTimeMs;
        private String source;
        private LocalDateTime createTime;
    }
}
