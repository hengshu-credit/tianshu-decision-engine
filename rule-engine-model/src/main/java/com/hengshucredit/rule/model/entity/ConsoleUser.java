package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("console_user")
public class ConsoleUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    /** 不随恢复改名变化的业务归属标识，兼容历史 applicant/operator。 */
    private String operatorName;
    /** 历史重复超管合并归档后指向保留账户；归档账户不可重新登录或启用。 */
    private Long mergedIntoUserId;
    private String mergedUsername;
    private String displayName;
    private String passwordHash;
    private String email;
    private LocalDateTime emailVerifiedAt;
    private String registrationStatus;
    private String registrationSource;
    private Integer status;
    private Long permissionVersion;
    private String projectAccessMode;
    private Boolean globalEditable;
    private LocalDateTime lastLoginTime;
    private String createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    private String updateBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
