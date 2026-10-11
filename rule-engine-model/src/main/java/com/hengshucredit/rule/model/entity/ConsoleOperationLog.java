package com.hengshucredit.rule.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("console_operation_log")
public class ConsoleOperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private String module;
    private String action;
    private String requestMethod;
    private String requestPath;
    private String pagePath;
    private String targetType;
    private String targetId;
    private String targetName;
    private String outcome;
    private Integer resultCode;
    private String targetVersion;
    private String changeSummary;
    private String clientIp;
    private Long durationMs;
    private LocalDateTime createTime;
}
