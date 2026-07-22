package com.hengshucredit.rule.server.publish;

import com.hengshucredit.rule.model.dto.RulePushMessage;
import org.junit.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class RulePushServiceTest {

    @Test
    public void pushUsesProjectChannelWhenProjectCodeExists() {
        RulePushService service = new RulePushService();
        RecordingRedisTemplate redisTemplate = new RecordingRedisTemplate();
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redisTemplate);

        RulePushMessage message = new RulePushMessage();
        message.setRuleCode("risk_rule");
        message.setProjectCode("credit-app");
        service.push(message);

        assertEquals(1, redisTemplate.channels.size());
        assertEquals("rule:push:credit-app", redisTemplate.channels.get(0));
    }

    @Test
    public void pushFallsBackToBroadcastWithoutProjectCode() {
        RulePushService service = new RulePushService();
        RecordingRedisTemplate redisTemplate = new RecordingRedisTemplate();
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redisTemplate);

        RulePushMessage message = new RulePushMessage();
        message.setRuleCode("global_rule");
        service.push(message);

        assertEquals(1, redisTemplate.channels.size());
        assertEquals("rule:push:broadcast", redisTemplate.channels.get(0));
    }

    private static class RecordingRedisTemplate extends StringRedisTemplate {
        private final List<String> channels = new ArrayList<>();

        @Override
        public Long convertAndSend(String channel, Object message) {
            channels.add(channel);
            return 0L;
        }
    }
}
