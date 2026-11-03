package com.hengshucredit.rule.server.system;

import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.auth.CredentialCipher;
import com.hengshucredit.rule.server.auth.ProjectAuthProperties;
import com.hengshucredit.rule.server.consolelogin.RuleEngineConsoleLoginProperties;
import com.hengshucredit.rule.server.governance.GovernanceSecretCodec;
import com.hengshucredit.rule.server.governance.ResourceSnapshot;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import static org.junit.Assert.*;

public class SystemSettingsServiceTest {
    private JdbcTemplate jdbc;
    private SystemSettingsService service;
    private StandardEnvironment env;
    private RuleEngineConsoleLoginProperties login;
    @Before public void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:settings" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE rule_system_setting (id BIGINT PRIMARY KEY, config_json CLOB, secret_ciphertext CLOB, secret_digest VARCHAR(64), version BIGINT DEFAULT 0, update_by VARCHAR(128), update_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE rule_system_asset (id VARCHAR(36) PRIMARY KEY, content BLOB, content_type VARCHAR(64), create_by VARCHAR(128))");
        for (int i = 1; i <= 4; i++) jdbc.update("INSERT INTO rule_system_setting(id,config_json) VALUES (?, '{}')", i);
        var properties = new ProjectAuthProperties(); properties.setActiveKeyId("test");
        properties.setMasterKeys(Map.of("test", "test-only-master-key-0123456789-abcdef"));
        login = new RuleEngineConsoleLoginProperties(); env = new StandardEnvironment();
        env.getPropertySources().remove("systemEnvironment"); env.getPropertySources().remove("systemProperties");
        service = new SystemSettingsService(jdbc, new GovernanceSecretCodec(new CredentialCipher(properties)), login, env, new SystemFontService(jdbc));
    }
    @Test public void brandSaveIsVersionedAndPublicViewContainsNoOperationalSecrets() {
        Map<String, Object> brand = service.get("brand"); brand.put("systemName", "测试引擎");
        assertEquals("测试引擎", service.saveBrand(brand, "admin").get("systemName"));
        assertEquals(1L, ((Number) service.publicBrand().get("version")).longValue());
        assertThrows(IllegalArgumentException.class, () -> service.saveBrand(brand, "admin"));
        assertFalse(service.publicBrand().containsKey("password"));
        assertEquals(11, service.publicBrand().size());
    }
    @Test public void fontsValidateInstancesAndPreserveSavedConfigForLegacyClients() throws Exception {
        var fonts = new SystemFontService(jdbc);
        byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of("../rule-engine-web/public/fonts/font.ttf"));
        var upload = fonts.upload(bytes, "admin");
        String id = upload.get("id").toString();
        var controller = new SystemSettingsController(service, null, null, null, null, null, null);
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "fonts", fonts);
        assertThrows(IllegalArgumentException.class, () -> controller.publicFont(id, 0));
        assertEquals("DEFAULT", ((Map<?, ?>) service.publicBrand().get("font")).get("source"));
        var parsed = new EngineFontFile(bytes);
        assertTrue(parsed.faces().get(0).instances().stream().anyMatch(instance -> instance.name().equals("SemiBold")));
        int semiBold = java.util.stream.IntStream.range(0, parsed.faces().get(0).instances().size())
                .filter(i -> parsed.faces().get(0).instances().get(i).name().equals("SemiBold")).findFirst().orElseThrow();
        var selection = Map.of("source", "UPLOAD", "family", "", "assetId", id, "face", 0, "instance", semiBold);
        var brand = service.get("brand"); brand.put("font", selection);
        Map<?, ?> saved = (Map<?, ?>) service.saveBrand(brand, "admin").get("font");
        assertEquals(600.0, ((Map<?, ?>) saved.get("coordinates")).get("wght"));
        assertEquals(80.0, ((Map<?, ?>) saved.get("coordinates")).get("BEVL"));
        new EngineFontFile(fonts.face(id, 0)).validate();
        assertArrayEquals(bytes, controller.publicFont(id, 0).getBody());
        assertThrows(IllegalArgumentException.class, () -> controller.publicFont(id, 1));
        var legacy = service.get("brand"); legacy.remove("font"); service.saveBrand(legacy, "admin");
        assertEquals(id, ((Map<?, ?>) service.publicBrand().get("font")).get("assetId"));
        for (var invalid : java.util.List.of(Map.entry("face", -1), Map.entry("face", 10), Map.entry("instance", 500), Map.entry("instance", 1.5), Map.entry("assetId", "missing"), Map.entry("coordinates", Map.of("wght", 900)))) {
            var value = new LinkedHashMap<String, Object>(selection); value.put(invalid.getKey(), invalid.getValue());
            var edit = service.get("brand"); edit.put("font", value);
            assertThrows(IllegalArgumentException.class, () -> service.saveBrand(edit, "admin"));
        }
        var image = service.get("brand"); image.put("logoId", id);
        assertThrows(IllegalArgumentException.class, () -> service.saveBrand(image, "admin"));
        assertThrows(IllegalArgumentException.class, () -> fonts.upload("not a font".getBytes(java.nio.charset.StandardCharsets.UTF_8), "admin"));
        var local = service.get("brand"); local.put("font", Map.of("source", "LOCAL", "family", "Microsoft YaHei", "assetId", "", "face", 0, "instance", 0));
        assertEquals("Microsoft YaHei", ((Map<?, ?>) service.saveBrand(local, "admin").get("font")).get("family"));
        assertThrows(IllegalArgumentException.class, () -> controller.publicFont(id, 0));
    }

    @Test public void watermarkDefaultsOffPersistsAndSurvivesLegacyBrandEdits() {
        assertEquals(false, service.publicBrand().get("watermarkEnabled"));
        var brand = service.get("brand");
        brand.put("watermarkEnabled", true);
        assertEquals(true, service.saveBrand(brand, "admin").get("watermarkEnabled"));
        assertEquals(true, service.get("brand").get("watermarkEnabled"));
        var legacy = service.get("brand");
        legacy.remove("watermarkEnabled"); legacy.put("subtitle", "修改副标题");
        assertEquals(true, service.saveBrand(legacy, "admin").get("watermarkEnabled"));
        var invalid = service.get("brand"); invalid.put("watermarkEnabled", "false");
        assertThrows(IllegalArgumentException.class, () -> service.saveBrand(invalid, "admin"));
        invalid.put("watermarkEnabled", null);
        assertThrows(IllegalArgumentException.class, () -> service.saveBrand(invalid, "admin"));
        assertEquals(true, service.publicBrand().get("watermarkEnabled"));
        var disabled = service.get("brand"); disabled.put("watermarkEnabled", false);
        assertEquals(false, service.saveBrand(disabled, "admin").get("watermarkEnabled"));
    }
    @Test public void imageUrlsPersistAndFaviconFallsBackToCurrentLogo() {
        var brand = service.get("brand");
        brand.put("logoUrl", "https://images.example.com/logo.svg?version=2");
        var saved = service.saveBrand(brand, "admin");
        assertEquals(brand.get("logoUrl"), saved.get("logoUrl"));
        assertEquals(saved.get("logoUrl"), saved.get("faviconUrl"));
        brand = service.get("brand");
        brand.put("faviconUrl", "/images/icon.webp");
        assertEquals("/images/icon.webp", service.saveBrand(brand, "admin").get("faviconUrl"));
        for (String url : java.util.List.of("javascript:alert(1)", "data:image/svg+xml,test", "//other.example/logo.png", "https://user:secret@example.com/a.png", "https://example.com\\a.png")) {
            assertThrows(IllegalArgumentException.class, () -> SystemSettingsService.validateImageUrl(url));
        }
        var legacy = service.get("brand");
        legacy.remove("logoUrl"); legacy.remove("faviconUrl");
        assertEquals("", service.saveBrand(legacy, "admin").get("logoUrl"));
    }
    @Test public void watermarkAppearancePersistsAndLegacyEditorsPreserveAllOptions() {
        var original = service.publicBrand();
        assertEquals("{系统名称} {用户名} {登录时间}", original.get("watermarkContent"));
        assertEquals(88, original.get("watermarkTransparency"));
        assertEquals("DIAGONAL", original.get("watermarkLayout"));
        assertEquals("NORMAL", original.get("watermarkDensity"));
        var brand = service.get("brand");
        Map<String, Object> options = Map.of("watermarkEnabled", true, "watermarkContent", "内部资料\n{用户名} {登录时间}",
                "watermarkTransparency", 65, "watermarkLayout", "HORIZONTAL", "watermarkDensity", "DENSE");
        brand.putAll(options);
        var saved = service.saveBrand(brand, "admin");
        options.forEach((key, value) -> assertEquals(value, saved.get(key)));
        options.forEach((key, value) -> assertEquals(value, service.get("brand").get(key)));
        var legacy = service.get("brand");
        options.keySet().forEach(legacy::remove);
        legacy.put("systemName", "新名称");
        var updated = service.saveBrand(legacy, "admin");
        options.forEach((key, value) -> assertEquals(value, updated.get(key)));
        assertEquals("新名称", updated.get("systemName"));
    }
    @Test public void invalidWatermarkOptionsAreRejectedWithoutChangingEffectiveValues() {
        for (var invalid : java.util.List.of(
                Map.entry("watermarkContent", "  \n"), Map.entry("watermarkContent", "水".repeat(201)),
                Map.entry("watermarkTransparency", -1), Map.entry("watermarkTransparency", 101),
                Map.entry("watermarkTransparency", 12.5), Map.entry("watermarkTransparency", "88"),
                Map.entry("watermarkLayout", "INVALID"), Map.entry("watermarkDensity", "INVALID"))) {
            var brand = service.get("brand"); brand.put(invalid.getKey(), invalid.getValue());
            assertThrows(IllegalArgumentException.class, () -> service.saveBrand(brand, "admin"));
        }
        for (String key : java.util.List.of("watermarkContent", "watermarkTransparency", "watermarkLayout", "watermarkDensity")) {
            var brand = service.get("brand"); brand.put(key, null);
            assertThrows(IllegalArgumentException.class, () -> service.saveBrand(brand, "admin"));
        }
        assertEquals(0L, ((Number) service.publicBrand().get("version")).longValue());
        for (int transparency : new int[]{0, 100}) {
            var brand = service.get("brand"); brand.put("watermarkTransparency", transparency);
            assertEquals(transparency, service.saveBrand(brand, "admin").get("watermarkTransparency"));
        }
    }
    @Test public void mailPasswordSurvivesBlankEditAndNeverAppearsInReadSnapshot() {
        Map<String, Object> mail = service.get("mail"); mail.put("email", "notify@example.com"); mail.put("host", "smtp.example.com"); mail.put("password", "test-secret");
        ResourceSnapshot initial = service.normalize(ResourceSnapshot.ofJson(CanonicalJson.write(mail)));
        service.validate(initial); service.persist(initial, "admin");
        assertFalse(service.get("mail").containsKey("password"));
        assertFalse(service.snapshot(2).snapshotJson().contains("test-secret"));
        Map<String, Object> edit = new LinkedHashMap<>(service.get("mail")); edit.put("password", ""); edit.put("port", 465); edit.put("encryption", "TLS");
        Map<String, Object> draft = service.mailDraft(edit);
        assertEquals("test-secret", draft.get("password"));
        assertEquals(465, draft.get("port"));
        assertEquals(587, service.mail().get("port"));
        edit.put("password", "draft-only-secret");
        ResourceSnapshot base = service.normalize(new ResourceSnapshot(CanonicalJson.write(edit), "ACTIVE", service.snapshot(2).secretPayloadCiphertext(), service.snapshot(2).secretDigest()));
        edit.remove("password");
        assertEquals("draft-only-secret", service.mailDraft(edit, base).get("password"));
        assertEquals("test-secret", service.mail().get("password"));
    }
    @Test public void sharedTargetsAreValidButUnknownSystemCodeIsRejected() {
        Map<String, Object> codes = service.get("codes"); codes.put("enabled", true);
        codes.put("mappings", Map.of("100001", Map.of("code", "E1", "message", "参数错误"), "100002", Map.of("code", "E1", "message", "参数错误")));
        ResourceSnapshot snapshot = service.normalize(ResourceSnapshot.ofJson(CanonicalJson.write(codes)));
        service.validate(snapshot); service.persist(snapshot, "admin");
        assertEquals("E1", service.mapping().code(com.hengshucredit.rule.model.PlatformCode.PARAMETER_INVALID));
        assertEquals("E1", service.mapping().code(com.hengshucredit.rule.model.PlatformCode.REQUIRED_FIELD_MISSING));
        codes.put("mappings", Map.of("missing", Map.of("code", "E1")));
        assertThrows(IllegalArgumentException.class, () -> service.validate(service.normalize(ResourceSnapshot.ofJson(CanonicalJson.write(codes)))));
    }
    @Test public void scopeUnknownFieldsAndUnconfiguredRecoveryAreRejected() {
        var value = service.get("access");
        assertThrows(IllegalArgumentException.class, () -> service.validate(service.normalize(ResourceSnapshot.ofJson(CanonicalJson.write(value)))));
        value.put("passwordRecoveryEnabled", false); value.put("registrationEnabled", false);
        service.validate(service.normalize(ResourceSnapshot.ofJson(CanonicalJson.write(value))));
        value.put("scope", "PROJECT");
        assertThrows(IllegalArgumentException.class, () -> service.normalize(ResourceSnapshot.ofJson(CanonicalJson.write(value))));
        value.put("scope", "GLOBAL"); value.put("unknown", "value");
        assertThrows(IllegalArgumentException.class, () -> service.normalize(ResourceSnapshot.ofJson(CanonicalJson.write(value))));
    }
    @Test public void explicitDeploymentOverrideWinsAndLocksGroup() {
        var value = service.get("access"); value.put("registrationEnabled", false); value.put("passwordRecoveryEnabled", false);
        service.persist(service.normalize(ResourceSnapshot.ofJson(CanonicalJson.write(value))), "admin");
        login.setRegistrationEnabled(true);
        env.getPropertySources().addFirst(new MapPropertySource("commandLineArgs", Map.of("rule-engine.console-login.registration-enabled", "true")));
        assertTrue(service.deploymentManaged("access"));
        assertEquals(true, service.access().get("registrationEnabled"));
    }
    @Test public void uploadedImagesOnlyBecomePublicWhenReferencedBySavedBrand() throws Exception {
        var assets = new SystemBrandAssetService(jdbc, service);
        var bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", bytes);
        String id = assets.upload(bytes.toByteArray(), "admin");
        assertThrows(IllegalArgumentException.class, () -> assets.publicAsset(id));
        var brand = service.get("brand"); brand.put("logoId", id); service.saveBrand(brand, "admin");
        assertArrayEquals(bytes.toByteArray(), assets.publicAsset(id));
        assertThrows(IllegalArgumentException.class, () -> assets.upload("<svg onload='alert(1)'/>".getBytes(java.nio.charset.StandardCharsets.UTF_8), "admin"));
    }
}
