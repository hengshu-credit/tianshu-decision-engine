package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.server.mapper.RuleProjectAuthTokenMapper;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class ProjectTokenLastUsedCoalescerTest {

    @Test
    public void repeatedTouchesWithinWindowAreWrittenOnlyOnce() {
        ExternalCallProperties properties = new ExternalCallProperties();
        properties.setTokenLastUsedCoalesceSeconds(60L);
        AtomicInteger updates = new AtomicInteger();
        RuleProjectAuthTokenMapper mapper = (RuleProjectAuthTokenMapper) Proxy.newProxyInstance(
                RuleProjectAuthTokenMapper.class.getClassLoader(),
                new Class<?>[]{RuleProjectAuthTokenMapper.class},
                (proxy, method, args) -> {
                    if ("updateLastUsedTime".equals(method.getName())) {
                        updates.incrementAndGet();
                        return 1;
                    }
                    if (method.getReturnType().isPrimitive()) return 0;
                    return null;
                });
        ProjectTokenLastUsedCoalescer coalescer = new ProjectTokenLastUsedCoalescer(properties, mapper);
        LocalDateTime now = LocalDateTime.now();

        coalescer.touch(10L, now);
        coalescer.touch(10L, now.plusSeconds(10L));
        coalescer.flush();

        assertEquals(1, updates.get());
        coalescer.close();
    }
}
