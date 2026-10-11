package com.hengshucredit.rule.model.dto;

import lombok.Data;
import java.util.Map;
import java.util.Set;

/** Management test source. VERSION identifies an immutable snapshot, not a mutable version binding. */
@Data
public class RuleTestExecutionSource {
    private String sourceType;
    private String sourceId;
    private String lockVersion;
    private String baseSourceType;
    private String baseSourceId;

    public void validate(String modelJson) {
        if (!Set.of("WORKSPACE", "PREVIEW", "REVISION", "VERSION", "LATEST", "BUSINESS_VERSION").contains(sourceType == null ? "" : sourceType))
            throw new IllegalArgumentException("请选择有效的测试来源");
        if ("REVISION".equals(sourceType) || "VERSION".equals(sourceType) || "BUSINESS_VERSION".equals(sourceType)) {
            requireId(sourceId);
        }
        if (lockVersion != null) {
            if (!lockVersion.matches("0|[1-9][0-9]*")) throw new IllegalArgumentException("草稿锁版本必须是规范的非负整数");
            try { Integer.parseInt(lockVersion); }
            catch (NumberFormatException invalid) { throw new IllegalArgumentException("草稿锁版本超出有效范围"); }
        }
        if ("PREVIEW".equals(sourceType) && (modelJson == null || modelJson.isBlank()))
            throw new IllegalArgumentException("画布预览必须提供本次模型内容");
        if ("PREVIEW".equals(sourceType)) {
            if (!Set.of("REVISION", "VERSION", "WORKSPACE").contains(baseSourceType == null ? "" : baseSourceType))
                throw new IllegalArgumentException("画布预览必须提供可验证的编辑来源");
            requireId(baseSourceId);
        }
        if (!"PREVIEW".equals(sourceType) && modelJson != null && !modelJson.isBlank())
            throw new IllegalArgumentException("指定版本测试不能混入当前画布内容");
    }

    private void requireId(String value) {
        if (value == null || !value.matches("[1-9][0-9]*"))
            throw new IllegalArgumentException("指定版本测试必须提供有效的修订或快照 ID");
        try { Long.parseLong(value); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("测试修订或快照 ID 超出有效范围"); }
    }

    public static RuleTestExecutionSource from(Object value) {
        if (value == null) return null;
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("测试来源格式无效");
        RuleTestExecutionSource result = new RuleTestExecutionSource();
        result.setSourceType(map.get("sourceType") instanceof String text ? text : null);
        try {
            if (map.get("sourceId") != null) result.setSourceId(map.get("sourceId").toString());
            if (map.get("lockVersion") != null) result.setLockVersion(map.get("lockVersion").toString());
            result.setBaseSourceType(map.get("baseSourceType") instanceof String text ? text : null);
            if (map.get("baseSourceId") != null) result.setBaseSourceId(map.get("baseSourceId").toString());
        } catch (NumberFormatException invalid) { throw new IllegalArgumentException("测试修订标识或锁版本格式无效"); }
        return result;
    }
}
