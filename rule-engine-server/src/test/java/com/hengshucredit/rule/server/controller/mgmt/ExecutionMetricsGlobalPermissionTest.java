package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.server.health.OnnxWarmupStatus;
import com.hengshucredit.rule.server.security.ConsoleAccessDeniedException;
import com.hengshucredit.rule.server.security.ConsoleProjectAccess;
import com.hengshucredit.rule.server.service.OnnxModelWarmupRunner;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class ExecutionMetricsGlobalPermissionTest {
    @Test public void restrictedScopeCannotStartNodeWideModelOrRuleWarmup() {
        ExecutionMetricsController controller = new ExecutionMetricsController();
        AtomicInteger calls = new AtomicInteger();
        ReflectionTestUtils.setField(controller, "onnxWarmupRunner", new OnnxModelWarmupRunner(null, null, new OnnxWarmupStatus()) {
            @Override public Map<String, Object> retryWarmup() { calls.incrementAndGet(); return Map.of("state", "READY"); }
        });
        ReflectionTestUtils.setField(controller, "consoleProjects", new ConsoleProjectAccess() {
            @Override public Scope current() { return new Scope(1L, Set.of(9L), Set.of("model:view", "model:edit", "rule:view", "rule:submit"), false, true, Map.of()); }
        });
        assertThrows(ConsoleAccessDeniedException.class, controller::retryModelWarmup);
        assertThrows(ConsoleAccessDeniedException.class, controller::retryRuleWarmup);
        assertEquals(0, calls.get());
        ReflectionTestUtils.setField(controller, "consoleProjects", new ConsoleProjectAccess() {
            @Override public Scope current() { return new Scope(1L, Set.of(), Set.of("model:view", "model:edit"), true, true, Map.of()); }
        });
        controller.retryModelWarmup();
        assertEquals(1, calls.get());
    }
}
