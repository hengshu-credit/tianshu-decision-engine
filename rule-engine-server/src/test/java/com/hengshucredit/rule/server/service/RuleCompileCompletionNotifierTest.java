package com.hengshucredit.rule.server.service;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class RuleCompileCompletionNotifierTest {
    @Test public void completionWakesOnlyMatchingTasksAndLeavesNoStoredCompletion() {
        var notifier = new RuleCompileCompletionNotifier();
        var first = notifier.await("one");
        var second = notifier.await("one");
        var other = notifier.await("two");
        assertFalse(first.hasResult());
        notifier.completed("one");
        assertTrue(first.hasResult()); assertTrue(second.hasResult()); assertFalse(other.hasResult());
        assertFalse(notifier.await("one").hasResult()); // Controller's second status check closes the completion race.
        notifier.close(); assertTrue(other.hasResult());
    }

    @Test public void crossNodeNotificationWakesLocalWaiterWithoutTransportingRuleContent() {
        var notifier = new RuleCompileCompletionNotifier();
        var result = notifier.await("remote-task");
        notifier.onMessage(new org.springframework.data.redis.connection.DefaultMessage(
                "channel".getBytes(StandardCharsets.UTF_8), "remote-task".getBytes(StandardCharsets.UTF_8)), null);
        assertTrue(result.hasResult());
        assertEquals(Boolean.TRUE, ((com.hengshucredit.rule.model.ApiResponse<?>) result.getResult()).getData());
        notifier.close();
    }

    @Test public void excessWaitersFallBackWithoutUnboundedRetention() {
        var notifier = new RuleCompileCompletionNotifier();
        for (int i = 0; i < 8; i++) assertFalse(notifier.await("one").hasResult());
        var overflow = notifier.await("one");
        assertEquals(Boolean.FALSE, ((com.hengshucredit.rule.model.ApiResponse<?>) overflow.getResult()).getData());
        notifier.close();
    }
}
