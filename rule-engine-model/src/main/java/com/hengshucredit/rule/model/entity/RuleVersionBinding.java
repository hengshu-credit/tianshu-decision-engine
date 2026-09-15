package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** Stable business version, pointing at an immutable publication snapshot. */
@Data
@TableName("rule_engine.rule_version_binding")
public class RuleVersionBinding {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long definitionId;
    private Integer versionNo;
    private Long generation;
    private Long snapshotId;
    private Integer status;
    private LocalDateTime updateTime;
}
