package com.hengshucredit.rule.core.compiler;

public interface RuleCompiler {

    /**
     * 使用变量上下文编译模型 JSON 为 QLExpress 脚本。
     * 受管字段只允许通过 varContext 中的 refType:id 映射解析。
     *
     * @param modelJson   模型设计 JSON
     * @param varContext  变量上下文（refType:id → scriptName 映射）
     * @return 编译结果
     */
    default CompileResult compile(String modelJson, VarContext varContext) {
        // 兼容旧调用：有 varContext 但编译器未升级时，回退到无上下文编译
        return compile(modelJson);
    }

    /**
     * 无变量上下文的编译（兼容旧实现）。
     */
    CompileResult compile(String modelJson);
}
