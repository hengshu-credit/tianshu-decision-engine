package com.hengshucredit.rule.server.sftp;

import cn.hutool.crypto.digest.BCrypt;
import com.hengshucredit.rule.server.artifact.Sha256Digests;

/** 管理节点生成合作方密码摘要，独立 SFTP 节点使用摘要验证原始凭据。 */
public final class EngineSftpCredentials {
    private EngineSftpCredentials() {}

    public static String loginHash(String raw) {
        return Sha256Digests.text(raw);
    }

    public static String hashPassword(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("SFTP 密码不能为空");
        // 先摘要全部 UTF-8 字节，避免 BCrypt 截断较长的 API Key。
        return BCrypt.hashpw(Sha256Digests.text(raw));
    }

    public static boolean matchesPassword(String raw, String hash) {
        if (raw == null || raw.isBlank() || hash == null || hash.isBlank()) return false;
        try {
            return BCrypt.checkpw(Sha256Digests.text(raw), hash);
        } catch (IllegalArgumentException invalidHash) {
            return false;
        }
    }

    public static void validateLogin(String login, String serviceUsername) {
        if (login == null || login.isBlank() || !login.equals(login.strip()) || login.length() > 128
                || login.codePoints().anyMatch(Character::isISOControl)
                || login.equalsIgnoreCase(serviceUsername) || login.equalsIgnoreCase("engine-storage")
                || login.matches("(?i)u[0-9]+(-p[0-9]+)?")) {
            throw new IllegalArgumentException("SFTP 登录名无效或已保留给引擎和控制台账户");
        }
    }
}
