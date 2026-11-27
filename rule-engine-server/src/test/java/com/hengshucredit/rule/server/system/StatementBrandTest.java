package com.hengshucredit.rule.server.system;

import com.sun.net.httpserver.HttpServer;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.*;

public class StatementBrandTest {
    @Test public void resolvesConfiguredSiteLogoOnceIntoOfflinePngAndKeepsTheSystemName() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/brand.svg", exchange -> {
            byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg' width='32' height='16'><rect width='32' height='16' fill='#336699'/></svg>"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, svg.length);
            try (var out = exchange.getResponseBody()) { out.write(svg); }
        });
        server.start();
        try {
            var settings = new SystemSettingsService(null, null, null, null, null) {
                @Override public Map<String, Object> get(String group) {
                    return Map.of("systemName", "测试品牌", "logoUrl", "/brand.svg");
                }
                @Override public Map<String, Object> access() {
                    return Map.of("baseUrl", "http://127.0.0.1:" + server.getAddress().getPort());
                }
            };
            var brand = new SystemBrandAssetService(null, settings).statementBrand();
            assertEquals("测试品牌", brand.name());
            var png = ImageIO.read(new ByteArrayInputStream(brand.logo()));
            assertEquals(png.getWidth(), 2 * png.getHeight());
            assertEquals(0xFF336699, png.getRGB(png.getWidth() / 2, png.getHeight() / 2));
        } finally { server.stop(0); }
    }
}
