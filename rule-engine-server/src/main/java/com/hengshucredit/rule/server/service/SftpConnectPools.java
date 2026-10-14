package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.entity.RuleSftpDatasource;
import com.hengshucredit.rule.server.mapper.RuleSftpDatasourceMapper;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * 基于 JSch ChannelSftp 的轻量连接池。Session/Channel 均只被一个借用者使用，
 * 读取完成后归还池中；数据源更新或删除时由 refresh 立即关闭旧池。
 */
@Service
public class SftpConnectPools implements DisposableBean {
    static final int MAX_SAFE_FILE_SIZE_BYTES = 256 * 1024 * 1024;
    @Resource
    private RuleSftpDatasourceMapper datasourceMapper;

    private final ConcurrentMap<Long, SftpPoolHolder> pools = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public byte[] readFile(Long datasourceId, String remotePath) throws Exception {
        return readFileWithRetry(datasourceId, remotePath, 2, 200, 2D).content();
    }

    public ReadResult readFileWithRetry(Long datasourceId, String remotePath, int retryCount,
                                        long backoffMs, double backoffMultiplier) throws Exception {
        int retries = Math.max(0, Math.min(5, retryCount));
        long delay = Math.max(0, Math.min(60000, backoffMs));
        double multiplier = Math.max(1D, Math.min(5D, backoffMultiplier));
        Exception last = null;
        for (int attempt = 0; attempt <= retries; attempt++) {
            try {
                RequestDeadlineContext.check();
                return new ReadResult(readFileOnce(datasourceId, remotePath), attempt);
            } catch (Exception failure) {
                last = failure;
                if (attempt >= retries || !isRetryable(failure)) throw failure;
                RequestDeadlineContext.check();
                if (delay > 0) {
                    long sleepMs = Math.min(delay, Math.max(1, RequestDeadlineContext.remainingMillis()));
                    try {
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw interrupted;
                    }
                }
                delay = Math.min(60000, Math.max(delay + 1, (long) Math.ceil(delay * multiplier)));
            }
        }
        throw last == null ? new IllegalStateException("SFTP文件读取失败") : last;
    }

    protected byte[] readFileOnce(Long datasourceId, String remotePath) throws Exception {
        if (datasourceId == null) throw new IllegalArgumentException("SFTP数据源ID不能为空");
        SftpPoolHolder holder = pool(datasourceId);
        String path = remotePath(remotePath, holder.datasource);
        PooledChannel pooled = holder.borrow();
        boolean reusable = true;
        try {
            ChannelSftp channel = pooled.channel;
            SftpATTRSInfo stat = stat(channel, path);
            int maxBytes = holder.datasource.getMaxFileSizeBytes();
            if (stat.size > maxBytes) {
                throw new IllegalArgumentException("SFTP文件超过单文件读取上限：" + maxBytes + " 字节");
            }
            try (InputStream input = channel.get(path)) {
                return readLimited(input, maxBytes);
            }
        } catch (Exception e) {
            reusable = false;
            throw e;
        } finally {
            holder.release(pooled, reusable);
        }
    }

    private boolean isRetryable(Throwable failure) {
        if (failure instanceof IllegalArgumentException || failure instanceof RequestDeadlineContext.DeadlineTimeoutException
                || failure instanceof java.util.concurrent.TimeoutException) return false;
        if (failure instanceof SftpException sftp) {
            return sftp.id != ChannelSftp.SSH_FX_NO_SUCH_FILE
                    && sftp.id != ChannelSftp.SSH_FX_PERMISSION_DENIED;
        }
        return failure instanceof SocketTimeoutException || failure instanceof IOException
                || failure instanceof JSchException || failure instanceof IllegalStateException;
    }

    public void testConnection(RuleSftpDatasource datasource) throws Exception {
        RuleSftpDatasource effective = normalized(datasource);
        PooledChannel pooled = createChannel(effective);
        try {
            pooled.channel.pwd();
        } finally {
            closeQuietly(pooled);
        }
    }

    public void refresh(Long datasourceId) {
        if (datasourceId == null) return;
        SftpPoolHolder holder = pools.remove(datasourceId);
        if (holder != null) holder.close();
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Map<String, Object>> details = new LinkedHashMap<>();
        long active = 0;
        long idle = 0;
        long pending = 0;
        for (Map.Entry<Long, SftpPoolHolder> entry : pools.entrySet()) {
            SftpPoolHolder holder = entry.getValue();
            Map<String, Object> item = holder.snapshot();
            details.put(String.valueOf(entry.getKey()), item);
            active += ((Number) item.get("active")).longValue();
            idle += ((Number) item.get("idle")).longValue();
            pending += ((Number) item.get("pending")).longValue();
        }
        result.put("poolCount", details.size());
        result.put("active", active);
        result.put("idle", idle);
        result.put("pending", pending);
        result.put("pools", details);
        return result;
    }

    public RuleSftpDatasource datasource(Long id) {
        RuleSftpDatasource datasource = datasourceMapper.selectById(id);
        if (datasource == null) throw new IllegalArgumentException("SFTP数据源不存在");
        if (!Integer.valueOf(1).equals(datasource.getStatus())) {
            throw new IllegalArgumentException("SFTP数据源未启用");
        }
        return normalized(datasource);
    }

    /** 返回已加载到连接池的配置，避免每次文件读取都额外查询数据库。 */
    public RuleSftpDatasource connectionConfig(Long id) throws Exception {
        return pool(id).datasource;
    }

    /** 仅查看本节点已缓存的配置，日志写入失败时不触发新的建池或数据库访问。 */
    public RuleSftpDatasource cachedDatasource(Long id) {
        SftpPoolHolder holder = id == null ? null : pools.get(id);
        return holder == null || holder.closed ? null : holder.datasource;
    }

    public String remotePath(String path, RuleSftpDatasource datasource) {
        if (path == null || path.trim().isEmpty()) throw new IllegalArgumentException("SFTP远端文件路径不能为空");
        String value = path.trim().replace('\\', '/');
        if (value.indexOf('\0') >= 0) throw new IllegalArgumentException("SFTP远端文件路径非法");
        String base = datasource == null ? "" : nullToEmpty(datasource.getBasePath()).replace('\\', '/');
        if (!base.isEmpty() && !value.startsWith("/")) value = base + "/" + value;
        String[] parts = value.split("/");
        List<String> clean = new ArrayList<>();
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part)) continue;
            if ("..".equals(part)) throw new IllegalArgumentException("SFTP路径不能包含上级目录");
            clean.add(part);
        }
        return (value.startsWith("/") ? "/" : "") + String.join("/", clean);
    }

    private SftpPoolHolder pool(Long id) throws Exception {
        if (closed) throw new IllegalStateException("SFTP连接池已关闭");
        SftpPoolHolder current = pools.get(id);
        if (current != null && !current.closed) return current;
        synchronized (pools) {
            current = pools.get(id);
            if (current != null && !current.closed) return current;
            RuleSftpDatasource datasource = datasource(id);
            current = new SftpPoolHolder(datasource);
            pools.put(id, current);
            current.ensureMinIdle();
            return current;
        }
    }

    private PooledChannel createChannel(RuleSftpDatasource datasource) throws Exception {
        JSch jsch = new JSch();
        if (hasText(datasource.getKnownHosts())) {
            jsch.setKnownHosts(new ByteArrayInputStream(datasource.getKnownHosts().getBytes(StandardCharsets.UTF_8)));
        }
        if (hasText(datasource.getPrivateKey())) {
            byte[] key = datasource.getPrivateKey().getBytes(StandardCharsets.UTF_8);
            if (hasText(datasource.getPassphrase())) {
                jsch.addIdentity("rule-sftp-" + datasource.getDatasourceCode(), key, null,
                        datasource.getPassphrase().getBytes(StandardCharsets.UTF_8));
            } else {
                jsch.addIdentity("rule-sftp-" + datasource.getDatasourceCode(), key, null, null);
            }
        }
        Session session = null;
        ChannelSftp channel = null;
        try {
            session = jsch.getSession(datasource.getUsername(), datasource.getHost(), datasource.getPort());
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", Boolean.TRUE.equals(datasource.getStrictHostKeyChecking()) ? "yes" : "no");
            config.putAll(extraConfig(datasource.getExtraConfig()));
            session.setConfig(config);
            if (!hasText(datasource.getPrivateKey()) && hasText(datasource.getPassword())) session.setPassword(datasource.getPassword());
            session.setTimeout(datasource.getSocketTimeoutMs());
            session.connect(datasource.getConnectTimeoutMs());
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(datasource.getSessionTimeoutMs());
            return new PooledChannel(session, channel);
        } catch (Exception failure) {
            if (channel != null) {
                try { channel.disconnect(); } catch (RuntimeException ignored) { }
            }
            if (session != null) {
                try { session.disconnect(); } catch (RuntimeException ignored) { }
            }
            throw failure;
        }
    }

    private RuleSftpDatasource normalized(RuleSftpDatasource source) {
        RuleSftpDatasource value = source == null ? new RuleSftpDatasource() : source;
        if (value.getPort() == null || value.getPort() <= 0) value.setPort(22);
        if (value.getConnectTimeoutMs() == null || value.getConnectTimeoutMs() <= 0) value.setConnectTimeoutMs(10000);
        if (value.getSessionTimeoutMs() == null || value.getSessionTimeoutMs() <= 0) value.setSessionTimeoutMs(30000);
        if (value.getSocketTimeoutMs() == null || value.getSocketTimeoutMs() <= 0) value.setSocketTimeoutMs(30000);
        if (value.getMaxPoolSize() == null || value.getMaxPoolSize() <= 0) value.setMaxPoolSize(8);
        if (value.getMinIdle() == null || value.getMinIdle() < 0) value.setMinIdle(1);
        if (value.getMinIdle() > value.getMaxPoolSize()) value.setMinIdle(value.getMaxPoolSize());
        if (value.getBorrowTimeoutMs() == null || value.getBorrowTimeoutMs() <= 0) value.setBorrowTimeoutMs(5000);
        if (value.getIdleTimeoutMs() == null || value.getIdleTimeoutMs() <= 0) value.setIdleTimeoutMs(600000);
        if (value.getMaxFileSizeBytes() == null || value.getMaxFileSizeBytes() <= 0) value.setMaxFileSizeBytes(10 * 1024 * 1024);
        if (value.getMaxFileSizeBytes() > MAX_SAFE_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("SFTP单文件读取上限不能超过 " + MAX_SAFE_FILE_SIZE_BYTES + " 字节");
        }
        if (!hasText(value.getFileNameEncoding())) value.setFileNameEncoding("UTF-8");
        return value;
    }

    private Map<String, String> extraConfig(String json) {
        if (!hasText(json)) return Collections.emptyMap();
        try {
            Map<String, Object> values = JSON.parseObject(json);
            Map<String, String> result = new LinkedHashMap<>();
            if (values != null) values.forEach((key, value) -> {
                if (key != null && value != null) result.put(key, String.valueOf(value));
            });
            return result;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("SFTP扩展配置必须是合法 JSON", e);
        }
    }

    private SftpATTRSInfo stat(ChannelSftp channel, String path) throws Exception {
        com.jcraft.jsch.SftpATTRS stat = channel.stat(path);
        return new SftpATTRSInfo(stat.getSize());
    }

    private byte[] readLimited(InputStream input, int maxBytes) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) >= 0) {
            if (count == 0) continue;
            total += count;
            if (total > maxBytes) throw new IllegalArgumentException("SFTP文件超过单文件读取上限：" + maxBytes + " 字节");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static void closeQuietly(PooledChannel pooled) {
        if (pooled == null) return;
        try { pooled.channel.disconnect(); } catch (RuntimeException ignored) { }
        try { pooled.session.disconnect(); } catch (RuntimeException ignored) { }
    }

    private static boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
    private static String nullToEmpty(String value) { return value == null ? "" : value.trim(); }

    @Override
    public void destroy() {
        closed = true;
        pools.values().forEach(SftpPoolHolder::close);
        pools.clear();
    }

    private final class SftpPoolHolder {
        private final RuleSftpDatasource datasource;
        private final java.util.concurrent.BlockingQueue<PooledChannel> idle = new java.util.concurrent.LinkedBlockingQueue<>();
        private int total;
        private int active;
        private int waiting;
        private volatile boolean closed;

        private SftpPoolHolder(RuleSftpDatasource datasource) { this.datasource = datasource; }

        private synchronized void ensureMinIdle() throws Exception {
            while (!closed && total < datasource.getMinIdle()) {
                idle.offer(createChannel(datasource));
                total++;
            }
        }

        private PooledChannel borrow() throws Exception {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(datasource.getBorrowTimeoutMs());
            while (true) {
                PooledChannel pooled = idle.poll();
                if (pooled != null) {
                    synchronized (this) { active++; }
                    if (isExpired(pooled)) { synchronized (this) { active--; } discard(pooled); continue; }
                    return pooled;
                }
                synchronized (this) {
                    if (closed) throw new IllegalStateException("SFTP连接池已关闭");
                    if (total < datasource.getMaxPoolSize()) {
                        PooledChannel created = createChannel(datasource);
                        total++;
                        active++;
                        return created;
                    }
                }
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new IllegalStateException("SFTP连接池借用超时");
                synchronized (this) { waiting++; }
                PooledChannel waited;
                try {
                    waited = idle.poll(remaining, TimeUnit.NANOSECONDS);
                } finally {
                    synchronized (this) { waiting = Math.max(0, waiting - 1); }
                }
                if (waited != null) {
                    synchronized (this) { active++; }
                    if (isExpired(waited)) { synchronized (this) { active--; } discard(waited); continue; }
                    return waited;
                }
            }
        }

        private void release(PooledChannel pooled, boolean reusable) {
            synchronized (this) {
                active = Math.max(0, active - 1);
                if (reusable && !closed && !isExpired(pooled) && isConnected(pooled)) {
                    pooled.lastUsed = System.currentTimeMillis();
                    idle.offer(pooled);
                    return;
                }
                total = Math.max(0, total - 1);
            }
            closeQuietly(pooled);
        }

        private synchronized void discard(PooledChannel pooled) {
            if (pooled == null) return;
            total = Math.max(0, total - 1);
            closeQuietly(pooled);
        }

        private boolean isExpired(PooledChannel pooled) {
            return pooled == null || System.currentTimeMillis() - pooled.lastUsed > datasource.getIdleTimeoutMs();
        }

        private boolean isConnected(PooledChannel pooled) {
            return pooled != null && pooled.session.isConnected() && pooled.channel.isConnected();
        }

        private synchronized Map<String, Object> snapshot() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("active", active);
            result.put("idle", idle.size());
            result.put("total", total);
            result.put("pending", waiting);
            result.put("maxPoolSize", datasource.getMaxPoolSize());
            return result;
        }

        private synchronized void close() {
            if (closed) return;
            closed = true;
            List<PooledChannel> channels = new ArrayList<>();
            idle.drainTo(channels);
            channels.forEach(SftpConnectPools::closeQuietly);
            total = active;
        }
    }

    private static final class PooledChannel {
        private final Session session;
        private final ChannelSftp channel;
        private long lastUsed = System.currentTimeMillis();

        private PooledChannel(Session session, ChannelSftp channel) {
            this.session = session;
            this.channel = channel;
        }
    }

    private record SftpATTRSInfo(long size) { }

    public record ReadResult(byte[] content, int retryCount) { }
}
