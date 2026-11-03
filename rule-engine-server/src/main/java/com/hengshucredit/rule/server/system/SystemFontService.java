package com.hengshucredit.rule.server.system;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class SystemFontService {
    public static final Map<String, Object> DEFAULT = Map.of("source", "DEFAULT", "family", "", "assetId", "", "face", 0, "instance", 0);
    private final JdbcTemplate jdbc;
    private final Map<String, java.util.List<EngineFontFile.Face>> metadata = new LinkedHashMap<>();
    public SystemFontService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String, Object> upload(byte[] bytes, String actor) {
        EngineFontFile font = new EngineFontFile(bytes); font.validate();
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO rule_system_asset (id, content_type, content, create_by) VALUES (?, 'font/sfnt', ?, ?)", id, bytes, actor);
        return Map.of("id", id, "faces", faces(id));
    }
    public Map<String, Object> metadata(String id) { return Map.of("id", id, "faces", faces(id)); }
    private synchronized java.util.List<EngineFontFile.Face> faces(String id) {
        // Immutable asset IDs; keep only small metadata, never the uploaded binary, in this bounded cache.
        if (!metadata.containsKey(id)) {
            var value = load(id).faces();
            if (metadata.size() >= 32) metadata.remove(metadata.keySet().iterator().next());
            metadata.put(id, value);
        }
        return metadata.get(id);
    }
    public byte[] face(String id, int face) { return load(id).face(face); }
    private EngineFontFile load(String id) {
        var rows = jdbc.query("SELECT content FROM rule_system_asset WHERE id = ? AND content_type = 'font/sfnt'", (rs, index) -> rs.getBytes(1), id);
        if (rows.isEmpty()) throw new IllegalArgumentException("字体资源不存在，请重新上传");
        return new EngineFontFile(rows.get(0));
    }
    public Map<String, Object> resolve(Object raw) {
        if (!(raw instanceof Map<?, ?> value) || !value.keySet().equals(DEFAULT.keySet())) throw new IllegalArgumentException("字体配置无效");
        if (!(value.get("source") instanceof String source) || !Set.of("DEFAULT", "LOCAL", "UPLOAD").contains(source)
                || !(value.get("family") instanceof String family) || family.length() > 256 || family.chars().anyMatch(Character::isISOControl)
                || !(value.get("assetId") instanceof String assetId)) throw new IllegalArgumentException("字体配置无效");
        long face = SystemSettingsService.number(value.get("face"), "子字体"), instance = SystemSettingsService.number(value.get("instance"), "字体样式");
        Map<String, Object> result = new LinkedHashMap<>();
        DEFAULT.keySet().forEach(key -> result.put(key, value.get(key)));
        if (source.equals("UPLOAD")) {
            if (!assetId.matches("[a-f0-9-]{36}") || !family.isEmpty()) throw new IllegalArgumentException("字体资源引用无效");
            var faces = faces(assetId);
            if (face >= faces.size() || instance >= faces.get((int) face).instances().size()) throw new IllegalArgumentException("请选择有效的子字体和样式");
            result.put("coordinates", faces.get((int) face).instances().get((int) instance).coordinates());
        } else {
            if (!assetId.isEmpty() || face != 0 || instance != 0 || (source.equals("LOCAL") ? family.isBlank() : !family.isEmpty())) throw new IllegalArgumentException("字体来源与配置不一致");
            result.put("coordinates", Map.of());
        }
        return result;
    }
}
