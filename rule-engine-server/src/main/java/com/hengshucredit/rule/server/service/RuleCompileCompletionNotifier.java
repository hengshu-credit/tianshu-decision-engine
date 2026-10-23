package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.ApiResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Completion is a wake-up hint only. Results are fetched through the usual authenticated endpoint. */
@Service
public class RuleCompileCompletionNotifier implements MessageListener {
    private static final String CHANNEL = "rule:designer:compile:completed";
    @Autowired(required = false) private RedisMessageListenerContainer listeners;
    @Autowired(required = false) private StringRedisTemplate redis;
    private final Map<String, Set<DeferredResult<ApiResponse<Boolean>>>> waiting = new HashMap<>();

    @PostConstruct public void start() {
        if (listeners != null) listeners.addMessageListener(this, new ChannelTopic(CHANNEL));
    }

    public DeferredResult<ApiResponse<Boolean>> await(String taskId) {
        DeferredResult<ApiResponse<Boolean>> result = new DeferredResult<>(10000L, ApiResponse.ok(false));
        synchronized (waiting) {
            if (waiting.size() >= 256 || waiting.getOrDefault(taskId, Set.of()).size() >= 8) {
                result.setResult(ApiResponse.ok(false));
                return result;
            }
            waiting.computeIfAbsent(taskId, key -> new HashSet<>()).add(result);
        }
        Runnable remove = () -> {
            synchronized (waiting) {
                Set<DeferredResult<ApiResponse<Boolean>>> subscribers = waiting.get(taskId);
                if (subscribers == null) return;
                subscribers.remove(result);
                if (subscribers.isEmpty()) waiting.remove(taskId);
            }
        };
        result.onCompletion(remove);
        result.onTimeout(remove);
        result.onError(error -> remove.run());
        return result;
    }

    public void completed(String taskId) {
        wake(taskId);
        if (redis != null) {
            try { redis.convertAndSend(CHANNEL, taskId); }
            catch (RuntimeException ignored) { /* Timed status rechecks also cover lost cross-node hints. */ }
        }
    }

    void wake(String taskId) {
        Set<DeferredResult<ApiResponse<Boolean>>> subscribers;
        synchronized (waiting) { subscribers = waiting.remove(taskId); }
        if (subscribers != null) subscribers.forEach(result -> result.setResult(ApiResponse.ok(true)));
    }

    @Override public void onMessage(Message message, byte[] pattern) {
        wake(new String(message.getBody(), StandardCharsets.UTF_8));
    }

    @PreDestroy public void close() {
        if (listeners != null) listeners.removeMessageListener(this);
        List<String> tasks;
        synchronized (waiting) { tasks = List.copyOf(waiting.keySet()); }
        tasks.forEach(this::wake);
    }
}
