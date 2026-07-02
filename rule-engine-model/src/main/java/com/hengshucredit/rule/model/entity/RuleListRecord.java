package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rule_engine.rule_list_record")
public class RuleListRecord {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long listId;
    private String itemType;
    private String itemContent;
    private LocalDateTime effectiveTime;
    private LocalDateTime expireTime;
    private String reason;
    private String remark;
    /** ADD/UPDATE/DELETE，表示当前记录最近一次执行操作。 */
    private String lastOperation;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
