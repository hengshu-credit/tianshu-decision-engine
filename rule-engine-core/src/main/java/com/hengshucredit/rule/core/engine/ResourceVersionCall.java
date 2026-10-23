package com.hengshucredit.rule.core.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 固定版本读取按资源身份与不可变版本身份分离，业务编码不参与关联。 */
public record ResourceVersionCall(String refType, Long refId, Long versionId,
                                  String relativePath, List<Object> arguments, String statusDimension, String expectedStatus, String versionSource) {
    private static final Set<String> TYPES = Set.of("VARIABLE", "CONSTANT", "DATA_OBJECT", "DATA_OBJECT_ROOT", "MODEL", "MODEL_OUTPUT", "EXTERNAL_API", "FUNCTION");

    public ResourceVersionCall(String refType, Long refId, Long versionId, String relativePath, List<Object> arguments) {
        this(refType, refId, versionId, relativePath, arguments, null, null, null);
    }
    public ResourceVersionCall(String refType, Long refId, Long versionId, String relativePath, List<Object> arguments, String statusDimension, String expectedStatus) {
        this(refType, refId, versionId, relativePath, arguments, statusDimension, expectedStatus, null);
    }

    public ResourceVersionCall {
        refType = refType == null ? "" : refType.toUpperCase(Locale.ROOT);
        if (!TYPES.contains(refType)) throw new IllegalArgumentException("不支持固定版本的引用类型：" + refType);
        if (versionSource != null && !versionSource.isBlank() && !"BUSINESS_VERSION".equals(versionSource) && !("FUNCTION".equals(refType) && "FUNCTION_HISTORY".equals(versionSource)))
            throw new IllegalArgumentException("资源版本来源不匹配");
        if (versionSource != null && versionSource.isBlank()) versionSource = null;
        if (refId == null || refId <= 0 || versionId == null || versionId <= 0)
            throw new IllegalArgumentException("固定版本引用必须包含有效的资源 ID 和版本 ID");
        relativePath = relativePath == null ? "" : relativePath;
        arguments = arguments == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(arguments));
    }

    public String versionToken() { return versionSource == null ? String.valueOf(versionId) : versionSource + ":" + versionId; }
    public String bindingKey() { return refType + ":" + refId + "@" + versionToken(); }
}
