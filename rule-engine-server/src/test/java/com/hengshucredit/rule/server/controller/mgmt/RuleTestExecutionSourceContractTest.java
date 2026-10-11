package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.server.service.RuleExecuteService;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/** An explicit test source must never silently fall back to the mutable workspace. */
public class RuleTestExecutionSourceContractTest {
    @Test public void unknownSourceCannotExecuteWorkspace() {
        rejectsBeforeExecution(Map.of("sourceType", "UNKNOWN"));
    }

    @Test public void revisionSourceRequiresItsStableId() {
        rejectsBeforeExecution(Map.of("sourceType", "REVISION", "lockVersion", 0));
    }

    @Test public void versionSourceRequiresItsImmutableSnapshotId() {
        rejectsBeforeExecution(Map.of("sourceType", "VERSION"));
    }

    @Test public void previewSourceRequiresTheActualCanvasModel() {
        rejectsBeforeExecution(Map.of("sourceType", "PREVIEW"));
    }

    private void rejectsBeforeExecution(Map<String, Object> source) {
        AtomicInteger calls = new AtomicInteger();
        RuleDefinitionController controller = new RuleDefinitionController();
        ReflectionTestUtils.setField(controller, "executeService", new RuleExecuteService() {
            @Override public RuleResult testExecute(Long definitionId, Map<String, Object> params, Long projectId) {
                calls.incrementAndGet();
                RuleResult result = new RuleResult();
                result.setSuccess(true);
                result.setResult(Map.of("score", 9));
                return result;
            }
        });
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("definitionId", 10L);
        request.put("executionSource", source);
        request.put("params", Map.of("age", 25));
        assertThrows("显式来源不合法时不得运行当前管理投影", IllegalArgumentException.class,
                () -> controller.execute(request));
        assertEquals("拒绝发生在调用执行引擎之前", 0, calls.get());
    }
}
