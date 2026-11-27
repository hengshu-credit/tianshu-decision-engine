package com.hengshucredit.rule.server.transfer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hengshucredit.rule.model.dto.ProjectAuthSaveRequest;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.security.ConsoleProjectAccess;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 由部署管理员按子包完整摘要挂载；既不从上传内容加载，也不进入响应、审批摘要和迁移日志。 */
@Service
public class TransferInstallationBindings {
    private final Path directory;
    private final ConsoleProjectAccess access;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String,Set<String>> FIELDS = Map.of(
            "DATABASE", Set.of("host", "port", "jdbcUrl", "username", "password"),
            "SFTP", Set.of("host", "port", "username", "password", "knownHosts"),
            "EXTERNAL_DATASOURCE", Set.of("baseUrl", "authConfig"),
            "EXTERNAL_API", Set.of("authApiConfig"));

    public TransferInstallationBindings(@Value("${rule-engine.transfer.bindings-directory:/run/secrets/tianshu-transfer}") String directory,
                                        ConsoleProjectAccess access) {
        this.directory = Path.of(directory);
        this.access = access;
    }

    Profile load(String digest) {
        if (!digest.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("子包摘要无效");
        Path file = directory.resolve(digest + ".json");
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return Profile.EMPTY;
        access.requirePermission("system:edit", 0L);
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > 1024 * 1024)
                throw new IOException();
            Map<String, Map<String,Object>> rows = JSON.readValue(Files.readAllBytes(file), new TypeReference<>() {});
            return new Profile(Map.copyOf(rows));
        } catch (IOException | RuntimeException invalid) {
            throw new IllegalArgumentException("本次安装的迁移凭据配置无效，请核对挂载文件");
        }
    }

    static final class Profile {
        static final Profile EMPTY = new Profile(Map.of());
        private final Map<String,Map<String,Object>> rows;
        Profile(Map<String,Map<String,Object>> rows) { this.rows = rows; }
        boolean configured() { return !rows.isEmpty(); }
        void validateKeys(Set<String> keys) {
            if (!keys.containsAll(rows.keySet())) throw new IllegalArgumentException("安装级绑定包含不属于本子包的资源");
        }
        void bind(TransferBundle.Resource resource, Map<String,Object> configuration) {
            var values = rows.get(resource.key());
            if (values == null) return;
            var type = TransferKey.parse(resource.key()).type();
            if (type == TransferResourceType.PROJECT) return;
            var allowed = FIELDS.getOrDefault(type.name(), Set.of());
            for (var entry : values.entrySet()) {
                String field = entry.getKey();
                if (allowed.contains(field)) {
                    Object value = entry.getValue();
                    configuration.put(field, value instanceof Map<?,?> ? CanonicalJson.write(value) : value);
                } else if (type == TransferResourceType.EXTERNAL_API && field.matches("/executionConfig/steps/[0-9]+/callback/signatureSecret")
                        && resource.requiredEnvironmentFields().contains(field)) {
                    Object execution = configuration.get("executionConfig");
                    boolean encoded = execution instanceof String;
                    var config = encoded ? CanonicalJson.readMap((String) execution) : execution;
                    if (!(config instanceof Map<?,?> plan) || !(plan.get("steps") instanceof List<?> steps)
                            || !(steps.get(Integer.parseInt(field.split("/")[3])) instanceof Map<?,?> step)
                            || !(step.get("callback") instanceof Map<?,?> callback))
                        throw new IllegalArgumentException("回调凭据配置与迁移包不匹配");
                    @SuppressWarnings("unchecked") var writable = (Map<String,Object>) callback;
                    writable.put("signatureSecret", entry.getValue());
                    if (encoded) configuration.put("executionConfig", CanonicalJson.write(config));
                } else throw new IllegalArgumentException("安装级凭据配置含不支持的字段：" + field);
            }
        }

        void bindAuth(String projectKey, ProjectAuthSaveRequest request) {
            var rowsForProject = rows.getOrDefault(projectKey, Map.of());
            Object config = rowsForProject.get(request.getAuthCode());
            if (config == null) return;
            if (!(config instanceof Map<?,?> credentials) || !Set.of("identifier", "secret").containsAll(credentials.keySet()))
                throw new IllegalArgumentException("安装级项目鉴权配置无效");
            if (credentials.get("identifier") instanceof String id) request.setIdentifier(id);
            if (credentials.get("secret") instanceof String secret) request.setSecret(secret);
        }
    }
}
