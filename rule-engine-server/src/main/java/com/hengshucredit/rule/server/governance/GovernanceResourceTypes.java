package com.hengshucredit.rule.server.governance;

import java.util.List;
import java.util.Map;

public final class GovernanceResourceTypes {
    public static final String VARIABLE = "VARIABLE";
    public static final String DATA_OBJECT = "DATA_OBJECT";
    public static final String MODEL = "MODEL";
    public static final String EXTERNAL_DATASOURCE =
            "EXTERNAL_DATASOURCE";
    public static final String EXTERNAL_API = "EXTERNAL_API";
    public static final String DATABASE = "DATABASE";
    public static final String SFTP = "SFTP";
    public static final String FUNCTION = "FUNCTION";
    public static final String RULE = "RULE";
    public static final String RULE_PROJECT_BINDING =
            "RULE_PROJECT_BINDING";
    public static final String LIST_LIBRARY = "LIST_LIBRARY";
    public static final String LIST_RECORD_BATCH = "LIST_RECORD_BATCH";
    public static final String FIELD_VALIDATION = "FIELD_VALIDATION";
    public static final String BILLING_CONFIG = "BILLING_CONFIG";
    public static final String EXPERIMENT = "EXPERIMENT";
    public static final String PROJECT = "PROJECT";
    public static final String SYSTEM_SETTING = "SYSTEM_SETTING";

    private static final Map<String, List<String>> TAB_TYPES = Map.of(
            "FIELD", List.of(VARIABLE, DATA_OBJECT, FIELD_VALIDATION),
            "MODEL", List.of(MODEL),
            "DATASOURCE", List.of(
                    EXTERNAL_DATASOURCE, EXTERNAL_API),
            "DATABASE", List.of(DATABASE),
            "SFTP", List.of(SFTP),
            "FUNCTION", List.of(FUNCTION),
            "RULE", List.of(RULE, RULE_PROJECT_BINDING),
            "LIST", List.of(LIST_LIBRARY, LIST_RECORD_BATCH),
            "EXPERIMENT", List.of(EXPERIMENT),
            "PROJECT", List.of(PROJECT, BILLING_CONFIG));

    private GovernanceResourceTypes() {
    }

    public static List<String> forTab(String tab) {
        if ("SYSTEM".equalsIgnoreCase(tab)) return List.of(SYSTEM_SETTING);
        return TAB_TYPES.getOrDefault(
                tab == null ? "" : tab.toUpperCase(),
                List.of());
    }
}
