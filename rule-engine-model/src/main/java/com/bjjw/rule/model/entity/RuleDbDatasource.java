package com.bjjw.rule.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rule_engine.rule_db_datasource")
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
    private String password;
    private String sshHost;
    private Integer sshPort;
    private String sshUsername;
    private String sshPassword;
    private String sshPrivateKey;
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
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
