package com.hengshucredit.rule.core.action;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

/** 设计器编译与运行时共用的名单动作配置校验。 */
public final class ListWriteConfig {
    private ListWriteConfig() { }

    public static JSONObject fromAction(JSONObject action) {
        JSONObject config = new JSONObject(true);
        for (String key : new String[]{"listIds", "operation", "expiryMode", "duration", "durationUnit", "expireTime"}) {
            if (action.containsKey(key)) config.put(key, action.get(key));
        }
        JSONArray entries = action.getJSONArray("entries");
        JSONArray types = new JSONArray();
        if (entries != null) for (Object raw : entries) types.add(((JSONObject) raw).getString("itemType"));
        config.put("itemTypes", types);
        validate(config);
        return config;
    }

    public static void validate(JSONObject config) {
        JSONArray ids = config.getJSONArray("listIds");
        if (ids == null || ids.isEmpty()) throw new IllegalArgumentException("名单落库请至少选择一个名单");
        for (Object id : ids) positiveLong(id, "名单 ID");
        if (!Set.of("MERGE", "OVERWRITE", "ADD_ONLY", "REMOVE").contains(config.getString("operation") == null ? "" : config.getString("operation"))) {
            throw new IllegalArgumentException("名单落库操作无效");
        }
        JSONArray types = config.getJSONArray("itemTypes");
        if (types == null || types.isEmpty()) throw new IllegalArgumentException("名单落库请至少添加一个内容字段");
        Set<String> allowed = Set.of("MOBILE", "ID_CARD", "ADDRESS", "IP", "DEVICE", "NAME", "GPS", "EMAIL", "BANK_CARD", "OTHER");
        for (Object type : types) if (type == null || !allowed.contains(String.valueOf(type))) throw new IllegalArgumentException("名单内容类型无效");
        if ("REMOVE".equals(config.getString("operation"))) return;
        String mode = config.getString("expiryMode");
        if ("PERMANENT".equals(mode)) return;
        if ("DURATION".equals(mode)) {
            positiveLong(config.get("duration"), "名单有效时长");
            if (!Set.of("MINUTES", "HOURS", "DAYS").contains(config.getString("durationUnit") == null ? "" : config.getString("durationUnit"))) throw new IllegalArgumentException("名单有效时长单位无效");
        } else if ("UNTIL".equals(mode)) {
            try { LocalDateTime.parse(config.getString("expireTime").replace(' ', 'T')); }
            catch (RuntimeException error) { throw new IllegalArgumentException("请设置有效的名单失效时间", error); }
        } else throw new IllegalArgumentException("名单有效期配置无效");
    }

    public static long positiveLong(Object value, String label) {
        try {
            long id = new BigDecimal(String.valueOf(value)).longValueExact();
            if (id > 0) return id;
        } catch (NumberFormatException | ArithmeticException ignored) { }
        throw new IllegalArgumentException(label + "必须是正整数");
    }

    public static LocalDateTime expiresAt(JSONObject config, LocalDateTime now) {
        if ("REMOVE".equals(config.getString("operation")) || "PERMANENT".equals(config.getString("expiryMode"))) return null;
        if ("UNTIL".equals(config.getString("expiryMode"))) return LocalDateTime.parse(config.getString("expireTime").replace(' ', 'T'));
        long duration = positiveLong(config.get("duration"), "名单有效时长");
        return switch (config.getString("durationUnit")) {
            case "MINUTES" -> now.plusMinutes(duration);
            case "HOURS" -> now.plusHours(duration);
            default -> now.plusDays(duration);
        };
    }
}
