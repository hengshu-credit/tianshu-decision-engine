import com.hengshucredit.rule.server.artifact.*;
import com.hengshucredit.rule.model.entity.DecisionArtifact;
import java.util.*;

public class ArtifactLoadProbe {
    public static void main(String[] args) {
        DecisionArtifactPackage pack = new DecisionArtifactPackage();
        byte[] payload = new byte[1024 * 1024];
        new Random(42).nextBytes(payload);
        pack.addComponent("assets/model.bin", "application/octet-stream", payload, Map.of("resourceType", "ATTACHMENT"));
        for (int i = 1; i <= 100; i++) {
            pack.addComponent("variables/" + i + ".json", "application/json",
                    CanonicalJson.writeBytes(Map.of("id", i, "varCode", "field" + i, "varType", "NUMBER", "varSource", "INPUT")),
                    Map.of("resourceType", "VARIABLE"));
        }
        DecisionArtifactPackageCodec codec = new DecisionArtifactPackageCodec();
        byte[] content = codec.encode(pack);
        var decoded = codec.decode(content);
        DecisionArtifact artifact = new DecisionArtifact();
        artifact.setId(1L);
        artifact.setArtifactDigest(decoded.getArtifactDigest());
        artifact.setPackageDigest(decoded.getPackageDigest());
        artifact.setPackageContent(content);
        ArtifactRuntimeSnapshotService service = new ArtifactRuntimeSnapshotService() {
            protected DecisionArtifact loadArtifact(Long id) { return artifact; }
        };
        for (int i = 0; i < 20; i++) service.load(1L, 1L, 1L);
        long[] times = new long[200];
        for (int i = 0; i < times.length; i++) {
            long start = System.nanoTime();
            var result = service.load(1L, 1L, (long) (i % 2 + 1));
            times[i] = System.nanoTime() - start;
            if (result.getVariables().size() != 100 || result.getVariables().get(0).getProjectId() != i % 2 + 1) throw new AssertionError();
        }
        double mean = Arrays.stream(times).average().orElseThrow() / 1e6;
        Arrays.sort(times);
        System.out.printf(Locale.ROOT, "package_bytes=%d samples=%d mean_ms=%.3f p50_ms=%.3f p95_ms=%.3f%n", content.length, times.length, mean, times[99]/1e6, times[189]/1e6);
    }
}
