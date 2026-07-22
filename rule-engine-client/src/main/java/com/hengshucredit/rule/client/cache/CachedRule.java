package com.hengshucredit.rule.client.cache;

import lombok.Data;

import java.util.List;

@Data
public class CachedRule {
    private String ruleCode;
    /** 规则所属项目编码 */
    private String projectCode;
    private int version;
    private Long revisionId;
    private String artifactDigest;
    private String modelType;
    private String compiledScript;
    private String compiledType;
    private String modelJson;
    /** 根规则提前终止时需要返回的输出字段脚本名（顺序与规则定义一致） */
    private List<String> outputScriptNames;
    private long lastUpdateTime;
}
