package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("rule_engine.rule_definition_version")
public class RuleDefinitionVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long definitionId;
    private Integer version;
    /** Business version is distinct from the immutable publication sequence above. */
    private Integer businessVersion;
    private Long versionBindingId;
    private Long bindingGeneration;
    private Long revisionId;
    private Long artifactId;
    private String artifactDigest;
    private String modelJson;
    private String compiledScript;
    private String compiledType;
    /** 发布时的对外规则接口不可变快照 */
    private String openApiConfigJson;
    private String changeLog;
    private String publishBy;
    private LocalDateTime publishTime;
}
