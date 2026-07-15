package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hengshucredit.rule.model.entity.RuleRuntimeCallLog;
import com.hengshucredit.rule.server.mapper.RuleRuntimeCallLogMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class RuleRuntimeCallLogService extends ServiceImpl<RuleRuntimeCallLogMapper, RuleRuntimeCallLog> {

    public IPage<RuleRuntimeCallLog> pageList(int pageNum, int pageSize, String moduleType, String actionType,
                                              String targetCode, String traceId, Integer success,
                                              LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<RuleRuntimeCallLog> wrapper = new LambdaQueryWrapper<>();
        if (hasText(moduleType)) {
            wrapper.eq(RuleRuntimeCallLog::getModuleType, moduleType);
        }
        if (hasText(actionType)) {
            wrapper.eq(RuleRuntimeCallLog::getActionType, actionType);
        }
        if (hasText(targetCode)) {
            wrapper.like(RuleRuntimeCallLog::getTargetCode, targetCode);
        }
        if (hasText(traceId)) {
            wrapper.and(w -> w.eq(RuleRuntimeCallLog::getTraceId, traceId)
                    .or().eq(RuleRuntimeCallLog::getRuleTraceId, traceId));
        }
        if (success != null) {
            wrapper.eq(RuleRuntimeCallLog::getSuccess, success);
        }
        if (startTime != null) {
            wrapper.ge(RuleRuntimeCallLog::getCreateTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(RuleRuntimeCallLog::getCreateTime, endTime);
        }
        wrapper.orderByDesc(RuleRuntimeCallLog::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    public void safeSave(RuleRuntimeCallLog log) {
        if (log == null) {
            return;
        }
        try {
            save(log);
        } catch (Exception ignored) {
            // 业务调用不能因为诊断日志写入失败而失败。
        }
    }

    public String toJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        try {
            return JSON.toJSONString(value);
        } catch (StackOverflowError e) {
            return "{\"error\":\"JSON_SERIALIZE_STACK_OVERFLOW\"}";
        } catch (Exception e) {
            return "{\"error\":\"JSON_SERIALIZE_FAILED\",\"message\":\"" + escapeJson(e.getMessage()) + "\"}";
        }
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
