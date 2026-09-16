import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordingFile;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;

/** Counts sampled stacks, not exact method timings. Categories may overlap. */
public class SummarizeRuntimeJfr {
    public static void main(String[] args) throws Exception {
        for (String file : args) {
            long samples = 0;
            Map<String, Long> cpu = new LinkedHashMap<>();
            Map<String, Long> allocation = new LinkedHashMap<>();
            try (RecordingFile recording = new RecordingFile(Path.of(file))) {
                while (recording.hasMoreEvents()) {
                    RecordedEvent event = recording.readEvent();
                    boolean execution = event.getEventType().getName().equals("jdk.ExecutionSample");
                    boolean allocated = event.getEventType().getName().equals("jdk.ObjectAllocationSample");
                    if ((!execution && !allocated) || event.getStackTrace() == null) continue;
                    if (execution) samples++;
                    StringBuilder stack = new StringBuilder();
                    for (RecordedFrame frame : event.getStackTrace().getFrames()) {
                        stack.append(frame.getMethod().getType().getName()).append('.')
                                .append(frame.getMethod().getName()).append('\n');
                    }
                    for (String category : new String[] {"ThreadLocal", "snapshotValue", "deepEquals",
                            "copySourceStates", "replaceSourceStates", "captureContext", "Pattern", "Matcher"}) {
                        if (stack.indexOf(category) >= 0) {
                            if (execution) cpu.merge(category, 1L, Long::sum);
                            if (allocated) allocation.merge(category, event.getLong("weight"), Long::sum);
                        }
                    }
                }
            }
            System.out.println(file + " execution_samples=" + samples);
            for (var entry : cpu.entrySet()) {
                System.out.printf(Locale.ROOT, "  cpu_stack %s=%d (%.2f%%)%n", entry.getKey(), entry.getValue(),
                        samples == 0 ? 0 : 100.0 * entry.getValue() / samples);
            }
            System.out.println("  sampled_allocation_weight_bytes=" + allocation);
        }
    }
}
