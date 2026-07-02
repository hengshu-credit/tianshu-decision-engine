package com.hengshucredit.rule.model.dto;

import lombok.Data;

/**
 * 规则分页查询参数封装。
 * 将原来 pageList 方法的 17 个过滤参数归拢为一个 DTO，
 * 避免方法签名过长、可选参数难以阅读的问题。
 */
@Data
public class RuleQueryDTO {

    private Integer pageNum = 1;
    private Integer pageSize = 20;
    private Long projectId;
    private String modelType;
    private String keyword;
    private String projectName;
    private String scope;
    private String status;
    private String ruleCode;
    private String ruleName;
    private String projectCode;
    private String publishedVersion;
    private String createBeginTime;
    private String createEndTime;
    private String updateBeginTime;
    private String updateEndTime;

    /** 供 pageListForProject 使用：只过滤项目级规则时传入 scope=GLOBAL */
    private Boolean globalOnly;

    public int getPageNumOrDefault() {
        return pageNum != null && pageNum > 0 ? pageNum : 1;
    }

    public int getPageSizeOrDefault() {
        return pageSize != null && pageSize > 0 ? pageSize : 20;
    }
}