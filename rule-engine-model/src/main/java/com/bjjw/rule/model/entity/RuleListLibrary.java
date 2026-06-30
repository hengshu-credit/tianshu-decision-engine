package com.bjjw.rule.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rule_engine.rule_list_library")
public class RuleListLibrary {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private String scope;
    private String listCode;
    private String listName;
    /** BLACK/GREY/WHITE/OTHER，仅用于业务标识和筛选。 */
    private String listType;
    private String description;
    private Integer status;

    @TableField(exist = false)
    private String projectName;

    @TableField(exist = false)
    private Long recordCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
