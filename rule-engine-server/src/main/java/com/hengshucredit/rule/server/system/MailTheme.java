package com.hengshucredit.rule.server.system;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.dto.ConsoleThemePreference;
import org.springframework.core.io.ClassPathResource;

import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** 同一封邮件和工作簿共享发送时解析的颜色；预设与控制台使用同一份目录。 */
public record MailTheme(String accent, String scrollbarEnd, String onAccent, String link, String page,
                        String surface, String text, String muted, String border, String band,
                        String month, String total, String track) {
    private record Preset(String primary, String secondary, boolean gradient) {}
    private static final Map<String, Preset> PRESETS = loadPresets();
    public static final MailTheme DEFAULT = from(null);

    private static Map<String, Preset> loadPresets() {
        try {
            var rows = JSON.parseArray(new ClassPathResource("theme-presets.json").getContentAsString(StandardCharsets.UTF_8));
            Map<String, Preset> presets = new HashMap<>();
            for (int i = 0; i < rows.size(); i++) {
                var row = rows.getJSONObject(i);
                presets.put(row.getString("id"), new Preset(row.getString("primary"), row.getString("secondary"),
                        "gradient".equals(row.getString("kind"))));
            }
            return Map.copyOf(presets);
        } catch (IOException e) {
            throw new IllegalStateException("无法读取主题色预设", e);
        }
    }

    public static MailTheme from(ConsoleThemePreference saved) {
        boolean dark = saved != null && "DARK".equals(saved.getColorScheme());
        Preset preset = PRESETS.getOrDefault(saved == null ? "" : saved.getAccentPreset(), PRESETS.get("PEACH_PINK_GRADIENT"));
        String primary = preset.primary();
        String end = preset.gradient() ? preset.secondary() : primary;
        if (saved != null && "CUSTOM_SOLID".equals(saved.getAccentMode())) {
            primary = saved.getCustomSolidColor();
            end = primary;
        } else if (saved != null && "CUSTOM_GRADIENT".equals(saved.getAccentMode())) {
            var stops = saved.getCustomGradientColors();
            primary = stops.get(0);
            end = stops.get(stops.size() - 1);
        }
        String surface = dark ? "#151D31" : "#FFFFFF";
        String page = dark ? "#0F1629" : "#F6F8FC";
        String text = dark ? "#EDF2FF" : "#172033";
        String muted = dark ? "#C2CCE0" : "#475569";
        String border = dark ? "#34415D" : "#CBD5E1";
        if (saved != null && Boolean.TRUE.equals(saved.getColorWeak())) {
            primary = invert80(primary); end = invert80(end); surface = invert80(surface);
            page = invert80(page); text = invert80(text); muted = invert80(muted); border = invert80(border);
        }
        String link = primary;
        for (int i = 0; i < 10 && contrast(surface, link) < 4.5; i++) link = mix(link, text, .22);
        String onAccent = contrast(primary, "#FFFFFF") >= contrast(primary, "#101525") ? "#FFFFFF" : "#101525";
        return new MailTheme(primary, end, onAccent, link, page, surface, text, muted, border,
                mix(surface, primary, .045), mix(surface, primary, dark ? .18 : .09),
                mix(surface, primary, dark ? .30 : .19), mix(surface, primary, dark ? .12 : .08));
    }

    private static String invert80(String rgb) {
        Color c = Color.decode(rgb);
        return mix(rgb, hex(255-c.getRed(), 255-c.getGreen(), 255-c.getBlue()), .8);
    }

    private static String mix(String base, String tint, double weight) {
        Color a = Color.decode(base), b = Color.decode(tint);
        return hex((int)Math.round(a.getRed()*(1-weight)+b.getRed()*weight),
                (int)Math.round(a.getGreen()*(1-weight)+b.getGreen()*weight),
                (int)Math.round(a.getBlue()*(1-weight)+b.getBlue()*weight));
    }

    private static String hex(int r, int g, int b) { return String.format("#%02X%02X%02X", r, g, b); }

    private static double contrast(String a, String b) {
        double x = luminance(Color.decode(a)) + .05, y = luminance(Color.decode(b)) + .05;
        return Math.max(x,y) / Math.min(x,y);
    }

    private static double luminance(Color c) {
        return .2126*linear(c.getRed()) + .7152*linear(c.getGreen()) + .0722*linear(c.getBlue());
    }

    private static double linear(int channel) {
        double s = channel / 255.0;
        return s <= .04045 ? s / 12.92 : Math.pow((s + .055) / 1.055, 2.4);
    }

    public Map<String, String> templateColors() {
        return Map.of("accent", accent, "onaccent", onAccent, "link", link, "page", page, "surface", surface,
                "textcolor", text, "muted", muted, "border", border, "thumbend", scrollbarEnd, "track", track);
    }
}
