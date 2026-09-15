package com.hengshucredit.rule.model.dto;

import com.hengshucredit.rule.model.enums.RuleDraftSourceType;
import lombok.Data;

@Data
public class RuleDraftSourceRequest {
    private RuleDraftSourceType sourceType;
    private Long sourceId;
    /** 仅在显式暂存时提交当前页面副本；省略则保持原有来源复制行为。 */
    private String modelJson;
    private String openApiConfigJson;
    private Boolean updateOpenApiConfig;
}
