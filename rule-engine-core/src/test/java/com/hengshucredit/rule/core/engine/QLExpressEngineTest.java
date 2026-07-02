package com.hengshucredit.rule.core.engine;

import com.hengshucredit.rule.model.dto.RuleResult;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class QLExpressEngineTest {

    @Test
    public void executeTruncatesOversizedTraceButKeepsResult() {
        StringBuilder script = new StringBuilder();
        script.append("x = 0\n");
        for (int i = 0; i < 1200; i++) {
            script.append("if (a >= 0) { x = x + 1 }\n");
        }
        script.append("x");

        Map<String, Object> context = new HashMap<>();
        context.put("a", 1);

        RuleResult result = new QLExpressEngine().execute(script.toString(), context, true);

        assertTrue(result.getErrorMessage(), result.isSuccess());
        assertEquals(1200, ((Number) result.getResult()).intValue());
        assertNotNull(result.getTraces());
        assertEquals(1, result.getTraces().size());
        assertTrue(result.getTraces().get(0) instanceof Map);
        Map<?, ?> traceSummary = (Map<?, ?>) result.getTraces().get(0);
        assertEquals("TRACE_TRUNCATED", traceSummary.get("type"));
        assertTrue(((Number) traceSummary.get("originalJsonLength")).intValue() > 100000);
    }

    @Test
    public void defaultEngineRejectsJavaMemberAccess() {
        RuleResult result = new QLExpressEngine().execute("\"abc\".getClass().getName()", new HashMap<>());

        assertFalse(result.isSuccess());
    }
}
