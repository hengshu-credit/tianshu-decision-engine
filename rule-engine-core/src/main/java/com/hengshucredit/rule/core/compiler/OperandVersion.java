package com.hengshucredit.rule.core.compiler;

import com.alibaba.fastjson.JSONObject;
import java.util.Set;

public final class OperandVersion {
    private OperandVersion() { }

    public static Long fixedVersionId(JSONObject operand) {
        String mode = operand.getString("versionMode");
        if (mode == null || mode.isBlank() || "LATEST".equals(mode)) return null;
        if (!"FIXED".equals(mode)) throw new IllegalArgumentException("未知字段版本策略：" + mode);
        if (!Set.of("REFERENCE", "PATH", "FUNCTION").contains(operand.getString("kind")))
            throw new IllegalArgumentException("当前表达式节点不支持固定版本");
        Object value = operand.get("versionId");
        if (value == null || !String.valueOf(value).matches("[1-9][0-9]*")) throw new IllegalArgumentException("请选择有效的固定版本");
        try { return Long.valueOf(String.valueOf(value)); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("版本 ID 超出有效范围", error); }
    }
}
