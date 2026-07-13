package com.hengshucredit.rule.core.compiler;

import java.util.Collections;
import java.util.Map;

/**
 * 变量上下文：提供 refType:id → scriptName、varId → scriptName 和 varCode → scriptName 的可靠映射，
 * 解决 varCode 与 scriptName 大小写不一致导致的脚本变量名错误问题。
 *
 * 编译时传入此上下文，所有编译器通过 {@link #getScriptName(Long)} 或
 * {@link #getScriptNameByVarCode(String)} 获取脚本中实际使用的变量引用名，
 * 而不是直接使用 modelJson 中的 varCode。
 *
 * 设计原则：
 * - 优先通过 refType + varId 精确查 scriptName，避免不同资源表 ID 冲突
 * - 若 refType 缺失或未命中，回退旧版 varId 映射（兼容存量变量数据）
 * - 进一步通过 varCode 在全局映射中查找 scriptName（设计器未保存 _varId 时兜底）
 */
public class VarContext {

    /** varId → scriptName 映射 */
    private final Map<Long, String> varIdToScriptName;

    /** varCode → scriptName 映射，用于 varId 缺失时的兜底查找（大小写敏感） */
    private final Map<String, String> varCodeToScriptName;

    /** refType:id → scriptName 映射，用于跨变量/常量/数据对象/模型的精确引用 */
    private final Map<String, String> refIdToScriptName;

    /** 常量 ID → 可信 QLExpress 表达式映射 */
    private final Map<Long, String> constantIdToExpression;

    /**
     * 构造函数，仅构建 varId → scriptName 映射。
     * {@link #getScriptNameByVarCode(String)} 在此构造方式下恒返 null。
     */
    public VarContext(Map<Long, String> varIdToScriptName) {
        this(varIdToScriptName, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
    }

    /**
     * 构造函数，同时构建两种映射。
     *
     * @param varIdToScriptName   varId → scriptName
     * @param varCodeToScriptName varCode → scriptName（大小写敏感）
     */
    public VarContext(Map<Long, String> varIdToScriptName, Map<String, String> varCodeToScriptName) {
        this(varIdToScriptName, varCodeToScriptName, Collections.emptyMap(), Collections.emptyMap());
    }

    /**
     * 构造函数，同时构建三种映射。
     *
     * @param varIdToScriptName   旧版变量 ID → scriptName
     * @param varCodeToScriptName varCode → scriptName（大小写敏感）
     * @param refIdToScriptName   refType:id → scriptName
     */
    public VarContext(Map<Long, String> varIdToScriptName,
                      Map<String, String> varCodeToScriptName,
                      Map<String, String> refIdToScriptName) {
        this(varIdToScriptName, varCodeToScriptName, refIdToScriptName, Collections.emptyMap());
    }

    public VarContext(Map<Long, String> varIdToScriptName,
                      Map<String, String> varCodeToScriptName,
                      Map<String, String> refIdToScriptName,
                      Map<Long, String> constantIdToExpression) {
        this.varIdToScriptName = varIdToScriptName != null ? varIdToScriptName : Collections.emptyMap();
        this.varCodeToScriptName = varCodeToScriptName != null ? varCodeToScriptName : Collections.emptyMap();
        this.refIdToScriptName = refIdToScriptName != null ? refIdToScriptName : Collections.emptyMap();
        this.constantIdToExpression = constantIdToExpression != null ? constantIdToExpression : Collections.emptyMap();
    }

    /**
     * 根据变量 ID 获取脚本中实际使用的引用名。
     * 若 ID 为 null 或未在映射中查到，返回 null（调用方将回退到 varCode）。
     *
     * @param varId 变量数据库 ID
     * @return 脚本引用名（scriptName），查不到时返回 null
     */
    public String getScriptName(Long varId) {
        if (varId == null) return null;
        return varIdToScriptName.get(varId);
    }

    /**
     * 根据变量 ID 获取脚本引用名，若未查到则返回传入的默认值。
     * 用于简化调用方逻辑。
     */
    public String getScriptNameOrElse(Long varId, String fallback) {
        String name = getScriptName(varId);
        return name != null ? name : fallback;
    }

    /**
     * 根据 varCode 查找脚本引用名。
     * 严格区分大小写，精确匹配后返回。
     * 用于设计器未保存 _varId 时的兜底回溯。
     *
     * @param varCode 变量编码（来自设计器 modelJson）
     * @return scriptName，查不到时返回 null
     */
    public String getScriptNameByVarCode(String varCode) {
        if (varCode == null) return null;
        return varCodeToScriptName.get(varCode);
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

    /** 是否为空（无任何映射） */
    public boolean isEmpty() {
        return varIdToScriptName.isEmpty() && varCodeToScriptName.isEmpty()
                && refIdToScriptName.isEmpty() && constantIdToExpression.isEmpty();
    }

    /**
     * 根据变量 ID 和编码解析脚本引用名。
     * 解析优先级：varId 精确匹配 → varCode 回退查找 → 返回原 varCode
     *
     * <p>等价于：
     * {@code varId != null ? orDefault(getScriptName(varId), varCode) : orDefault(getScriptNameByVarCode(varCode), varCode)}
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
     * 解析优先级：refType:id 精确匹配 → 旧 varId 映射 → varCode 回退查找 → 返回原 varCode
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
        if (varId != null && refType != null && !refType.trim().isEmpty()) {
            String name = refIdToScriptName.get(refKey(refType, varId));
            if (name != null) return name;
        }
        if (varId != null) {
            String name = varIdToScriptName.get(varId);
            if (name != null) return name;
        }
        if (varCode != null) {
            String name = varCodeToScriptName.get(varCode);
            if (name != null) return name;
        }
        return varCode != null ? varCode : "";
    }

    private String refKey(String refType, Long refId) {
        return refType.trim().toUpperCase() + ":" + refId;
    }

    /** 返回只读映射快照，便于调试和日志输出 */
    public Map<Long, String> getSnapshot() {
        return Collections.unmodifiableMap(varIdToScriptName);
    }
}
