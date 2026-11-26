package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.server.artifact.Sha256Digests;
import com.mysql.cj.conf.PropertyKey;
import com.mysql.cj.jdbc.JdbcConnection;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/** Shared immutable files. Normal-size model originals occupy one compressed BLOB, independently of network read size. */
@Component
public final class MysqlModelBlobStore implements SharedFileStore {
    private static final int PACKET_OVERHEAD = 256;
    private static final int READ_WINDOW = 8 * 1024 * 1024;
    private final JdbcTemplate db;
    @org.springframework.beans.factory.annotation.Value("${rule-engine.model.write-payload-limit:0}")
    private long writePayloadLimit;
    private final TransactionTemplate write;
    private final TransactionTemplate read;

    public MysqlModelBlobStore(JdbcTemplate db, PlatformTransactionManager transactions) {
        this.db = db;
        this.write = new TransactionTemplate(transactions);
        // Like atomic file publication, bytes are readable by worker connections before a model is activated.
        write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read = new TransactionTemplate(transactions);
        read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setReadOnly(true);
    }

    public boolean contains(String ref) {
        return Boolean.TRUE.equals(read.execute(tx -> db.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM rule_file_reference WHERE file_ref=?)", Boolean.class, ref)));
    }

    public void put(String ref, String digest, byte[] data, boolean archive) {
        put(ref, digest, data, archive, java.util.Map.of());
    }

    void put(String ref, String digest, byte[] data, boolean archive, java.util.Map<String, String> modelDigests) {
        if (data.length == 0 || !Sha256Digests.bytes(data).equals(digest)) throw new IllegalArgumentException("共享文件为空或摘要不匹配");
        put(ref, digest, data.length, archive, sink -> {
            if (archive) writeArchive(data, sink, modelDigests);
            else deflate(data, sink);
        });
    }

    void putArchive(String ref, String digest, java.nio.file.Path archive, int length, java.util.Map<String, String> modelDigests) {
        put(ref, digest, length, true, sink -> {
            try (var zip = new ZipFile(archive.toFile()); var file = new java.io.RandomAccessFile(archive.toFile(), "r")) {
                if (file.length() != length) throw new IOException("发布制品长度已变化");
                MessageDigest archiveDigest = sha256();
                archiveSegments(zip, length, modelDigests, (start, end, original) -> {
                    byte[] segment = new byte[end - start];
                    file.seek(start);
                    file.readFully(segment);
                    archiveDigest.update(segment);
                    if (original == null || !sink.reuseOriginal(original, segment, 0, segment.length))
                        sink.append(segment, 0, segment.length);
                });
                if (!HexFormat.of().formatHex(archiveDigest.digest()).equals(digest)) throw new IOException("发布制品摘要已变化");
            }
        });
    }

    @FunctionalInterface private interface WriteParts { void write(PayloadWriter writer) throws IOException; }

    private void put(String ref, String digest, int length, boolean archive, WriteParts parts) {
        write.executeWithoutResult(tx -> {
            db.update("INSERT INTO rule_file_object(digest,byte_size,encoding) VALUES(?,?,?) ON DUPLICATE KEY UPDATE digest=rule_file_object.digest",
                    digest,length,archive ? "IDENTITY" : "DEFLATE");
            var header = db.queryForMap("SELECT part_count,byte_size FROM rule_file_object WHERE digest=? FOR UPDATE", digest);
            if (((Number)header.get("byte_size")).longValue()!=length) throw new IllegalStateException("共享文件摘要与长度不匹配");
            if (((Number)header.get("part_count")).intValue()==0) {
                long packet = db.queryForObject("SELECT @@session.max_allowed_packet", Long.class);
                long budget = packet - PACKET_OVERHEAD;
                if (writePayloadLimit > 0) budget = Math.min(budget, writePayloadLimit);
                PayloadWriter sink = new PayloadWriter(digest, Math.toIntExact(budget));
                try {
                    parts.write(sink);
                } catch (IOException e) { throw new IllegalStateException("无法写入共享文件",e); }
                db.update("UPDATE rule_file_object SET stored_size=?,part_count=? WHERE digest=?",sink.total,sink.count,digest);
            }
            db.update("INSERT INTO rule_file_reference(file_ref,object_digest) VALUES(?,?) ON DUPLICATE KEY UPDATE file_ref=rule_file_reference.file_ref",ref,digest);
            if (!digest.equals(db.queryForObject("SELECT object_digest FROM rule_file_reference WHERE file_ref=? FOR UPDATE",String.class,ref)))
                throw new IllegalStateException("共享文件标识与原始摘要冲突");
        });
    }

    private void deflate(byte[] data, PayloadWriter sink) throws IOException {
        // ZIP's raw DEFLATE level matches DecisionArtifactPackageCodec. Model payload blocks can be shared with frozen ZIPs.
        if (data.length >= 8 * 1024 * 1024) {
            var path = java.nio.file.Files.createTempFile("tianshu-compressed-", ".deflate");
            try {
                Deflater codec = new Deflater(9, true);
                try (var stream = java.nio.file.Files.newOutputStream(path);
                     var compressed = new DeflaterOutputStream(stream, codec, 64 * 1024)) {
                    compressed.write(data);
                } finally { codec.end(); }
                sink.append(path);
            } finally { java.nio.file.Files.deleteIfExists(path); }
            return;
        }
        Deflater codec = new Deflater(9,true);
        var compressed = new ByteArrayOutputStream(Math.min(data.length, 65536));
        try (var out = new DeflaterOutputStream(compressed,codec,64*1024)) { out.write(data); }
        finally { codec.end(); }
        byte[] payload = compressed.toByteArray();
        sink.append(payload, 0, payload.length);
    }

    private void writeArchive(byte[] bytes, PayloadWriter sink, java.util.Map<String, String> modelDigests) throws IOException {
        try (var zip = new ZipFile(new SeekableInMemoryByteChannel(bytes))) {
            archiveSegments(zip, bytes.length, modelDigests, (start, end, original) -> {
                if (original == null || !sink.reuseOriginal(original, bytes, start, end))
                    sink.append(bytes, start, end-start);
            });
        }
    }

    @FunctionalInterface private interface SegmentWriter { void write(int start, int end, String original) throws IOException; }

    private void archiveSegments(ZipFile zip, int length, java.util.Map<String,String> modelDigests, SegmentWriter writer) throws IOException {
        // Keep every original ZIP byte, including metadata and signatures. Split at embedded model boundaries
        // so an identical compressed payload reuses the same chunks even if other components change.
        SortedSet<Integer> boundaries = new TreeSet<>();
        var originalAt = new java.util.HashMap<Integer, String>();
        var entries = zip.getEntriesInPhysicalOrder();
        while (entries.hasMoreElements()) {
            var entry = entries.nextElement();
            String name = entry.getName().toLowerCase(Locale.ROOT);
            if (!name.endsWith(".pmml") && !name.endsWith(".onnx")) continue;
            long start = entry.getDataOffset(), size = entry.getCompressedSize();
            if (start<0 || size<0 || start>length || size>length-start) throw new IOException("制品中的模型数据范围无效");
            if (modelDigests.containsKey(entry.getName())) originalAt.put((int)start, modelDigests.get(entry.getName()));
            boundaries.add((int)start);
            boundaries.add((int)(start+size));
        }
        boundaries.add(length);
        int start = 0;
        for (int end : boundaries) {
            writer.write(start, end, originalAt.get(start));
            start = end;
        }
    }

    private final class PayloadWriter {
        private final String object;
        private final int maxPayload;
        private int count;
        private long total;

        PayloadWriter(String object, int maxPayload) {
            this.object = object;
            this.maxPayload = maxPayload;
            if (maxPayload <= 0) throw new IllegalStateException("MySQL 包大小不足以保存文件元数据");
        }

        void append(byte[] bytes, int offset, int length) {
            // One whole compressed model per payload unless the server's actual packet limit requires splitting.
            while (length > 0) {
                int size = Math.min(maxPayload, length);
                save(bytes, offset, size);
                length -= size;
                offset += size;
            }
        }

        // An earlier node may have used a smaller packet budget. Reuse its actual chunks
        // only after matching the exact compressed bytes now present in this ZIP entry.
        private boolean reuseOriginal(String digest, byte[] bytes, int start, int end) {
            var chunks = db.query("""
                    SELECT c.digest,OCTET_LENGTH(c.content) FROM rule_file_object_part p
                    JOIN rule_file_chunk c ON c.digest=p.chunk_digest
                    WHERE p.object_digest=? ORDER BY p.part_no
                    """, (rs,n) -> new Payload(rs.getString(1), rs.getLong(2)), digest);
            int position = start;
            for (Payload chunk : chunks) {
                if (chunk.size <= 0 || chunk.size > end - position) return false;
                MessageDigest md = sha256();
                md.update(bytes, position, (int)chunk.size);
                if (!HexFormat.of().formatHex(md.digest()).equals(chunk.digest)) return false;
                position += (int)chunk.size;
            }
            if (position != end || chunks.isEmpty()) return false;
            for (Payload chunk : chunks) link(chunk.digest, (int)chunk.size);
            return true;
        }

        void append(java.nio.file.Path file) throws IOException {
            long length = java.nio.file.Files.size(file);
            byte[] buffer = new byte[65536];
            try (var stream = java.nio.file.Files.newInputStream(file);
                 var payload = java.nio.channels.FileChannel.open(file, java.nio.file.StandardOpenOption.READ)) {
                for (long offset = 0; offset < length;) {
                    int size = (int)Math.min(maxPayload, length - offset);
                    var digest = sha256();
                    for (int remaining = size; remaining > 0;) {
                        int n = stream.read(buffer, 0, Math.min(remaining, buffer.length));
                        if (n <= 0) throw new IOException("压缩模型文件不完整");
                        digest.update(buffer, 0, n);
                        remaining -= n;
                    }
                    final long start = offset;
                    save(HexFormat.of().formatHex(digest.digest()), size, () ->
                            new org.apache.commons.compress.utils.BoundedSeekableByteChannelInputStream(start, size, payload));
                    offset += size;
                }
            }
        }

        @FunctionalInterface private interface ChunkStream { InputStream open() throws IOException; }

        private void save(byte[] bytes, int offset, int size) {
            var md = sha256();
            md.update(bytes, offset, size);
            save(HexFormat.of().formatHex(md.digest()), size, () -> new ByteArrayInputStream(bytes, offset, size));
        }

        private void save(String hash, int size, ChunkStream source) {
            // Server-side binary parameters stream through the driver without building a giant escaped SQL string.
            if (!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM rule_file_chunk WHERE digest=?)", Boolean.class, hash))) {
                db.execute((ConnectionCallback<Void>) connection -> {
                    try (var ps = connection.unwrap(JdbcConnection.class).serverPrepareStatement(
                            "INSERT INTO rule_file_chunk(digest,content) VALUES(?,?) ON DUPLICATE KEY UPDATE digest=rule_file_chunk.digest"); var input = source.open()) {
                        ps.setString(1,hash);
                        ps.setBinaryStream(2,input,size);
                        ps.executeUpdate();
                    } catch (IOException error) { throw new java.sql.SQLException("读取模型分片失败", error); }
                    return null;
                });
            }
            link(hash, size);
        }

        private void link(String hash, int size) {
            db.update("INSERT INTO rule_file_object_part(object_digest,part_no,chunk_digest) VALUES(?,?,?)",object,count,hash);
            count++;
            total += size;
        }
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private record Header(String digest,String encoding,long size,int parts,long stored) {}
    private record Payload(String digest, long size) {}

    public byte[] read(String ref,String expected,long limit) {
        // The caller can hold a REPEATABLE READ snapshot older than the object just installed by put().
        return read.execute(tx -> readCommittedObject(ref, expected, limit));
    }

    private byte[] readCommittedObject(String ref,String expected,long limit) {
        var rows=db.query("SELECT o.digest,o.encoding,o.byte_size,o.part_count,o.stored_size FROM rule_file_reference r JOIN rule_file_object o ON o.digest=r.object_digest WHERE r.file_ref=?",
                (rs,n)->new Header(rs.getString(1),rs.getString(2),rs.getLong(3),rs.getInt(4),rs.getLong(5)),ref);
        if (rows.size()!=1) throw new IllegalStateException("共享模型文件缺失：" + ref);
        Header header = rows.get(0);
        if (!header.digest.equals(expected) || header.size <= 0 || header.size > limit
                || header.size > Integer.MAX_VALUE - 8 || header.parts <= 0 || header.stored <= 0) {
            throw new IllegalStateException("共享文件的长度或摘要无效");
        }
        var source = new StoredPayloadInputStream(header);
        Inflater inflater = "DEFLATE".equals(header.encoding) ? new Inflater(true) : null;
        if (inflater == null && !"IDENTITY".equals(header.encoding)) throw new IllegalStateException("未知的共享文件编码");
        try (InputStream in = inflater == null ? source : new InflaterInputStream(source, inflater, 64 * 1024)) {
            byte[] result = new byte[(int) header.size];
            int length = in.readNBytes(result, 0, result.length);
            if (length != result.length || in.read() != -1 || source.read() != -1 || source.bytesFetched != header.stored
                    || (inflater != null && (!inflater.finished() || inflater.getRemaining() != 0))
                    || !Sha256Digests.bytes(result).equals(expected)) {
                throw new IOException("文件内容与记录的长度或摘要不匹配");
            }
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("共享文件读取校验失败：" + ref, e);
        } finally {
            if (inflater != null) inflater.end();
        }
    }

    /** Small SUBSTRING transfers also let a node with a smaller JDBC packet budget read an existing large BLOB. */
    private final class StoredPayloadInputStream extends InputStream {
        private final Header header;
        private final int window;
        private int next;
        private long bytesFetched;
        private long position;
        private Payload payload;
        private MessageDigest md;
        private ByteArrayInputStream buffer = new ByteArrayInputStream(new byte[0]);

        StoredPayloadInputStream(Header header) {
            this.header = header;
            int driverLimit = db.execute((ConnectionCallback<Integer>) connection ->
                    connection.unwrap(JdbcConnection.class).getPropertySet()
                            .getIntegerProperty(PropertyKey.maxAllowedPacket).getValue());
            this.window = Math.min(READ_WINDOW, driverLimit - PACKET_OVERHEAD);
            if (window <= 0) throw new IllegalStateException("MySQL 读取包预算不足");
        }

        private boolean availableBytes() throws IOException {
            if (buffer.available() > 0) return true;
            if (payload == null || position == payload.size) {
                if (next >= header.parts) return false;
                var rows = db.query("""
                        SELECT c.digest,OCTET_LENGTH(c.content) FROM rule_file_object_part p
                        JOIN rule_file_chunk c ON c.digest=p.chunk_digest WHERE p.object_digest=? AND p.part_no=?
                        """,(rs,n)->new Payload(rs.getString(1),rs.getLong(2)),header.digest,next++);
                if (rows.size()!=1) throw new EOFException("共享文件缺少二进制记录");
                payload = rows.get(0);
                if (payload.size<=0 || payload.size>header.stored-bytesFetched)
                    throw new IOException("共享文件的存储长度不匹配");
                position = 0;
                md = sha256();
            }
            int size = (int)Math.min(window,payload.size-position);
            byte[] slice = db.queryForObject("SELECT SUBSTRING(content,?,?) FROM rule_file_chunk WHERE digest=?",
                    byte[].class,position+1,size,payload.digest);
            if (slice == null || slice.length!=size) throw new EOFException("共享文件传输不完整");
            md.update(slice);
            position += size;
            bytesFetched += size;
            if (position==payload.size && !HexFormat.of().formatHex(md.digest()).equals(payload.digest))
                throw new IOException("共享文件二进制记录摘要不匹配");
            buffer = new ByteArrayInputStream(slice);
            return true;
        }

        @Override public int read() throws IOException {return availableBytes()?buffer.read():-1;}
        @Override public int read(byte[] b,int off,int len) throws IOException {
            if (len==0) return 0;
            return availableBytes()?buffer.read(b,off,len):-1;
        }
    }
}
