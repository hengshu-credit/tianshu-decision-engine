package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rule_engine.rule_project_auth")
public class RuleProjectAuth {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String authCode;
    private String authName;
    private String authType;
    private String lookupKey;
    private String identifierCiphertext;
    private String secretCiphertext;
    private String configJson;
    private String accessPolicyJson;
    private Integer asyncAccessLogEnabled;
    private Integer tokenTtlSeconds;
    private Integer tokenGraceSeconds;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
