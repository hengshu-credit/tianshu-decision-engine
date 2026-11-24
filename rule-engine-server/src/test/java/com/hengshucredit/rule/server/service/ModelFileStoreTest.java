package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.server.upload.UploadScanProperties;
import com.hengshucredit.rule.server.upload.UploadSecurity;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.Assert.*;

public class ModelFileStoreTest {
    @Test public void verifiedSnapshotReusesOnlyExactStoredBytes() {
        var files = store(temp.getRoot().toPath(), "CLEAN");
        byte[] original = "<PMML version='4.4'/>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String reference = files.accept(7L, "sample.pmml", original);
        String digest = com.hengshucredit.rule.server.artifact.Sha256Digests.bytes(original);
        assertEquals(reference, files.fromVerifiedSnapshot(7L, "sample.pmml", original.length, digest,
                () -> { throw new AssertionError("不应展开第二份已验证的原件"); }));
        assertThrows(IllegalArgumentException.class, () -> files.fromVerifiedSnapshot(7L, "sample.pmml",
                original.length + 1, digest, () -> original));
    }

    @Rule public TemporaryFolder temp = new TemporaryFolder();
    static ModelFileStore store(Path root, String status) {
        UploadSecurity scanner = new UploadSecurity(new UploadScanProperties()) {
            @Override public Result scan(byte[] content) { return new Result(status, status); }
        };
        return new ModelFileStore(root.toString(), 1024 * 1024, 0, scanner);
    }

    @Test public void localFileCacheEvictsByBytesAndDiscardsCorruptHits() {
        try (var files = new VerifiedFileCache()) {
            byte[] bytes = {1, 2, 3};
            String sha = com.hengshucredit.rule.server.artifact.Sha256Digests.bytes(bytes);
            files.put("one", bytes, 3);
            assertArrayEquals(bytes, files.read("one", sha));
            files.put("two", bytes, 3);
            assertNull(files.read("one", sha));
            assertNull(files.read("two", "wrong"));
            assertNull(files.read("two", sha));
        }
    }

    @Test public void originalSurvivesConcurrentUploadsAndPinsHistoricalBytes() throws Exception {
        Path root = temp.getRoot().toPath();
        var store = store(root, "CLEAN");
        byte[] a = "<PMML version='4.4'/>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] b = "<PMML version='4.3'/>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var pool = Executors.newFixedThreadPool(2);
        String first;
        try {
            Callable<String> upload = () -> store.accept(7L, "评分卡.pmml", a);
            var done = pool.invokeAll(List.of(upload, upload));
            first = done.get(0).get();
            assertEquals(first, done.get(1).get());
        } finally { pool.shutdownNow(); }
        String second = store.accept(7L, "评分卡.pmml", b);
        assertNotEquals(first, second);
        assertArrayEquals(a, store.model(7L, "评分卡.pmml", first, null));
        assertArrayEquals(b, store.model(7L, "评分卡.pmml", second, null));
        assertArrayEquals(a, store.model(7L, "评分卡.pmml", Base64.getEncoder().encodeToString(a), null));
        var offline = store(root, "PENDING");
        assertThrows(IllegalArgumentException.class, () -> offline.accept(7L, "评分卡.pmml", a));
        assertEquals(first, offline.fromSnapshot(7L, "评分卡.pmml", a));
        assertThrows(IllegalArgumentException.class, () -> store.model(8L, "评分卡.pmml", first, null));
        Path file;
        try (var paths = Files.walk(root)) {
            file = paths.filter(Files::isRegularFile).filter(p -> first.endsWith(p.getFileName().toString())).findFirst().orElseThrow();
        }
        Files.write(file, b);
        assertThrows(IllegalArgumentException.class, () -> store.model(7L, "评分卡.pmml", first, null));
    }

    @Test public void rejectedPendingAndUnsafeXmlNeverBecomeAvailableFiles() throws Exception {
        Path root = temp.getRoot().toPath();
        for (String state : List.of("PENDING", "QUARANTINED")) {
            var store = store(root, state);
            var rejected = assertThrows(IllegalArgumentException.class, () -> store.accept(1L, "model.onnx", new byte[]{1}));
            assertTrue(rejected.getMessage().contains(state));
        }
        var clean = store(root, "CLEAN");
        assertThrows(IllegalArgumentException.class, () -> clean.accept(1L, "../bad.onnx", new byte[]{1}));
        assertThrows(IllegalArgumentException.class, () -> clean.accept(1L, "renamed.onnx", "not a model".getBytes()));
        // ModelProto -> GraphProto -> TensorProto with EXTERNAL storage and no payload.
        byte[] external = {8, 8, 58, 4, 42, 2, 112, 1};
        assertThrows(IllegalArgumentException.class, () -> clean.accept(1L, "external.onnx", external));
        assertThrows(IllegalArgumentException.class, () -> clean.accept(1L, "m.pmml", "<!DOCTYPE PMML><PMML/>".getBytes()));
        try (var paths = Files.walk(root)) { assertEquals(0L, paths.filter(Files::isRegularFile).count()); }
    }
}
