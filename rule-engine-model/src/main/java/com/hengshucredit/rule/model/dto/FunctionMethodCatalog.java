package com.hengshucredit.rule.model.dto;

import java.util.List;

/** 方法发现只返回类型与签名，不执行实现或读取 Bean 状态。 */
public record FunctionMethodCatalog(String status, String message, String resolvedClassName,
                                    String signature, boolean legacySignature, boolean valid,
                                    List<MethodOption> methods) {
    public record MethodOption(String signature, String name, String displaySignature,
                               List<ParameterOption> parameters, String returnJavaType,
                               boolean staticMethod, boolean available, String unavailableReason) { }
    public record ParameterOption(String name, String javaType, String suggestedType) { }
}
