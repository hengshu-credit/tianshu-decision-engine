import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.dto.RuleResult;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class EngineAuditBenchmark {
    private static final String SCRIPT = "approved = age >= 18 && score >= 600; return approved;";
    public static void main(String[] args) {
        QLExpressEngine engine = new QLExpressEngine();
        for (boolean trace : new boolean[] { false, true }) {
            for (int i = 0; i < 5000; i++) execute(engine, trace);
            long[] times = new long[20000];
            for (int i = 0; i < times.length; i++) {
                long start = System.nanoTime();
                execute(engine, trace);
                times[i] = System.nanoTime() - start;
            }
            double mean = Arrays.stream(times).average().orElseThrow() / 1000.0;
            Arrays.sort(times);
            System.out.printf(Locale.ROOT, "trace=%s samples=%d mean_us=%.3f p50_us=%.3f p95_us=%.3f p99_us=%.3f%n",
                    trace, times.length, mean, times[9999] / 1000.0, times[18999] / 1000.0, times[19799] / 1000.0);
        }
    }
    private static void execute(QLExpressEngine engine, boolean trace) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("age", 28);
        input.put("score", 650);
        RuleResult result = engine.execute(SCRIPT, input, trace);
        if (!result.isSuccess() || !Boolean.TRUE.equals(result.getResult())) {
            throw new IllegalStateException("Unexpected result: " + result.getErrorMessage());
        }
    }
}
