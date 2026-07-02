package com.hengshucredit.rule.client.sync;

import com.hengshucredit.rule.client.cache.L1MemoryCache;
import org.junit.Test;
import org.springframework.data.redis.listener.Topic;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class RedisSubscriberTest {

    @Test
    public void subscribesProjectPushChannelAndBroadcastChannel() throws Exception {
        RedisSubscriber subscriber = new RedisSubscriber(new L1MemoryCache(10), null, "credit_project");

        assertEquals("rule:push:credit_project", getField(subscriber, "channel"));
        List<?> topics = (List<?>) getField(subscriber, "topics");
        assertEquals(2, topics.size());
        assertEquals("rule:push:credit_project", ((Topic) topics.get(0)).getTopic());
        assertEquals("rule:push:broadcast", ((Topic) topics.get(1)).getTopic());
    }

    private Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
