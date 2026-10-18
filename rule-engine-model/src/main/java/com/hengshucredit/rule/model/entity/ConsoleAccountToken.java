package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("console_account_token")
public class ConsoleAccountToken {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String purpose;
    private String tokenHash;
    private LocalDateTime expiresAt;
    private LocalDateTime consumedAt;
    private String requestIp;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
