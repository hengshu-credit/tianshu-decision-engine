package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.server.artifact.Sha256Digests;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class VerifiedFileCacheTest {
    @Test public void fileLargerThanNativeMemoryBudgetRoundTripsWithoutReservingItsSize() throws Exception {
        String java = Path.of(System.getProperty("java.home"),"bin","java").toString();
        Process process = new ProcessBuilder(java,"-Xmx96m","-XX:MaxDirectMemorySize=2m","-cp",
                System.getProperty("java.class.path"),getClass().getName()).redirectErrorStream(true).start();
        try {
            assertTrue(process.waitFor(30,TimeUnit.SECONDS));
            assertEquals(new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8),0,process.exitValue());
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    public static void main(String[] args) {
        byte[] content = new byte[12*1024*1024+13];
        new Random(17).nextBytes(content);
        String digest=Sha256Digests.bytes(content);
        try (var cache=new VerifiedFileCache()) {
            cache.put("frozen",content,content.length);
            if (!cache.verifies("frozen",digest,content.length) || !Arrays.equals(content,cache.read("frozen",digest)))
                throw new AssertionError("多块及末尾不足一块的模型数据未完整读回");
            if (cache.read("frozen","wrong-digest")!=null || cache.read("frozen",digest)!=null)
                throw new AssertionError("摘要不符的本地副本未驱逐");
        }
    }
}
