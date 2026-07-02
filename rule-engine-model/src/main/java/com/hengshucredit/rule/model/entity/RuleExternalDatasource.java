package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rule_engine.rule_external_datasource")
public class RuleExternalDatasource {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String scope;
    private String datasourceCode;
    private String datasourceName;
    private String providerName;
    private String protocol;
    private String baseUrl;
    private String authType;
    private String authConfig;
    private Integer tokenCacheSeconds;
    private String description;
    private Integer status;
    @TableField(exist = false)
    private String projectName;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
