package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rule_engine.rule_project_auth_token")
public class RuleProjectAuthToken {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long authId;
    private String tokenCode;
    private String lookupKey;
    private String tokenCiphertext;
    private LocalDateTime issuedTime;
    private LocalDateTime expireTime;
    private LocalDateTime graceExpireTime;
    private LocalDateTime lastUsedTime;
    private LocalDateTime revokedTime;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
