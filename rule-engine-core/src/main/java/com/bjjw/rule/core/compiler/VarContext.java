package com.bjjw.rule.core.compiler;

import java.util.Collections;
import java.util.Map;

/**
 * 变量上下文：提供 varId → scriptName 的可靠映射，
 * 解决 varCode 与 scriptName 大小写不一致导致的脚本变量名错误问题。
 *
 * 编译时传入此上下文，所有编译器通过 {@link #getScriptName(Long)} 获取
 * 脚本中实际使用的变量引用名，而不是直接使用 modelJson 中的 varCode。
 *
 * 设计原则：
 * - 优先通过 varId 精确查 scriptName（大小写正确）
 * - 若 varId 为 null 或未命中，回退到 varCode（兼容旧数据）
 */
public class VarContext {

    /** varId → scriptName 映射 */
    private final Map<Long, String> varIdToScriptName;

    /** 构造函数，接收已构建好的映射 */
    public VarContext(Map<Long, String> varIdToScriptName) {
        this.varIdToScriptName = varIdToScriptName != null ? varIdToScriptName : Collections.emptyMap();
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

    /** 是否为空（无任何映射） */
    public boolean isEmpty() {
        return varIdToScriptName.isEmpty();
    }

    /** 返回只读映射快照，便于调试和日志输出 */
    public Map<Long, String> getSnapshot() {
        return Collections.unmodifiableMap(varIdToScriptName);
    }
}