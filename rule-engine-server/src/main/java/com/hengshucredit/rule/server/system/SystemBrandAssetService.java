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
    public record StatementBrand(String name, String subtitle, byte[] logo) {
        public StatementBrand {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("请在系统基本信息中填写系统名称");
            if (logo == null || logo.length == 0) throw new IllegalArgumentException("系统 Logo 不能为空");
        }
    }

    public StatementBrand statementBrand() {
        Map<String, Object> brand = settings.get("brand");
        String url = (String) brand.get("logoUrl");
        String id = (String) brand.get("logoId");
        byte[] image = id != null && !id.isBlank() ? load(id)
                : url != null && !url.isBlank() ? configuredLogo(url) : defaultLogo();
        return new StatementBrand((String) brand.get("systemName"), (String) brand.get("subtitle"), image);
    }

    // 地址来自已审批的系统品牌配置。发送接口不接受图片地址或附件内容。
    private byte[] configuredLogo(String url) {
        SystemSettingsService.validateImageUrl(url);
        java.net.URI uri = java.net.URI.create(url);
        if (!uri.isAbsolute()) {
            String base = (String) settings.access().get("baseUrl");
            if (base == null || base.isBlank()) throw new IllegalArgumentException("使用本站 Logo 路径时，请先配置控制台访问地址");
            uri = java.net.URI.create(base).resolve(uri);
        }
        try {
            var http = (java.net.HttpURLConnection) uri.toURL().openConnection();
            http.setInstanceFollowRedirects(false);
            http.setConnectTimeout(5000);
            http.setReadTimeout(5000);
            try {
                if (http.getResponseCode() != 200) throw new IOException("HTTP " + http.getResponseCode());
                try (var input = http.getInputStream()) {
                    return imagePng(input.readNBytes(4 * 1024 * 1024 + 1));
                }
            } finally {
                http.disconnect();
            }
        } catch (IOException error) {
            throw new IllegalArgumentException("无法读取系统 Logo，请检查已生效的图片地址或上传 Logo", error);
        }
    }

    static byte[] imagePng(byte[] bytes) throws IOException {
        if (bytes.length == 0 || bytes.length > 4 * 1024 * 1024) throw new IOException("Logo 文件为空或过大");
        java.awt.image.BufferedImage image;
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                try {
                    reader.setInput(input);
                    if (reader.getWidth(0) > 2048 || reader.getHeight(0) > 2048) throw new IOException("Logo 超过 2048 像素");
                    image = reader.read(0);
                } finally { reader.dispose(); }
            } else {
                var loader = new com.github.weisj.jsvg.parser.SVGLoader();
                var context = com.github.weisj.jsvg.parser.LoaderContext.builder()
                        .externalResourcePolicy(com.github.weisj.jsvg.parser.resources.ResourcePolicy.DENY_ALL).build();
                var svg = loader.load(new ByteArrayInputStream(bytes), null, context);
                if (svg == null) throw new IOException("无法解码 Logo");
                var size = svg.size();
                if (!(size.width > 0 && size.height > 0)) throw new IOException("Logo 缺少有效尺寸");
                float scale = 256 / Math.max(size.width, size.height);
                int width = Math.max(1, Math.round(size.width * scale));
                int height = Math.max(1, Math.round(size.height * scale));
                image = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                var graphics = image.createGraphics();
                try {
                    svg.render(null, graphics, new com.github.weisj.jsvg.view.ViewBox(0, 0, width, height));
                } finally { graphics.dispose(); }
            }
        }
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    public byte[] logo() {
        Object id = settings.get("brand").get("logoId");
        if (id != null && !id.toString().isEmpty()) return load(id.toString());
        return defaultLogo();
    }
    private byte[] defaultLogo() {
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
