package com.hengshucredit.rule.server.sftp;

import com.hengshucredit.rule.server.security.ConsolePermissionService;
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
    private final ConsolePermissionService permissions;

    EngineSftpAccounts(JdbcTemplate db, ConsolePermissionService permissions) {
        this.db = db;
        this.permissions = permissions;
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

    record Identity(long user, Long project, String fingerprint, long version) {}

    Identity authenticate(String login, PublicKey key) {
        var match = LOGIN.matcher(login);
        if (!match.matches()) return null;
        long user = Long.parseLong(match.group(1));
        Long project = match.group(2) == null ? null : Long.valueOf(match.group(2));
        var rows = db.query("SELECT s.key_fingerprint,u.permission_version FROM engine_sftp_account s JOIN console_user u ON u.id=s.account_id "
                        + "WHERE u.id=? AND u.status=1 AND u.registration_status='ACTIVE' AND u.merged_into_user_id IS NULL",
                (rs, i) -> new Identity(user, project, rs.getString(1), rs.getLong(2)), user);
        if (rows.isEmpty() || !rows.get(0).fingerprint().equals(KeyUtils.getFingerPrint(BuiltinDigests.sha256, key))) return null;
        if (project != null && (!permissions.scope(user).allowsProject(project)
                || !Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM rule_project WHERE id=?)", Boolean.class, project)))) return null;
        return rows.get(0);
    }

    boolean active(Identity identity) {
        return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM engine_sftp_account s JOIN console_user u ON u.id=s.account_id "
                + "WHERE u.id=? AND u.status=1 AND u.registration_status='ACTIVE' AND u.merged_into_user_id IS NULL AND u.permission_version=? AND s.key_fingerprint=?)",
                Boolean.class, identity.user(), identity.version(), identity.fingerprint()));
    }
}
