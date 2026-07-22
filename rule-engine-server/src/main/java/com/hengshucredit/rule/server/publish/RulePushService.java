package com.hengshucredit.rule.server.publish;

import com.hengshucredit.rule.model.dto.RulePushMessage;
import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

@Service
public class RulePushService {

    private static final Logger log = LoggerFactory.getLogger(RulePushService.class);
    private static final String BROADCAST_CHANNEL = "rule:push:broadcast";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void push(RulePushMessage message) {
        pushReliable(message);
    }

    public boolean pushReliable(RulePushMessage message) {
        String projectCode = trimToNull(message.getProjectCode());
        if (projectCode != null) {
            return pushToAppReliable(projectCode, message);
        }
        String json = JSON.toJSONString(message);
        try {
            stringRedisTemplate.convertAndSend(BROADCAST_CHANNEL, json);
            log.info("Rule pushed to Redis: {} action={}", message.getRuleCode(), message.getAction());
            return true;
        } catch (Exception e) {
            log.error("Failed to push rule to Redis: {}", e.getMessage(), e);
            return false;
        }
    }

    public void pushToApp(String appName, RulePushMessage message) {
        pushToAppReliable(appName, message);
    }

    public boolean pushToAppReliable(String appName, RulePushMessage message) {
        String channel = "rule:push:" + appName;
        String json = JSON.toJSONString(message);
        try {
            stringRedisTemplate.convertAndSend(channel, json);
            return true;
        } catch (Exception e) {
            log.error("Failed to push rule to app {}: {}", appName, e.getMessage(), e);
            return false;
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
