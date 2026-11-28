package com.hengshucredit.rule.server.sftp;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** 认证计数和解封共用 MySQL 行锁。文件包频率按独立 SFTP 实例内的真实账户合并计算。 */
@Service
public class EngineSftpProtection {
    public record Policy(int ipAttemptsPerMinute, int fileRequestsPerSecond, int failureThreshold,
                         int failureWindowSeconds, int banSeconds, long revision) {
        static final Policy DEFAULT = new Policy(120, 256, 8, 300, 900, 0);
        void validate() {
            range(ipAttemptsPerMinute, 1, 60000);
            range(fileRequestsPerSecond, 1, 100000);
            range(failureThreshold, 1, 1000);
            range(failureWindowSeconds, 1, 86400);
            range(banSeconds, 1, 604800);
        }
        private static void range(int value, int min, int max) {
            if (value < min || value > max) throw new IllegalArgumentException("SFTP 策略参数超出允许范围");
        }
    }
    public record Ban(String subject, String reason, int failures, long blockedUntil) {}
    private record Window(long second, int count) {}

    private final JdbcTemplate db;
    private final TransactionTemplate transaction;
    private final LongSupplier clock;
    private final AtomicLong nextCleanup = new AtomicLong();
    private final Cache<String,Window> transfers = Caffeine.newBuilder().expireAfterAccess(Duration.ofSeconds(3)).build();
    private final Cache<Integer,Policy> filePolicy = Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(1)).build();

    @org.springframework.beans.factory.annotation.Autowired
    public EngineSftpProtection(JdbcTemplate db, PlatformTransactionManager manager) {
        this(db, manager, System::currentTimeMillis);
    }
    EngineSftpProtection(JdbcTemplate db, PlatformTransactionManager manager, LongSupplier clock) {
        this.db = db;
        this.transaction = new TransactionTemplate(manager);
        this.clock = clock;
    }

    public Policy policy() {
        var rows = db.query("SELECT * FROM engine_sftp_policy WHERE policy_id=1", (r,i) -> new Policy(
                r.getInt("ip_attempts_per_minute"), r.getInt("file_requests_per_second"), r.getInt("failure_threshold"),
                r.getInt("failure_window_seconds"), r.getInt("ban_seconds"), r.getLong("revision")));
        return rows.isEmpty() ? Policy.DEFAULT : rows.get(0);
    }

    public Policy save(Policy input) {
        input.validate();
        return transaction.execute(tx -> {
            db.update("INSERT INTO engine_sftp_policy(policy_id) VALUES(1) ON DUPLICATE KEY UPDATE policy_id=1");
            if (db.update("UPDATE engine_sftp_policy SET ip_attempts_per_minute=?,file_requests_per_second=?,failure_threshold=?,"
                            + "failure_window_seconds=?,ban_seconds=?,revision=revision+1 WHERE policy_id=1 AND revision=?",
                    input.ipAttemptsPerMinute(), input.fileRequestsPerSecond(), input.failureThreshold(),
                    input.failureWindowSeconds(), input.banSeconds(), input.revision()) != 1)
                throw new IllegalStateException("策略已被其他管理员更新，请刷新后再保存");
            return policy();
        });
    }

    private static final class State {
        final String subject;
        long rateStart, failureStart, until;
        int attempts, failures;
        String reason;
        State(ResultSet row) throws SQLException {
            subject = row.getString("subject_key");
            rateStart = row.getLong("attempt_start_ms");
            attempts = row.getInt("attempts");
            failureStart = row.getLong("failure_start_ms");
            failures = row.getInt("failures");
            until = row.getLong("blocked_until_ms");
            reason = row.getString("block_reason");
        }
        void block(String cause, long expiry) {
            reason = cause;
            until = expiry;
            org.slf4j.LoggerFactory.getLogger(EngineSftpProtection.class).warn(
                    "SFTP authentication blocked subject={} reason={} until={}", subject, cause, expiry);
        }
        void expire(long now) {
            if (until > 0 && until <= now) {
                attempts = 0;
                failures = 0;
                rateStart = now;
                failureStart = now;
                until = 0;
                reason = "";
            }
        }
        void failure(long now, Policy policy) {
            if (now - failureStart >= 1000L * policy.failureWindowSeconds()) {
                failureStart = now;
                failures = 0;
            }
            if (++failures >= policy.failureThreshold()) block("FAILURE",now+1000L*policy.banSeconds());
        }
    }

    private State lock(String key, long now) {
        db.update("INSERT INTO engine_sftp_auth_state(subject_key,last_seen_ms) VALUES(?,?) "
                + "ON DUPLICATE KEY UPDATE subject_key=engine_sftp_auth_state.subject_key", key, now);
        State state = db.queryForObject("SELECT * FROM engine_sftp_auth_state WHERE subject_key=? FOR UPDATE", (rs,i)->new State(rs), key);
        state.expire(now);
        return state;
    }
    private void store(State s, long now) {
        db.update("UPDATE engine_sftp_auth_state SET attempt_start_ms=?,attempts=?,failure_start_ms=?,failures=?,"
                + "blocked_until_ms=?,block_reason=?,last_seen_ms=? WHERE subject_key=?",
                s.rateStart,s.attempts,s.failureStart,s.failures,s.until,s.reason,now,s.subject);
    }

    boolean authenticate(String ip, String subject, BooleanSupplier credential) {
        return authenticate(ip, subject, credential, null);
    }

    boolean authenticate(String ip, String subject, BooleanSupplier credential, byte[] sessionId) {
        long before = clock.getAsLong();
        long next = nextCleanup.get();
        if (before >= next && nextCleanup.compareAndSet(next, before + 60000)) {
            db.update("DELETE FROM engine_sftp_auth_state WHERE subject_key LIKE 'PROOF:%' AND last_seen_ms<? LIMIT 1000", before - 60000);
            db.update("DELETE FROM engine_sftp_auth_state WHERE last_seen_ms<? AND blocked_until_ms<=? LIMIT 1000",
                    before - Duration.ofDays(7).toMillis(), before);
        }
        return Boolean.TRUE.equals(transaction.execute(tx -> {
            Policy p = policy();
            State source = lock("IP:" + ip, clock.getAsLong());
            long now = clock.getAsLong();
            if (source.until > now) return denied(subject, credential, sessionId, now);
            if (now - source.rateStart >= 60000) {
                source.rateStart = now;
                source.attempts = 0;
            }
            if (++source.attempts > p.ipAttemptsPerMinute()) {
                source.block("RATE", source.rateStart + 60000);
                store(source, now);
                return denied(subject, credential, sessionId, now);
            }
            // 只为真实开通的账户创建状态；同一账户的个人和各项目登录共用一个计数。
            State account = known(subject) ? lock(subject, now) : null;
            if (account != null && account.until > now) {
                store(source, now);
                return denied(subject, credential, sessionId, now);
            }
            boolean accepted = credential.getAsBoolean();
            if (!accepted) {
                source.failure(now,p);
                if (account != null) account.failure(now,p);
            }
            store(source,now);
            if (account != null) store(account,now);
            return accepted;
        }));
    }

    private boolean denied(String subject, BooleanSupplier credential, byte[] sessionId, long now) {
        // 即使输入了正确的系统服务密码也拒绝文件访问。用本次 SSH 会话的摘要给管理节点一次性回执。
        if (sessionId != null && "SERVICE".equals(subject) && credential.getAsBoolean()) {
            db.update("INSERT INTO engine_sftp_auth_state(subject_key,last_seen_ms) VALUES(?,?) "
                    + "ON DUPLICATE KEY UPDATE last_seen_ms=?", proofKey(sessionId), now, now);
        }
        return false;
    }

    boolean consumeServiceDenial(byte[] sessionId) {
        return sessionId != null && db.update("DELETE FROM engine_sftp_auth_state WHERE subject_key=? AND last_seen_ms>=?",
                proofKey(sessionId), clock.getAsLong() - 60000) == 1;
    }

    private static String proofKey(byte[] sessionId) {
        return "PROOF:" + com.hengshucredit.rule.server.artifact.Sha256Digests.bytes(sessionId);
    }

    private boolean known(String subject) {
        if (subject == null) return false;
        if ("SERVICE".equals(subject)) return true;
        // AUTH 的真实主键已经过唯一登录名查询，未知请求不会生成任意账户封禁行。
        if (subject.startsWith("AUTH:")) return true;
        return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM engine_sftp_account WHERE account_id=?)",
                Boolean.class, Long.parseLong(subject.substring("USER:".length()))));
    }

    void authenticated(String subject) {
        // SSH 公钥探测会先调用认证器。只有 SSHD 确认签名并完成认证才重置账户的失败计数。
        if (subject != null) db.update("UPDATE engine_sftp_auth_state SET failures=0,failure_start_ms=0 "
                + "WHERE subject_key=? AND blocked_until_ms=0",subject);
    }

    boolean allowFileRequest(String subject) {
        int limit = filePolicy.get(1, k -> policy()).fileRequestsPerSecond();
        long second = clock.getAsLong() / 1000;
        Window window = transfers.asMap().compute(subject, (k,old) -> old == null || old.second()!=second
                ? new Window(second,1) : new Window(second,Math.min(100001,old.count()+1)));
        return window.count() <= limit;
    }

    public Page<Ban> bans(int page, int size) {
        int limit = Math.max(1, Math.min(size,100));
        int current = Math.max(1,page);
        long now = clock.getAsLong();
        long total = db.queryForObject("SELECT COUNT(*) FROM engine_sftp_auth_state WHERE blocked_until_ms>?",Long.class,now);
        List<Ban> rows = db.query("SELECT subject_key,block_reason,failures,blocked_until_ms FROM engine_sftp_auth_state "
                + "WHERE blocked_until_ms>? ORDER BY blocked_until_ms DESC,subject_key LIMIT ? OFFSET ?",
                (r,i)->new Ban(r.getString(1),r.getString(2),r.getInt(3),r.getLong(4)), now,limit,(long)(current-1)*limit);
        return new Page<Ban>(current,limit,total).setRecords(rows);
    }

    public void unblock(String subject, long blockedUntil) {
        if (db.update("DELETE FROM engine_sftp_auth_state WHERE subject_key=? AND blocked_until_ms=? AND blocked_until_ms>?",
                subject,blockedUntil,clock.getAsLong()) != 1) throw new IllegalStateException("该封禁已到期或发生变化，请刷新列表");
    }
}
