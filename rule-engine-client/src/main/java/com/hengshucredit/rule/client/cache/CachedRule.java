package com.hengshucredit.rule.client.cache;

import lombok.Data;

import java.util.List;

@Data
public class CachedRule {
    @com.alibaba.fastjson.annotation.JSONField(serialize = false, deserialize = false)
    private transient com.hengshucredit.rule.core.engine.QLExpressEngine.PreparedScript preparedScript;
    private String ruleCode;
    private Long definitionId;
    private Long versionBindingId;
    private Long bindingGeneration;
    private boolean fixedVersion;
    private boolean imported;
    private java.util.Map<String, Long> importBindings;
    /** 规则所属项目编码 */
    private String projectCode;
    /** 服务端标记该规则需要变量来源解析/恢复状态时，本地纯计算模式不得静默执行。 */
    private boolean requiresServerExecution;
    private String serverExecutionReason;
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
