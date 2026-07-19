package com.hengshucredit.rule.core.compiler;

import java.util.Collections;
import java.util.Map;

/**
 * 变量上下文：通过 refType:id 提供稳定引用到 scriptName 的可靠映射。
 * 解决 varCode 与 scriptName 大小写不一致导致的脚本变量名错误问题。
 *
 * 编译时传入此上下文，所有受管字段只能通过 refType:id 获取脚本引用名。
 *
 * 设计原则：
 * - 只通过 refType + varId 精确查 scriptName，避免不同资源表 ID 冲突
 * - varCode 仅作为错误信息中的展示值，禁止参与关联或回退
 */
public class VarContext {

    /** refType:id → scriptName 映射，用于跨变量/常量/数据对象/模型的精确引用 */
    private final Map<String, String> refIdToScriptName;

    /** 常量 ID → 可信 QLExpress 表达式映射 */
    private final Map<Long, String> constantIdToExpression;

    /** 函数 ID → 当前函数编码映射 */
    private final Map<Long, String> functionIdToCode;

    /** 函数 ID → 固定参数数量映射 */
    private final Map<Long, Integer> functionIdToArity;

    /**
     * 兼容旧调用签名；裸 ID 映射不参与受管字段解析。
     */
    public VarContext(Map<Long, String> varIdToScriptName) {
        this(varIdToScriptName, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
    }

    /**
     * 兼容旧调用签名；第二个映射被明确忽略，禁止按 varCode 解析引用。
     *
     * @param varIdToScriptName   varId → scriptName
     * @param ignoredVarCodeToScriptName 已废弃的 varCode 映射，不参与解析
     */
    public VarContext(Map<Long, String> varIdToScriptName, Map<String, String> ignoredVarCodeToScriptName) {
        this(varIdToScriptName, ignoredVarCodeToScriptName, Collections.emptyMap(), Collections.emptyMap());
    }

    /**
     * 兼容旧调用签名，仅 refType:id 映射参与受管字段解析。
     *
     * @param varIdToScriptName   旧版变量 ID → scriptName
     * @param ignoredVarCodeToScriptName 已废弃的 varCode 映射，不参与解析
     * @param refIdToScriptName   refType:id → scriptName
     */
    public VarContext(Map<Long, String> varIdToScriptName,
                      Map<String, String> ignoredVarCodeToScriptName,
                      Map<String, String> refIdToScriptName) {
        this(varIdToScriptName, ignoredVarCodeToScriptName, refIdToScriptName, Collections.emptyMap());
    }

    public VarContext(Map<Long, String> varIdToScriptName,
                      Map<String, String> ignoredVarCodeToScriptName,
                      Map<String, String> refIdToScriptName,
                      Map<Long, String> constantIdToExpression) {
        this(varIdToScriptName, ignoredVarCodeToScriptName, refIdToScriptName, constantIdToExpression,
                Collections.emptyMap(), Collections.emptyMap());
    }

    public VarContext(Map<Long, String> varIdToScriptName,
                      Map<String, String> ignoredVarCodeToScriptName,
                      Map<String, String> refIdToScriptName,
                      Map<Long, String> constantIdToExpression,
                      Map<Long, String> functionIdToCode,
                      Map<Long, Integer> functionIdToArity) {
        this.refIdToScriptName = refIdToScriptName != null ? refIdToScriptName : Collections.emptyMap();
        this.constantIdToExpression = constantIdToExpression != null ? constantIdToExpression : Collections.emptyMap();
        this.functionIdToCode = functionIdToCode != null ? functionIdToCode : Collections.emptyMap();
        this.functionIdToArity = functionIdToArity != null ? functionIdToArity : Collections.emptyMap();
    }

    /**
     * 根据引用类型和 ID 获取脚本引用名。
     */
    public String getScriptName(String refType, Long refId) {
        if (refId == null || refType == null || refType.trim().isEmpty()) return null;
        return refIdToScriptName.get(refKey(refType, refId));
    }

    public String resolveConstant(Long refId) {
        if (refId == null) {
            throw new IllegalArgumentException("常量引用缺少 ID");
        }
        String expression = constantIdToExpression.get(refId);
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("常量引用不存在、已停用或值不合法，ID=" + refId);
        }
        return expression;
    }

    public String resolveFunction(Long functionId, int actualArity) {
        if (functionId == null) {
            throw new IllegalArgumentException("受管方法引用缺少 ID");
        }
        String code = functionIdToCode.get(functionId);
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("方法引用不存在或已停用，ID=" + functionId);
        }
        Integer expectedArity = functionIdToArity.get(functionId);
        if (expectedArity != null && expectedArity != actualArity) {
            throw new IllegalArgumentException("方法 " + code + " 需要 " + expectedArity
                    + " 个参数，实际为 " + actualArity);
        }
        return code;
    }

    /** 是否为空（无任何映射） */
    public boolean isEmpty() {
        return refIdToScriptName.isEmpty() && constantIdToExpression.isEmpty()
                && functionIdToCode.isEmpty() && functionIdToArity.isEmpty();
    }

    /**
     * 根据变量 ID 和编码解析脚本引用名。
     * 兼容旧调用签名。缺少 ref_type 的受管引用会被拒绝；仅 ID 不参与类型猜测。
     *
     * @param varId   变量数据库 ID（可为 null）
     * @param varCode 变量编码（来自设计器 modelJson，可为 null）
     * @return 脚本中实际使用的引用名，永不为 null
     */
    public String resolveVar(Long varId, String varCode) {
        return resolveVar(varId, null, varCode);
    }

    /**
     * 根据引用类型、ID 和编码解析脚本引用名。
     * 解析规则：只允许 refType:id 精确匹配；varCode 仅用于非受管局部标识符。
     *
     * @param varId   引用字段 ID（可为 null）
     * @param refType 引用类型：VARIABLE/CONSTANT/DATA_OBJECT/MODEL（可为 null）
     * @param varCode 变量编码或字段脚本名（来自设计器 modelJson，可为 null）
     * @return 脚本中实际使用的引用名，永不为 null
     */
    public String resolveVar(Long varId, String refType, String varCode) {
        if ("CONSTANT".equalsIgnoreCase(refType)) {
            return resolveConstant(varId);
        }
        boolean missingId = varId == null;
        boolean missingType = refType == null || refType.trim().isEmpty();
        if (missingId && missingType) {
            // 局部变量、循环项等非受管标识符不参与资源关联，保持原样。
            return varCode == null ? "" : varCode;
        }
        if (missingId || missingType) {
            throw new IllegalArgumentException("受管字段引用缺少 ID 或引用类型");
        }
        String key = refKey(refType, varId);
        String name = refIdToScriptName.get(key);
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("字段引用不存在或已停用，" + key);
        }
        return name;
    }

    private String refKey(String refType, Long refId) {
        return refType.trim().toUpperCase() + ":" + refId;
    }

}
