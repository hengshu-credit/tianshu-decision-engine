package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.server.artifact.Sha256Digests;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;

/** Bounded local copies of immutable database files too large for the heap cache. */
final class VerifiedFileCache implements AutoCloseable {
    private static final int BLOCK_BYTES = 64 * 1024;
    private record Entry(FileChannel file, int size) { }
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private long bytes;

    synchronized byte[] read(String key, String digest) {
        Entry entry = entries.get(key);
        if (entry == null) return null;
        try {
            byte[] result = new byte[entry.size()];
            ByteBuffer buffer = ByteBuffer.wrap(result);
            // FileChannel stages heap buffers through per-thread direct memory. Limit each
            // transfer, including writes, independently of the size of the frozen model.
            while (buffer.position() < result.length) {
                buffer.limit(buffer.position() + Math.min(BLOCK_BYTES,result.length - buffer.position()));
                if (entry.file().read(buffer, buffer.position()) <= 0) break;
            }
            if (buffer.position() == result.length && Sha256Digests.bytes(result).equals(digest)) return result;
        } catch (IOException unavailable) {
            // The authoritative object can still be read from MySQL.
        }
        remove(key);
        return null;
    }

    synchronized boolean verifies(String key, String digest, int length) {
        Entry entry = entries.get(key);
        if (entry == null) return false;
        try {
            var hash = java.security.MessageDigest.getInstance("SHA-256");
            ByteBuffer block = ByteBuffer.allocate(BLOCK_BYTES);
            long pos = 0;
            if (entry.size() == length && entry.file().size() == length) {
                while (pos < length) {
                    block.clear();
                    int count = entry.file().read(block, pos);
                    if (count <= 0) break;
                    pos += count;
                    block.flip();
                    hash.update(block);
                }
                if (pos == length && java.util.HexFormat.of().formatHex(hash.digest()).equals(digest)) return true;
            }
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        } catch (IOException unavailable) {
            // Evict the local copy; the caller verifies the database object instead.
        }
        remove(key);
        return false;
    }

    synchronized void put(String key, byte[] content, long limit) {
        if (content.length == 0 || content.length > limit || entries.containsKey(key)) return;
        while (entries.size() >= 64 || !entries.isEmpty() && bytes > limit - content.length) remove(entries.keySet().iterator().next());
        Path path = null;
        FileChannel file = null;
        try {
            path = Files.createTempFile("tianshu-verified-", ".cache");
            file = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.DELETE_ON_CLOSE);
            var buffer = ByteBuffer.wrap(content);
            while (buffer.position() < content.length) {
                buffer.limit(buffer.position() + Math.min(BLOCK_BYTES,content.length - buffer.position()));
                if (file.write(buffer) <= 0) throw new IOException("模型缓存写入未前进");
            }
            entries.put(key, new Entry(file, content.length));
            bytes += content.length;
            file = null; // Ownership passes to the LRU until eviction or shutdown.
        } catch (IOException unavailable) {
            // A full or read-only temporary directory only disables this cache write.
        } finally {
            if (file != null) close(file);
            if (path != null && !entries.containsKey(key)) {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            }
        }
    }

    private void remove(String key) {
        Entry entry = entries.remove(key);
        bytes -= entry.size();
        close(entry.file());
    }

    private static void close(FileChannel file) {
        try { file.close(); } catch (IOException ignored) { }
    }

    @Override public synchronized void close() {
        entries.values().forEach(entry -> close(entry.file()));
        entries.clear();
        bytes = 0;
    }
}
