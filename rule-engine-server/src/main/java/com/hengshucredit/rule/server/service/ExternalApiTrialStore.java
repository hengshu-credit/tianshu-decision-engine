package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.server.auth.CredentialCipher;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** 外数草稿试运行的跨节点状态；快照不随同一requestKey重写。 */
@Service
public class ExternalApiTrialStore {
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.hengshucredit.rule.server.security.ConsoleDataPermissionHandler consoleDataScope;

    private String secured(String sql) { return consoleDataScope == null ? sql : consoleDataScope.filterSql(sql); }

    private static final String DB_NOW = "CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000 AS UNSIGNED)";
    private static final String COLUMNS = "id, trial_id, owner, request_key, datasource_id, snapshot_digest, snapshot_json, "
            + "status, attempt_no, lease_owner, lease_until_ms, expires_at_ms, pending_json, result_json, error_message, created_at_ms, updated_at_ms";
    private static final Set<String> FINISH_STATUSES = Set.of("WAITING", "SUCCEEDED", "FAILED", "UNKNOWN");
    @Resource private JdbcTemplate jdbcTemplate;
    @Resource private CredentialCipher credentialCipher;
    @Resource private PlatformTransactionManager transactionManager;

    public State create(String owner, String requestKey, Long datasourceId, String digest, String snapshotJson, int retentionSeconds) {
        byte[] ownerKey = ownerKey(owner);
        String key = uuid(requestKey, "试运行请求标识");
        if (datasourceId == null || datasourceId <= 0) throw new IllegalArgumentException("试运行必须指定正式数据源ID");
        if (digest == null || !digest.matches("[0-9a-fA-F]{64}")) throw new IllegalArgumentException("试运行快照摘要无效");
        if (snapshotJson == null || snapshotJson.isBlank()) throw new IllegalArgumentException("试运行快照不能为空");
        long retentionMillis = positiveMillis(retentionSeconds);
        return independent(() -> {
            try {
                int inserted = jdbcTemplate.update("INSERT INTO rule_external_api_trial "
                                + "(trial_id, owner, request_key, datasource_id, snapshot_digest, snapshot_json, status, attempt_no, "
                                + "expires_at_ms, created_at_ms, updated_at_ms) VALUES (?, ?, ?, ?, ?, ?, 'CREATED', 0, "
                                + DB_NOW + " + ?, " + DB_NOW + ", " + DB_NOW + ")",
                        UUID.randomUUID().toString(), ownerKey, key, datasourceId, digest,
                        credentialCipher.encrypt(snapshotJson), retentionMillis);
                if (inserted != 1) throw new IllegalStateException("试运行状态未成功创建");
            } catch (DuplicateKeyException duplicate) {
                // 同一owner+requestKey只能读取既有事实，不刷新TTL、摘要或快照。
                if (selectOne("owner = ? AND request_key = ?", ownerKey, key) == null) throw duplicate;
            }
            expire("owner = ? AND request_key = ?", ownerKey, key);
            State state = selectOne("owner = ? AND request_key = ?", ownerKey, key);
            if (state == null) throw new IllegalStateException("无法读取已接受的试运行状态");
            return state;
        });
    }
    public State findByRequestKey(String owner, String requestKey) {
        byte[] ownerKey = ownerKey(owner); String key = uuid(requestKey, "试运行请求标识");
        return independent(() -> { expire("owner = ? AND request_key = ?", ownerKey, key); return selectOne("owner = ? AND request_key = ?", ownerKey, key); });
    }
    public State get(String trialId, String owner) {
        byte[] ownerKey = ownerKey(owner); String id = uuid(trialId, "试运行ID");
        return independent(() -> { expire("trial_id = ? AND owner = ?", id, ownerKey); return selectOne("trial_id = ? AND owner = ?", id, ownerKey); });
    }
    public List<State> list(String owner, int limit) {
        byte[] ownerKey = ownerKey(owner); int count = Math.max(1, Math.min(20, limit));
        return independent(() -> {
            expire("owner = ?", ownerKey);
            return scopedQuery("SELECT " + COLUMNS + " FROM rule_external_api_trial WHERE owner = ? ORDER BY created_at_ms DESC, id DESC LIMIT ?", mapper(), ownerKey, count);
        });
    }
    public boolean claim(String trialId, String owner, String leaseToken, int leaseSeconds) {
        String id = uuid(trialId, "试运行ID"), token = uuid(leaseToken, "执行租约令牌"); byte[] ownerKey = ownerKey(owner);
        long leaseMillis = positiveMillis(leaseSeconds);
        return independent(() -> {
            expire("trial_id = ? AND owner = ?", id, ownerKey);
            return jdbcTemplate.update("UPDATE rule_external_api_trial SET status = 'RUNNING', attempt_no = attempt_no + 1, "
                            + "lease_owner = ?, lease_until_ms = LEAST(expires_at_ms, " + DB_NOW + " + ?), updated_at_ms = " + DB_NOW
                            + " WHERE trial_id = ? AND owner = ? AND status IN ('CREATED', 'WAITING') AND expires_at_ms > " + DB_NOW,
                    token, leaseMillis, id, ownerKey) == 1;
        });
    }
    public boolean finish(String trialId, String leaseToken, String status, String pendingJson, String resultJson, String errorMessage) {
        String id = uuid(trialId, "试运行ID"), token = uuid(leaseToken, "执行租约令牌");
        if (!FINISH_STATUSES.contains(status == null ? "" : status)) throw new IllegalArgumentException("不支持的试运行完成状态");
        return independent(() -> {
            expire("trial_id = ? AND lease_owner = ?", id, token);
            return jdbcTemplate.update("UPDATE rule_external_api_trial SET status = ?, pending_json = ?, result_json = ?, error_message = ?, "
                            + "lease_owner = NULL, lease_until_ms = NULL, updated_at_ms = " + DB_NOW + runningWhere(),
                    status, credentialCipher.encrypt(pendingJson), credentialCipher.encrypt(resultJson), shortError(errorMessage), id, token) == 1;
        });
    }
    public boolean checkpoint(String trialId, String leaseToken, String pendingJson) {
        String id = uuid(trialId, "试运行ID"), token = uuid(leaseToken, "执行租约令牌");
        return independent(() -> {
            expire("trial_id = ? AND lease_owner = ?", id, token);
            return jdbcTemplate.update("UPDATE rule_external_api_trial SET pending_json = ?, updated_at_ms = " + DB_NOW + runningWhere(),
                    credentialCipher.encrypt(pendingJson), id, token) == 1;
        });
    }
    public boolean isRunning(String trialId, String leaseToken) {
        String id = uuid(trialId, "试运行ID"), token = uuid(leaseToken, "执行租约令牌");
        return independent(() -> {
            expire("trial_id = ? AND lease_owner = ?", id, token);
            return !scopedQuery("SELECT 1 FROM rule_external_api_trial" + runningWhere(), (rs, row) -> true, id, token).isEmpty();
        });
    }
    public State cancel(String trialId, String owner) {
        String id = uuid(trialId, "试运行ID"); byte[] ownerKey = ownerKey(owner);
        return independent(() -> {
            expire("trial_id = ? AND owner = ?", id, ownerKey);
            jdbcTemplate.update("UPDATE rule_external_api_trial SET status = 'CANCELLED', lease_owner = NULL, lease_until_ms = NULL, "
                            + "updated_at_ms = " + DB_NOW + " WHERE trial_id = ? AND owner = ? AND status IN ('CREATED', 'RUNNING', 'WAITING')"
                            + " AND expires_at_ms > " + DB_NOW + " AND (status <> 'RUNNING' OR lease_until_ms > " + DB_NOW + ")",
                    id, ownerKey);
            expire("trial_id = ? AND owner = ?", id, ownerKey);
            State state = selectOne("trial_id = ? AND owner = ?", id, ownerKey);
            if (state != null && Set.of("CREATED", "RUNNING", "WAITING").contains(state.status())) {
                throw new IllegalStateException("试运行取消状态未确认，请重试");
            }
            return state;
        });
    }

    private String runningWhere() {
        return " WHERE trial_id = ? AND lease_owner = ? AND status = 'RUNNING' AND lease_until_ms > " + DB_NOW + " AND expires_at_ms > " + DB_NOW;
    }
    private void expire(String predicate, Object... args) {
        // 先使用旧status写原因，再转状态（MySQL单表SET按从左到右求值）。RUNNING优先UNKNOWN，不得重发。
        jdbcTemplate.update("UPDATE rule_external_api_trial SET error_message = CASE WHEN status = 'RUNNING' "
                        + "THEN '执行租约或会话已到期，外部结果不确定，请核对后重新创建试运行' ELSE '试运行已过期，请重新创建' END, "
                        + "status = CASE WHEN status = 'RUNNING' THEN 'UNKNOWN' ELSE 'EXPIRED' END, "
                        + "lease_owner = NULL, lease_until_ms = NULL, updated_at_ms = " + DB_NOW + " WHERE (" + predicate + ") AND "
                        + "((status = 'RUNNING' AND (lease_until_ms IS NULL OR lease_until_ms <= " + DB_NOW + " OR expires_at_ms <= " + DB_NOW + ")) "
                        + "OR (status IN ('CREATED', 'WAITING') AND expires_at_ms <= " + DB_NOW + "))", args);
    }
    private State selectOne(String predicate, Object... args) {
        List<State> states = scopedQuery("SELECT " + COLUMNS + " FROM rule_external_api_trial WHERE " + predicate + " LIMIT 1", mapper(), args);
        return states.isEmpty() ? null : states.get(0);
    }
    private RowMapper<State> mapper() {
        return (rs, row) -> new State(rs.getLong("id"), rs.getString("trial_id"), new String(rs.getBytes("owner"), StandardCharsets.UTF_8),
                rs.getString("request_key"), rs.getObject("datasource_id", Long.class), rs.getString("snapshot_digest"),
                credentialCipher.decrypt(rs.getString("snapshot_json")), rs.getString("status"), rs.getInt("attempt_no"), rs.getString("lease_owner"),
                rs.getObject("lease_until_ms", Long.class), rs.getLong("expires_at_ms"), credentialCipher.decrypt(rs.getString("pending_json")),
                credentialCipher.decrypt(rs.getString("result_json")), rs.getString("error_message"), rs.getLong("created_at_ms"), rs.getLong("updated_at_ms"));
    }
    private <T> T independent(Supplier<T> action) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return transaction.execute(status -> action.get());
    }
    private byte[] ownerKey(String owner) {
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("试运行必须具有当前用户身份");
        byte[] value = owner.getBytes(StandardCharsets.UTF_8);
        if (value.length > 512) throw new IllegalArgumentException("试运行用户身份过长");
        return value;
    }
    private String uuid(String value, String label) {
        if (value == null || !value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new IllegalArgumentException(label + "必须是UUID");
        }
        return UUID.fromString(value).toString();
    }
    private long positiveMillis(int seconds) {
        if (seconds <= 0) throw new IllegalArgumentException("试运行期限必须为正数");
        return seconds * 1000L;
    }
    private String shortError(String value) {
        if (value == null || value.codePointCount(0, value.length()) <= 2048) return value;
        return value.substring(0, value.offsetByCodePoints(0, 2048));
    }

    public record State(long id, String trialId, String owner, String requestKey, Long datasourceId,
                        String snapshotDigest, String snapshotJson, String status, int attemptNo,
                        String leaseOwner, Long leaseUntil, long expiresAt, String pendingJson,
                        String resultJson, String errorMessage, long createdAt, long updatedAt) { }

    private <T> java.util.List<T> scopedQuery(String sql, org.springframework.jdbc.core.RowMapper<T> mapper, Object... parameters) {
        return jdbcTemplate.query(secured(sql), mapper, parameters);
    }
}
