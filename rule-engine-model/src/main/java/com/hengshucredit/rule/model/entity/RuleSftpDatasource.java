package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** SFTP 连接池数据源配置。敏感字段由治理层加密保存。 */
@Data
@TableName("rule_sftp_datasource")
public class RuleSftpDatasource {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String scope;
    private String datasourceCode;
    private String datasourceName;
    private String connectionMode;
    private String host;
    private Integer port;
    private String basePath;
    private String username;
    private String password;
    private String privateKey;
    private String passphrase;
    private String knownHosts;
    private Boolean strictHostKeyChecking;
    private Integer connectTimeoutMs;
    private Integer sessionTimeoutMs;
    private Integer socketTimeoutMs;
    private Integer maxPoolSize;
    private Integer minIdle;
    private Integer borrowTimeoutMs;
    private Integer idleTimeoutMs;
    private Integer maxFileSizeBytes;
    private String fileNameEncoding;
    private String extraConfig;
    private String description;
    private Integer status;
    @TableField(exist = false)
    private String projectName;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
