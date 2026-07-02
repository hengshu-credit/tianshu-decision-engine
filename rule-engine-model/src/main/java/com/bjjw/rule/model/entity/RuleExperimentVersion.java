package com.bjjw.rule.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rule_engine.rule_experiment_version")
public class RuleExperimentVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long experimentId;
    private Integer version;
    private String experimentJson;
    private String groupsJson;
    private String changeLog;
    private String publishBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime publishTime;
}
