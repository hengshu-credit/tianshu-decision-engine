package com.hengshucredit.rule.core.function;

import com.hengshucredit.rule.core.engine.RuntimeContextBridge;

/** QLExpress 请求级运行时上下文函数。 */
public class RuntimeContextBuiltinFunctions {

    public Object setRuntimeValue(String path, Object value) {
        return RuntimeContextBridge.setValue(path, value);
    }
}
