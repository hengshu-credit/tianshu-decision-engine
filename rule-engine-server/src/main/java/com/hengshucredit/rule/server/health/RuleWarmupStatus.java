package com.hengshucredit.rule.server.health;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.util.UUID;

@Component
public class RuleWarmupStatus {
    private RuleWarmupState state = RuleWarmupState.NOT_STARTED;
    private int targetCount;
    private int preparedCount;
    private int failureCount;
    private String runId;
    private Instant startedAt;
    private Instant completedAt;
    private final List<Map<String, Object>> failures = new ArrayList<>();

    public synchronized void start(int targets) {
        if (targets < 0) throw new IllegalArgumentException("规则预热目标数不能为负数");
        if (state != RuleWarmupState.NOT_STARTED) {
            throw new IllegalStateException("规则预热已经开始");
        }
        begin(targets);
    }

    /** 允许修复规则/函数后在不重启进程的情况下重新执行预热。 */
    public synchronized void retry(int targets) {
        if (targets < 0) throw new IllegalArgumentException("规则预热目标数不能为负数");
        if (state == RuleWarmupState.WARMING) {
            throw new IllegalStateException("规则预热正在进行中");
        }
        begin(targets);
    }

    private void begin(int targets) {
        targetCount = targets;
        preparedCount = 0;
        failureCount = 0;
        failures.clear();
        runId = UUID.randomUUID().toString();
        startedAt = Instant.now();
        completedAt = null;
        state = RuleWarmupState.WARMING;
    }

    public synchronized void recordPrepared() {
        requireWarming();
        preparedCount++;
    }

    public synchronized void recordFailure(Long definitionId, Integer version, Throwable failure) {
        recordFailure(definitionId, version, null, null, failure);
    }

    public synchronized void recordFailure(Long definitionId, Integer version, Long revisionId,
                                           String artifactDigest, Throwable failure) {
        requireWarming();
        failureCount++;
        if (failures.size() >= 20) return;
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("definitionId", definitionId);
        detail.put("version", version);
        detail.put("revisionId", revisionId);
        detail.put("artifactDigest", artifactDigest);
        String message = failure == null ? "未知预热错误" : failure.getMessage();
        detail.put("message", message);
        detail.put("code", failureCode(message));
        detail.put("title", failureTitle(message));
        detail.put("nextAction", failureNextAction(message));
        failures.add(detail);
    }

    private static String failureCode(String message) {
        if (message == null) return "WARMUP_FAILED";
        if (message.contains("函数未绑定") || message.contains("FUNCTION_NOT_BOUND")) return "FUNCTION_NOT_BOUND";
        if (message.contains("语法") || message.contains("SYNTAX_ERROR") || message.contains("编译脚本")) return "INVALID_SCRIPT";
        if (message.contains("制品") || message.contains("artifact") || message.contains("绑定")) return "ARTIFACT_INVALID";
        return "WARMUP_FAILED";
    }

    private static String failureTitle(String message) {
        return switch (failureCode(message)) {
            case "FUNCTION_NOT_BOUND" -> "脚本函数未绑定";
            case "INVALID_SCRIPT" -> "发布脚本无法编译";
            case "ARTIFACT_INVALID" -> "发布制品不可用";
            default -> "规则预热失败";
        };
    }

    private static String failureNextAction(String message) {
        return switch (failureCode(message)) {
            case "FUNCTION_NOT_BOUND" -> "在规则设计器中重新选择对应函数，保存并重新编译发布";
            case "INVALID_SCRIPT" -> "打开规则设计器修复配置或脚本，编译通过后重新发布";
            case "ARTIFACT_INVALID" -> "检查制品依赖和固定版本绑定，生成新制品后重新发布";
            default -> "打开规则详情查看错误，修复后重新发布并点击预热重试";
        };
    }

    public synchronized void complete() {
        requireWarming();
        state = failureCount == 0 ? RuleWarmupState.READY : RuleWarmupState.FAILED;
        completedAt = Instant.now();
    }

    public synchronized RuleWarmupState getState() {
        return state;
    }

    public synchronized Map<String, Object> details() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("targetScope", "PRODUCTION_ACTIVE");
        details.put("state", state.name());
        details.put("targetCount", targetCount);
        details.put("preparedCount", preparedCount);
        details.put("failureCount", failureCount);
        details.put("runId", runId);
        details.put("startedAt", startedAt);
        details.put("completedAt", completedAt);
        details.put("failures", List.copyOf(failures));
        return details;
    }

    private void requireWarming() {
        if (state != RuleWarmupState.WARMING) {
            throw new IllegalStateException("规则预热不在进行中");
        }
    }
}
