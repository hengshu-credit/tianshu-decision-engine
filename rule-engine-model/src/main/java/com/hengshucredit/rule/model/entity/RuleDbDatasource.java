package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "rule_db_datasource", autoResultMap = true)
public class RuleDbDatasource {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String scope;
    private String datasourceCode;
    private String datasourceName;
    private String dbType;
    private String connectionMode;
    private String host;
    private Integer port;
    private String databaseName;
    private String jdbcParams;
    private String driverClassName;
    private String jdbcUrl;
    private String username;
    @TableField(typeHandler = com.hengshucredit.rule.model.security.EncryptedCredentialTypeHandler.class)
    private String password;
    private String sshHost;
    private Integer sshPort;
    private String sshUsername;
    @TableField(typeHandler = com.hengshucredit.rule.model.security.EncryptedCredentialTypeHandler.class)
    private String sshPassword;
    @TableField(typeHandler = com.hengshucredit.rule.model.security.EncryptedCredentialTypeHandler.class)
    private String sshPrivateKey;
    @TableField(typeHandler = com.hengshucredit.rule.model.security.EncryptedCredentialTypeHandler.class)
    private String sshPassphrase;
    private Integer sshTimeoutMs;
    private Integer maxPoolSize;
    private Integer minIdle;
    private Integer connectionTimeoutMs;
    private Integer idleTimeoutMs;
    private String validationQuery;
    private String description;
    private Integer status;
    @TableField(exist = false)
    private String projectName;
    @TableField(exist = false)
    private java.util.Map<String, Boolean> configuredSecrets;
    @TableField(exist = false)
    private java.util.List<String> clearCredentials;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
