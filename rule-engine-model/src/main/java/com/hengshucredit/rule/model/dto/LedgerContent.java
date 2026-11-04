package com.hengshucredit.rule.model.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 台账文章草稿与发布快照的共同内容；引用仅保存稳定 ID。 */
@Data
public class LedgerContent {
    private String title;
    private String summary;
    private Boolean summaryAuto;
    private String body;
    private String category;
    private String scope;
    private Long projectId;
    private LocalDateTime occurredAt;
    private List<Long> eventIds = new ArrayList<>();
    private String changeSummary;
}
