package com.hengshucredit.rule.core.function;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 部署代码明确授权脚本调用的方法。必须审核实现不暴露文件、反射、进程或秘密。 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ScriptCallable { }
