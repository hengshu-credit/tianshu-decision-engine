package com.hengshucredit.rule.server.health;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RuleWarmupStatus {
    private RuleWarmupState state = RuleWarmupState.NOT_STARTED;
    private int targetCount;
    private int preparedCount;
    private int failureCount;
    private final List<Map<String, Object>> failures = new ArrayList<>();

    public synchronized void start(int targets) {
        if (targets < 0) throw new IllegalArgumentException("规则预热目标数不能为负数");
        if (state != RuleWarmupState.NOT_STARTED) {
            throw new IllegalStateException("规则预热已经开始");
        }
        targetCount = targets;
        state = RuleWarmupState.WARMING;
    }

    public synchronized void recordPrepared() {
        requireWarming();
        preparedCount++;
    }

    public synchronized void recordFailure(Long definitionId, Integer version, Throwable failure) {
        requireWarming();
        failureCount++;
        if (failures.size() >= 20) return;
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("definitionId", definitionId);
        detail.put("version", version);
        detail.put("message", failure == null ? "未知预热错误" : failure.getMessage());
        failures.add(detail);
    }

    public synchronized void complete() {
        requireWarming();
        state = failureCount == 0 ? RuleWarmupState.READY : RuleWarmupState.FAILED;
    }

    public synchronized RuleWarmupState getState() {
        return state;
    }

    public synchronized Map<String, Object> details() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("state", state.name());
        details.put("targetCount", targetCount);
        details.put("preparedCount", preparedCount);
        details.put("failureCount", failureCount);
        details.put("failures", List.copyOf(failures));
        return details;
    }

    private void requireWarming() {
        if (state != RuleWarmupState.WARMING) {
            throw new IllegalStateException("规则预热不在进行中");
        }
    }
}
