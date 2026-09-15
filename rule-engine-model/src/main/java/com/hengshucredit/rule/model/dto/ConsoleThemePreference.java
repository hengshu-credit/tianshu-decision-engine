package com.hengshucredit.rule.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConsoleThemePreference {
    private Integer schemaVersion;
    private String colorScheme;
    private String accentMode;
    private String accentPreset;
    private String customSolidColor;
    private List<String> customGradientColors;
    private String customGradientType;
    private Integer customGradientAngle;
    private String navigationLayout;
    private String sidebarTheme;
    private String contentWidth;
    private String tableScrollMode;
    private Boolean fixedSidebar;
    private Boolean colorWeak;
}
