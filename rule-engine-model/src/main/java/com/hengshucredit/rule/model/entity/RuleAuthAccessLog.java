package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rule_engine.rule_auth_access_log")
public class RuleAuthAccessLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String projectCode;
    private Long authId;
    private String authCode;
    private String authType;
    private Long tokenId;
    private String tokenCode;
    private String authPhase;
    private String requestMethod;
    private String requestUri;
    private String requestId;
    private String clientIp;
    private Integer success;
    private String failureReason;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
