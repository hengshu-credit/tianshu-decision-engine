package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("rule_engine.rule_designer_save_operation")
public class RuleDesignerSaveOperation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long definitionId;
    private String requestId;
    private String requestDigest;
    private String responseJson;
    private String createBy;
    private LocalDateTime createTime;
}
