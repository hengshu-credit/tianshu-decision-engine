package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("rule_auth_institution")
public class RuleAuthInstitution {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String institutionName;
    private Integer status;
}
