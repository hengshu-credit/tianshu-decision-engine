import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.core.engine.RequestContext;
import com.hengshucredit.rule.core.engine.RuntimeContextBridge;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.sun.management.ThreadMXBean;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Standalone, identical workload for before/after JFR recordings; not a timing assertion. */
public class RuntimeContextProfile {
    private static volatile Object sink;
    private static final ThreadMXBean BEAN = (ThreadMXBean) ManagementFactory.getThreadMXBean();

    public static void main(String[] args) throws Exception {
        Map<String, Object> rule = Map.of("id", 1L, "code", "PROFILE", "traceId", "profile");
        Map<String, Map<String, Object>> states = new LinkedHashMap<>();
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < 32; i++) {
            states.put("VARIABLE:" + i, Map.of("OUTCOME", "SUCCESS", "CACHE_STATE", "MISS"));
            values.put("C" + i, Map.of("limit", i, "bands", List.of(1, 2, 3, 4)));
        }
        values.put("age", 28);
        values.put("score", 650);
        QLExpressEngine engine = new QLExpressEngine();
        RuntimeContextBridge.setRuleContext(rule, List.of("age >= 18"));
        RuntimeContextBridge.replaceSourceStates(states);
        values.forEach((key, value) -> { if (key.startsWith("C")) RuntimeContextBridge.registerConstant(key, value); });
        Map<String, Runnable> workloads = new LinkedHashMap<>();
        workloads.put("threadlocal", () -> {
            sink = RuntimeContextBridge.currentRule();
            sink = RuntimeContextBridge.currentMatchedConditions();
            sink = RuntimeContextBridge.sourceStatusMatches("VARIABLE", "1", "OUTCOME", "SUCCESS");
        });
        workloads.put("snapshot", () -> {
            RuntimeContextBridge.setRuleContext(rule, List.of("age >= 18"));
            RuntimeContextBridge.replaceSourceStates(states);
            RuntimeContextBridge.ContextSnapshot snapshot = RuntimeContextBridge.captureContext();
            try (RuntimeContextBridge.ContextScope ignored = RuntimeContextBridge.installContext(snapshot, event -> {})) {
                sink = RuntimeContextBridge.currentRule();
            }
        });
        workloads.put("constant_copy", () -> values.forEach((key, value) -> {
            if (key.startsWith("C")) RuntimeContextBridge.registerConstant(key, value);
        }));
        workloads.put("constant_compare", () -> {
            if (!RuntimeContextBridge.containsRegisteredConstants(values)) throw new AssertionError("constants missing");
            RuntimeContextBridge.assertConstantsUnchanged(values);
        });
        workloads.put("execute", () -> {
            RuleResult result = engine.execute("return age >= 18 && score >= 600;", values, false);
            if (!result.isSuccess() || !Boolean.TRUE.equals(result.getResult())) throw new AssertionError(result.getErrorMessage());
            sink = result;
        });
        QLExpressEngine.PreparedScript prepared = engine.prepare("return age >= 18 && score >= 600;");
        workloads.put("execute_empty_request", () -> {
            RuleResult result = engine.execute(prepared, values, false, new RequestContext());
            if (!result.isSuccess() || !Boolean.TRUE.equals(result.getResult())) throw new AssertionError(result.getErrorMessage());
            sink = result;
        });
        for (var workload : workloads.entrySet()) {
            run(workload.getValue(), 1500);
            try (Recording recording = new Recording(Configuration.getConfiguration("profile"))) {
                recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(2));
                recording.enable("jdk.ObjectAllocationSample").with("throttle", "1000/s");
                recording.start();
                long thread = Thread.currentThread().getId();
                long allocated = BEAN.getThreadAllocatedBytes(thread);
                long cpu = BEAN.getCurrentThreadCpuTime();
                long start = System.nanoTime();
                long count = run(workload.getValue(), 4000);
                long elapsed = System.nanoTime() - start;
                cpu = BEAN.getCurrentThreadCpuTime() - cpu;
                allocated = BEAN.getThreadAllocatedBytes(thread) - allocated;
                recording.stop();
                recording.dump(Path.of(args[0] + "-" + workload.getKey() + ".jfr"));
                System.out.printf(Locale.ROOT, "%s ops=%d wall_ns/op=%.1f cpu_ns/op=%.1f bytes/op=%.1f%n",
                        workload.getKey(), count, (double) elapsed / count, (double) cpu / count, (double) allocated / count);
            }
        }
        RuntimeContextBridge.clear();
    }

    private static long run(Runnable work, long milliseconds) {
        long end = System.nanoTime() + milliseconds * 1_000_000;
        long count = 0;
        do {
            for (int i = 0; i < 256; i++) work.run();
            count += 256;
        } while (System.nanoTime() < end);
        return count;
    }
}
