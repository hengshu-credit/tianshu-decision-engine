package com.hengshucredit.rule.server.system;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;

@Service
public class SystemBrandAssetService {
    private final JdbcTemplate jdbc;
    private final SystemSettingsService settings;
    public SystemBrandAssetService(JdbcTemplate jdbc, SystemSettingsService settings) { this.jdbc = jdbc; this.settings = settings; }

    public String upload(byte[] bytes, String actor) throws IOException {
        if (bytes.length == 0 || bytes.length > 1024 * 1024) throw new IllegalArgumentException("图片大小应在 1 MB 以内");
        byte[] png;
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("请选择有效的 PNG 或 JPG 图片");
            ImageReader reader = readers.next();
            try {
                if (!Set.of("png", "jpeg", "jpg").contains(reader.getFormatName().toLowerCase(java.util.Locale.ROOT))) throw new IllegalArgumentException("只支持 PNG、JPG 图片");
                reader.setInput(input);
                if (reader.getWidth(0) > 2048 || reader.getHeight(0) > 2048) throw new IllegalArgumentException("图片宽高不能超过 2048 像素");
                var output = new ByteArrayOutputStream();
                ImageIO.write(reader.read(0), "png", output); png = output.toByteArray();
                if (png.length > 4 * 1024 * 1024) throw new IllegalArgumentException("图片解码后过大，请缩小图片");
            } finally { reader.dispose(); }
        }
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO rule_system_asset (id, content_type, content, create_by) VALUES (?, 'image/png', ?, ?)", id, png, actor);
        return id;
    }

    public byte[] publicAsset(String id) {
        Map<String, Object> brand = settings.get("brand");
        if (!id.equals(brand.get("logoId")) && !id.equals(brand.get("faviconId"))) throw new IllegalArgumentException("品牌图片不存在");
        return load(id);
    }
    public byte[] logo() {
        Object id = settings.get("brand").get("logoId");
        if (id != null && !id.toString().isEmpty()) return load(id.toString());
        try (var stream = getClass().getResourceAsStream("/brand/tianshu-logo.png")) {
            if (stream == null) throw new IllegalStateException("内置品牌图片缺失");
            return stream.readAllBytes();
        } catch (IOException error) { throw new IllegalStateException("品牌图片读取失败", error); }
    }
    private byte[] load(String id) {
        var rows = jdbc.query("SELECT content FROM rule_system_asset WHERE id = ?", (rs, index) -> rs.getBytes(1), id);
        if (rows.isEmpty()) throw new IllegalArgumentException("品牌图片不存在");
        return rows.get(0);
    }
}
