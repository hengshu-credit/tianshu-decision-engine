package com.hengshucredit.rule.server.sftp;

import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.digest.BuiltinDigests;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.security.PublicKey;
import java.util.regex.Pattern;

@Service
public final class EngineSftpAccounts {
    private static final Pattern LOGIN = Pattern.compile("u([1-9][0-9]{0,17})(?:-p([1-9][0-9]{0,17}))?");
    private final JdbcTemplate db;

    EngineSftpAccounts(JdbcTemplate db) {
        this.db = db;
    }

    public void configure(long account, String publicKey, boolean disable) {
        if (disable) {
            if (publicKey != null && !publicKey.isBlank()) throw new IllegalArgumentException("不能同时更换公钥和关闭 SFTP");
            db.update("DELETE FROM engine_sftp_account WHERE account_id=?", account);
            return;
        }
        if (publicKey == null || publicKey.isBlank()) return;
        if (publicKey.length() > 8192 || publicKey.strip().lines().count() != 1)
            throw new IllegalArgumentException("请填写单行 OpenSSH 公钥");
        String fingerprint;
        try {
            var entry = AuthorizedKeyEntry.parseAuthorizedKeyEntry(publicKey.trim());
            if (entry == null || !entry.getLoginOptions().isEmpty()) throw new IllegalArgumentException();
            PublicKey key = entry.resolvePublicKey(null, PublicKeyEntryResolver.FAILING);
            fingerprint = KeyUtils.getFingerPrint(BuiltinDigests.sha256, key);
        } catch (Exception e) { throw new IllegalArgumentException("不支持的 SSH 公钥"); }
        db.update("INSERT INTO engine_sftp_account(account_id,key_fingerprint) VALUES(?,?) ON DUPLICATE KEY UPDATE key_fingerprint=?",
                account, fingerprint, fingerprint);
    }

    public enum AccessMode { READ_WRITE, READ_ONLY, SUSPENDED }
    public record Account(long id, String username, String displayName, boolean active, AccessMode mode, long revision) {}
    record Identity(long user, Long project, String fingerprint, long version, AccessMode mode) {}

    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<Account> list(String search, int page, int size) {
        int current=Math.max(1,page), limit=Math.max(1,Math.min(100,size));
        String pattern="%" + (search == null ? "" : search.trim()) + "%";
        String where=" FROM console_user u LEFT JOIN engine_sftp_account s ON s.account_id=u.id "
                + "WHERE u.merged_into_user_id IS NULL AND (u.username LIKE ? OR u.display_name LIKE ?)";
        long count=db.queryForObject("SELECT COUNT(*)"+where,Long.class,pattern,pattern);
        var rows=db.query("SELECT u.id,u.username,u.display_name,u.status,u.registration_status,s.access_mode,u.permission_version"+where
                +" ORDER BY u.id LIMIT ? OFFSET ?", (r,i)->new Account(r.getLong(1),r.getString(2),r.getString(3),
                r.getInt(4)==1 && "ACTIVE".equals(r.getString(5)),r.getString(6)==null?null:AccessMode.valueOf(r.getString(6)),r.getLong(7)),
                pattern,pattern,limit,(long)(current-1)*limit);
        return new com.baomidou.mybatisplus.extension.plugins.pagination.Page<Account>(current,limit,count).setRecords(rows);
    }

    public void setMode(long id, AccessMode mode) {
        if (mode == null) throw new IllegalArgumentException("请选择 SFTP 账户权限");
        if (!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM engine_sftp_account WHERE account_id=?)",Boolean.class,id)))
            throw new IllegalArgumentException("首次开通请填写上传方的公钥");
        db.update("UPDATE engine_sftp_account SET access_mode=? WHERE account_id=?", mode.name(), id);
    }

    static String subject(String login) {
        var match=LOGIN.matcher(login);
        return match.matches() ? "USER:" + match.group(1) : null;
    }

    Identity authenticate(String login, PublicKey key) {
        var match = LOGIN.matcher(login);
        if (!match.matches()) return null;
        long user = Long.parseLong(match.group(1));
        Long project = match.group(2) == null ? null : Long.valueOf(match.group(2));
        var rows = db.query("SELECT s.key_fingerprint,u.permission_version,s.access_mode FROM engine_sftp_account s JOIN console_user u ON u.id=s.account_id "
                        + "WHERE u.id=? AND u.status=1 AND u.registration_status='ACTIVE' AND u.merged_into_user_id IS NULL",
                (rs, i) -> new Identity(user, project, rs.getString(1), rs.getLong(2), AccessMode.valueOf(rs.getString(3))), user);
        if (rows.isEmpty() || rows.get(0).mode() == AccessMode.SUSPENDED || !rows.get(0).fingerprint().equals(KeyUtils.getFingerPrint(BuiltinDigests.sha256, key))) return null;
        if (project != null && !Boolean.TRUE.equals(db.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM rule_project p JOIN console_user u ON u.id=? WHERE p.id=? "
                        + "AND (u.project_access_mode='ALL' OR EXISTS(SELECT 1 FROM console_user_project a "
                        + "WHERE a.user_id=u.id AND a.project_id=p.id)))", Boolean.class, user, project))) return null;
        return rows.get(0);
    }

    boolean active(Identity identity) {
        return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM engine_sftp_account s JOIN console_user u ON u.id=s.account_id "
                + "WHERE u.id=? AND u.status=1 AND u.registration_status='ACTIVE' AND u.merged_into_user_id IS NULL AND u.permission_version=? AND s.key_fingerprint=? AND s.access_mode=?)",
                Boolean.class, identity.user(), identity.version(), identity.fingerprint(), identity.mode().name()));
    }
}
