package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.server.service.DBConnectPools;
import com.alibaba.fastjson.JSON;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Broadcasts database pool invalidation so every server node converges after approval. */
@Component
public class DatabasePoolRefreshNotifier implements MessageListener {
    public static final String CHANNEL = "rule:push:database";

    @Resource
    private StringRedisTemplate redisTemplate;
    @Resource
    private RedisMessageListenerContainer listenerContainer;
    @Resource
    private DBConnectPools pools;

    @PostConstruct
    public void subscribe() {
        listenerContainer.addMessageListener(this, new ChannelTopic(CHANNEL));
    }

    public void publish(Long datasourceId) {
        if (datasourceId == null) return;
        try {
            redisTemplate.convertAndSend(CHANNEL, JSON.toJSONString(Map.of("datasourceId", datasourceId)));
        } catch (RuntimeException ignored) {
            // 当前节点已经刷新；其他节点会在下一次配置版本检查或重启时收敛。
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            Long datasourceId = JSON.parseObject(body).getLong("datasourceId");
            if (datasourceId != null) pools.refresh(datasourceId);
        } catch (RuntimeException ignored) {
            // 非法通知不能阻断 Redis 订阅线程。
        }
    }
}
