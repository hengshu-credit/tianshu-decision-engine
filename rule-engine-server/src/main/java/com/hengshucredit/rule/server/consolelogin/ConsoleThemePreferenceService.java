package com.hengshucredit.rule.server.consolelogin;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.model.dto.ConsoleThemePreference;
import com.hengshucredit.rule.server.service.ConsoleUserPreferenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ConsoleThemePreferenceService {

    public static final String PREFERENCE_KEY = "UI_THEME";

    private static final Logger LOGGER = LoggerFactory.getLogger(
            ConsoleThemePreferenceService.class);
    private static final Set<String> LEGACY_FIELDS = Set.of(
            "schemaVersion", "colorScheme", "accentPreset", "sidebarTheme",
            "contentWidth", "fixedSidebar", "colorWeak");
    private static final Set<String> V2_FIELDS = Set.of(
            "schemaVersion", "colorScheme", "accentMode", "accentPreset",
            "customSolidColor", "customGradientColors",
            "customGradientType", "customGradientAngle",
            "navigationLayout", "sidebarTheme", "contentWidth",
            "fixedSidebar", "colorWeak");
    private static final Set<String> FIELDS = Set.of(
            "schemaVersion", "colorScheme", "accentMode", "accentPreset",
            "customSolidColor", "customGradientColors",
            "customGradientType", "customGradientAngle",
            "navigationLayout", "sidebarTheme", "contentWidth",
            "tableScrollMode", "fixedSidebar", "colorWeak");
    private static final Set<String> TABLE_SCROLL_MODES = Set.of("AUTO", "FIXED");
    private static final Set<String> COLOR_SCHEMES = Set.of("LIGHT", "DARK");
    private static final Set<String> ACCENT_MODES = Set.of(
            "PRESET", "CUSTOM_SOLID", "CUSTOM_GRADIENT");
    private static final Set<String> GRADIENT_TYPES = Set.of(
            "LINEAR", "RADIAL");
    private static final Set<String> NAVIGATION_LAYOUTS = Set.of(
            "LEFT", "TOP");
    private static final Set<String> SIDEBAR_THEMES = Set.of("LIGHT", "DARK");
    private static final Set<String> CONTENT_WIDTHS = Set.of("FLUID", "FIXED");
    private static final Set<String> ACCENT_PRESETS = Set.of(
            "THEME_BLUE", "LIQUID_PURPLE", "PEACH_PINK", "NEON_PINK",
            "MIDNIGHT_PURPLE", "INDIGO_PURPLE", "NEBULA_BLUE", "CRYSTAL_BLUE",
            "THEME_BLUE_GRADIENT", "LIQUID_PURPLE_GRADIENT",
            "PEACH_PINK_GRADIENT", "NEBULA_GRADIENT");
    private static final Pattern HEX_COLOR = Pattern.compile(
            "^#[0-9A-Fa-f]{6}$");

    private final ConsoleUserPreferenceService preferenceService;

    public ConsoleThemePreferenceService(
            ConsoleUserPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    public ConsoleThemePreference defaults() {
        return new ConsoleThemePreference(
                3, "LIGHT", "PRESET", "THEME_BLUE", "#2639E9",
                List.of("#2639E9", "#873FF2"), "LINEAR", 135, "LEFT",
                "DARK", "FLUID", "AUTO", true, false);
    }

    public ConsoleThemePreference load(Long userId) {
        String stored = preferenceService.find(userId, PREFERENCE_KEY);
        if (stored == null || stored.isBlank()) return defaults();
        try {
            JSONObject parsed = JSON.parseObject(stored);
            if (parsed == null) return defaults();
            return validate(parsed);
        } catch (RuntimeException exception) {
            LOGGER.warn("用户 {} 的主题偏好无效，已回退默认配置: {}",
                    userId, exception.getMessage());
            return defaults();
        }
    }

    public ConsoleThemePreference save(Long userId, String username,
                                       Map<String, Object> body) {
        ConsoleThemePreference preference = validate(body);
        preferenceService.save(userId, PREFERENCE_KEY,
                JSON.toJSONString(preference), username);
        return preference;
    }

    private ConsoleThemePreference validate(Map<String, Object> body) {
        if (body == null) {
            throw new IllegalArgumentException("主题配置不能为空");
        }
        Object schemaVersion = body.get("schemaVersion");
        if (!(schemaVersion instanceof Number)
                || ((Number) schemaVersion).doubleValue()
                != ((Number) schemaVersion).intValue()) {
            throw new IllegalArgumentException("不支持的主题配置版本");
        }
        int version = ((Number) schemaVersion).intValue();
        if (version == 1) return migrateLegacy(body);
        if (version == 2 && body.keySet().equals(V2_FIELDS)) {
            Map<String, Object> migrated = new HashMap<>(body);
            migrated.put("schemaVersion", 3);
            migrated.put("tableScrollMode", "AUTO");
            return validate(migrated);
        }
        if (version != 3) {
            throw new IllegalArgumentException("不支持的主题配置版本");
        }
        if (!body.keySet().equals(FIELDS)) {
            throw new IllegalArgumentException("主题配置字段不完整或包含未知字段");
        }
        String colorScheme = requireAllowed(body, "colorScheme",
                COLOR_SCHEMES, "整体风格无效");
        String accentMode = requireAllowed(body, "accentMode",
                ACCENT_MODES, "主题色来源无效");
        String accentPreset = requireAllowed(body, "accentPreset",
                ACCENT_PRESETS, "主题色无效");
        String customSolidColor = requireColor(body, "customSolidColor");
        List<String> customGradientColors = requireGradientColors(body);
        String customGradientType = requireAllowed(body,
                "customGradientType", GRADIENT_TYPES, "渐变方式无效");
        Integer customGradientAngle = requireInteger(body,
                "customGradientAngle", 0, 360, "渐变角度无效");
        String navigationLayout = requireAllowed(body,
                "navigationLayout", NAVIGATION_LAYOUTS, "菜单位置无效");
        String sidebarTheme = requireAllowed(body, "sidebarTheme",
                SIDEBAR_THEMES, "侧栏风格无效");
        String contentWidth = requireAllowed(body, "contentWidth",
                CONTENT_WIDTHS, "内容区宽度无效");
        Boolean fixedSidebar = requireBoolean(body, "fixedSidebar");
        Boolean colorWeak = requireBoolean(body, "colorWeak");
        String tableScrollMode = requireAllowed(body, "tableScrollMode",
                TABLE_SCROLL_MODES, "表格滚动方式无效");
        return new ConsoleThemePreference(3, colorScheme, accentMode,
                accentPreset, customSolidColor, customGradientColors,
                customGradientType, customGradientAngle, navigationLayout,
                sidebarTheme, contentWidth, tableScrollMode, fixedSidebar, colorWeak);
    }

    private ConsoleThemePreference migrateLegacy(Map<String, Object> body) {
        if (!body.keySet().equals(LEGACY_FIELDS)) {
            throw new IllegalArgumentException("主题配置字段不完整或包含未知字段");
        }
        String colorScheme = requireAllowed(body, "colorScheme",
                COLOR_SCHEMES, "整体风格无效");
        String accentPreset = requireAllowed(body, "accentPreset",
                ACCENT_PRESETS, "主题色无效");
        String sidebarTheme = requireAllowed(body, "sidebarTheme",
                SIDEBAR_THEMES, "侧栏风格无效");
        String contentWidth = requireAllowed(body, "contentWidth",
                CONTENT_WIDTHS, "内容区宽度无效");
        Boolean fixedSidebar = requireBoolean(body, "fixedSidebar");
        Boolean colorWeak = requireBoolean(body, "colorWeak");
        return new ConsoleThemePreference(3, colorScheme, "PRESET",
                accentPreset, "#2639E9", List.of("#2639E9", "#873FF2"),
                "LINEAR", 135, "LEFT", sidebarTheme, contentWidth, "AUTO",
                fixedSidebar, colorWeak);
    }

    private static String requireAllowed(Map<String, Object> body, String key,
                                         Set<String> allowed, String message) {
        Object value = body.get(key);
        if (!(value instanceof String) || !allowed.contains(value)) {
            throw new IllegalArgumentException(message);
        }
        return (String) value;
    }

    private static Boolean requireBoolean(Map<String, Object> body,
                                          String key) {
        Object value = body.get(key);
        if (!(value instanceof Boolean)) {
            throw new IllegalArgumentException(key + " 必须为布尔值");
        }
        return (Boolean) value;
    }

    private static String requireColor(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (!(value instanceof String)
                || !HEX_COLOR.matcher((String) value).matches()) {
            throw new IllegalArgumentException(key + " 必须为六位十六进制颜色");
        }
        return ((String) value).toUpperCase(Locale.ROOT);
    }

    private static List<String> requireGradientColors(
            Map<String, Object> body) {
        Object value = body.get("customGradientColors");
        if (!(value instanceof Collection<?>)) {
            throw new IllegalArgumentException("自定义渐变色必须为颜色数组");
        }
        Collection<?> values = (Collection<?>) value;
        if (values.size() < 2 || values.size() > 3) {
            throw new IllegalArgumentException("自定义渐变色仅支持二色或三色");
        }
        List<String> colors = new ArrayList<>(values.size());
        for (Object item : values) {
            if (!(item instanceof String)
                    || !HEX_COLOR.matcher((String) item).matches()) {
                throw new IllegalArgumentException("自定义渐变色包含无效颜色");
            }
            colors.add(((String) item).toUpperCase(Locale.ROOT));
        }
        return List.copyOf(colors);
    }

    private static Integer requireInteger(Map<String, Object> body, String key,
                                          int minimum, int maximum,
                                          String message) {
        Object value = body.get(key);
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException(message);
        }
        Number number = (Number) value;
        int normalized = number.intValue();
        if (number.doubleValue() != normalized
                || normalized < minimum || normalized > maximum) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
