package com.hengshucredit.rule.server.consolelogin;

import com.hengshucredit.rule.model.dto.ConsoleThemePreference;
import com.hengshucredit.rule.server.mapper.ConsoleUserPreferenceMapper;
import com.hengshucredit.rule.server.service.ConsoleUserPreferenceService;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ConsoleThemePreferenceServiceTest {

    @Test
    public void missingPreferenceUsesTheCompleteSystemDefault() {
        ConsoleThemePreference theme = service(new HashMap<>()).load(7L);

        assertEquals(Integer.valueOf(3), theme.getSchemaVersion());
        assertEquals("LIGHT", theme.getColorScheme());
        assertEquals("PRESET", theme.getAccentMode());
        assertEquals("THEME_BLUE", theme.getAccentPreset());
        assertEquals("#2639E9", theme.getCustomSolidColor());
        assertEquals(List.of("#2639E9", "#873FF2"),
                theme.getCustomGradientColors());
        assertEquals("LINEAR", theme.getCustomGradientType());
        assertEquals(Integer.valueOf(135), theme.getCustomGradientAngle());
        assertEquals("LEFT", theme.getNavigationLayout());
        assertEquals("DARK", theme.getSidebarTheme());
        assertEquals("FLUID", theme.getContentWidth());
        assertEquals("AUTO", theme.getTableScrollMode());
        assertTrue(theme.getFixedSidebar());
        assertFalse(theme.getColorWeak());
    }

    @Test
    public void validGradientThemeIsNormalizedSavedAndReloaded() {
        Map<String, String> rows = new HashMap<>();
        ConsoleThemePreferenceService service = service(rows);
        Map<String, Object> request = validTheme();
        request.put("colorScheme", "DARK");
        request.put("accentPreset", "PEACH_PINK_GRADIENT");

        ConsoleThemePreference saved = service.save(7L, "alice", request);
        ConsoleThemePreference loaded = service.load(7L);

        assertEquals("DARK", saved.getColorScheme());
        assertEquals("PEACH_PINK_GRADIENT", loaded.getAccentPreset());
        assertTrue(rows.get("7:UI_THEME").contains("PEACH_PINK_GRADIENT"));
    }

    @Test
    public void customThreeColorRadialThemeAndTopNavigationRoundTrip() {
        Map<String, String> rows = new HashMap<>();
        ConsoleThemePreferenceService service = service(rows);
        Map<String, Object> request = validTheme();
        request.put("accentMode", "CUSTOM_GRADIENT");
        request.put("customGradientColors",
                List.of("#125DFF", "#6C3BFF", "#E94FC7"));
        request.put("customGradientType", "RADIAL");
        request.put("navigationLayout", "TOP");

        ConsoleThemePreference saved = service.save(7L, "alice", request);
        ConsoleThemePreference loaded = service.load(7L);

        assertEquals("CUSTOM_GRADIENT", saved.getAccentMode());
        assertEquals(List.of("#125DFF", "#6C3BFF", "#E94FC7"),
                loaded.getCustomGradientColors());
        assertEquals("RADIAL", loaded.getCustomGradientType());
        assertEquals("TOP", loaded.getNavigationLayout());
    }

    @Test
    public void legacyV1ThemeMigratesWithoutLosingExistingChoices() {
        Map<String, String> rows = new HashMap<>();
        rows.put("7:UI_THEME", "{\"schemaVersion\":1,"
                + "\"colorScheme\":\"DARK\","
                + "\"accentPreset\":\"NEBULA_GRADIENT\","
                + "\"sidebarTheme\":\"LIGHT\","
                + "\"contentWidth\":\"FIXED\","
                + "\"fixedSidebar\":false,\"colorWeak\":true}");

        ConsoleThemePreference loaded = service(rows).load(7L);

        assertEquals(Integer.valueOf(3), loaded.getSchemaVersion());
        assertEquals("DARK", loaded.getColorScheme());
        assertEquals("NEBULA_GRADIENT", loaded.getAccentPreset());
        assertEquals("LIGHT", loaded.getSidebarTheme());
        assertEquals("LEFT", loaded.getNavigationLayout());
    }

    @Test
    public void unknownFieldsAndUnlistedAccentPresetsAreRejected() {
        ConsoleThemePreferenceService service = service(new HashMap<>());
        Map<String, Object> invalidPreset = validTheme();
        invalidPreset.put("accentPreset", "url(evil)");
        Map<String, Object> unknownField = validTheme();
        unknownField.put("unknown", true);

        assertThrows(IllegalArgumentException.class,
                () -> service.save(7L, "alice", invalidPreset));
        assertThrows(IllegalArgumentException.class,
                () -> service.save(7L, "alice", unknownField));
    }

    @Test
    public void missingFieldsWrongTypesAndFutureVersionsAreRejected() {
        ConsoleThemePreferenceService service = service(new HashMap<>());
        Map<String, Object> missingField = validTheme();
        missingField.remove("colorWeak");
        Map<String, Object> wrongType = validTheme();
        wrongType.put("fixedSidebar", "true");
        Map<String, Object> futureVersion = validTheme();
        futureVersion.put("schemaVersion", 4);

        assertThrows(IllegalArgumentException.class,
                () -> service.save(7L, "alice", missingField));
        assertThrows(IllegalArgumentException.class,
                () -> service.save(7L, "alice", wrongType));
        assertThrows(IllegalArgumentException.class,
                () -> service.save(7L, "alice", futureVersion));
    }

    @Test
    public void invalidCustomColorsGradientShapeAndNavigationAreRejected() {
        ConsoleThemePreferenceService service = service(new HashMap<>());
        Map<String, Object> invalidColor = validTheme();
        invalidColor.put("customSolidColor", "url(evil)");
        Map<String, Object> invalidColorCount = validTheme();
        invalidColorCount.put("customGradientColors", List.of("#125DFF"));
        Map<String, Object> invalidGradient = validTheme();
        invalidGradient.put("customGradientType", "CONIC");
        Map<String, Object> invalidAngle = validTheme();
        invalidAngle.put("customGradientAngle", 361);
        Map<String, Object> invalidNavigation = validTheme();
        invalidNavigation.put("navigationLayout", "FLOATING");

        assertThrows(IllegalArgumentException.class,
                () -> service.save(7L, "alice", invalidColor));
        assertThrows(IllegalArgumentException.class,
                () -> service.save(7L, "alice", invalidColorCount));
        assertThrows(IllegalArgumentException.class,
                () -> service.save(7L, "alice", invalidGradient));
        assertThrows(IllegalArgumentException.class,
                () -> service.save(7L, "alice", invalidAngle));
        assertThrows(IllegalArgumentException.class,
                () -> service.save(7L, "alice", invalidNavigation));
    }

    @Test
    public void corruptStoredJsonFallsBackWithoutBlockingTheUser() {
        Map<String, String> rows = new HashMap<>();
        rows.put("7:UI_THEME", "not-json");

        ConsoleThemePreference loaded = service(rows).load(7L);

        assertEquals("LIGHT", loaded.getColorScheme());
        assertEquals("THEME_BLUE", loaded.getAccentPreset());
    }

    @Test
    public void tableScrollModesRoundTripAndRemainIsolatedByUser() {
        ConsoleThemePreferenceService service = service(new HashMap<>());
        for (String mode : List.of("FIXED", "AUTO")) {
            Map<String, Object> request = validTheme();
            request.put("tableScrollMode", mode);
            assertEquals(mode, service.save(7L, "alice", request).getTableScrollMode());
            assertEquals(mode, service.load(7L).getTableScrollMode());
            assertEquals("AUTO", service.load(8L).getTableScrollMode());
        }
    }

    @Test
    public void v2PreferenceKeepsAppearanceAndDefaultsToAutoHeight() {
        Map<String, String> rows = new HashMap<>();
        Map<String, Object> legacy = validTheme();
        legacy.put("schemaVersion", 2);
        legacy.remove("tableScrollMode");
        legacy.put("colorScheme", "DARK");
        legacy.put("navigationLayout", "TOP");
        rows.put("7:UI_THEME", com.alibaba.fastjson.JSON.toJSONString(legacy));

        ConsoleThemePreference loaded = service(rows).load(7L);
        assertEquals(Integer.valueOf(3), loaded.getSchemaVersion());
        assertEquals("DARK", loaded.getColorScheme());
        assertEquals("TOP", loaded.getNavigationLayout());
        assertEquals("AUTO", loaded.getTableScrollMode());
    }

    @Test
    public void invalidOrMissingTableScrollModeDoesNotOverwriteSavedPreference() {
        ConsoleThemePreferenceService service = service(new HashMap<>());
        Map<String, Object> request = validTheme();
        request.put("tableScrollMode", "FIXED");
        service.save(7L, "alice", request);
        for (Object mode : List.of("SCROLL", true, 1)) {
            request.put("tableScrollMode", mode);
            assertThrows(IllegalArgumentException.class,
                    () -> service.save(7L, "alice", request));
        }
        request.remove("tableScrollMode");
        assertThrows(IllegalArgumentException.class,
                () -> service.save(7L, "alice", request));
        assertEquals("FIXED", service.load(7L).getTableScrollMode());
    }

    private static Map<String, Object> validTheme() {
        Map<String, Object> value = new HashMap<>();
        value.put("schemaVersion", 3);
        value.put("colorScheme", "LIGHT");
        value.put("accentMode", "PRESET");
        value.put("accentPreset", "THEME_BLUE");
        value.put("customSolidColor", "#2639E9");
        value.put("customGradientColors", List.of("#2639E9", "#873FF2"));
        value.put("customGradientType", "LINEAR");
        value.put("customGradientAngle", 135);
        value.put("navigationLayout", "LEFT");
        value.put("sidebarTheme", "DARK");
        value.put("contentWidth", "FLUID");
        value.put("tableScrollMode", "AUTO");
        value.put("fixedSidebar", true);
        value.put("colorWeak", false);
        return value;
    }

    private static ConsoleThemePreferenceService service(
            Map<String, String> rows) {
        ConsoleUserPreferenceMapper mapper =
                (ConsoleUserPreferenceMapper) Proxy.newProxyInstance(
                        ConsoleUserPreferenceMapper.class.getClassLoader(),
                        new Class<?>[]{ConsoleUserPreferenceMapper.class},
                        (proxy, method, args) -> {
                            if ("findValue".equals(method.getName())) {
                                return rows.get(key(args[0], args[1]));
                            }
                            if ("upsertValue".equals(method.getName())) {
                                rows.put(key(args[0], args[1]), (String) args[2]);
                                return 1;
                            }
                            if ("deleteValue".equals(method.getName())) {
                                return rows.remove(key(args[0], args[1])) == null
                                        ? 0 : 1;
                            }
                            if ("toString".equals(method.getName())) {
                                return "InMemoryConsoleUserPreferenceMapper";
                            }
                            throw new UnsupportedOperationException(method.getName());
                        });
        return new ConsoleThemePreferenceService(
                new ConsoleUserPreferenceService(mapper));
    }

    private static String key(Object userId, Object preferenceKey) {
        return userId + ":" + preferenceKey;
    }
}
