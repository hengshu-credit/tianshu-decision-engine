package com.hengshucredit.rule.core.engine;

import java.util.function.BiConsumer;

/**
 * 将编译脚本产生的中间结果通知给当前请求的运行时上下文。
 * 未绑定监听器时为安全的空操作，保证客户端和纯核心执行兼容。
 */
public final class RuntimeContextBridge {

    private static final ThreadLocal<BiConsumer<String, Object>> LISTENER = new ThreadLocal<>();

    private RuntimeContextBridge() {
    }

    public static void bind(BiConsumer<String, Object> listener) {
        if (listener == null) {
            LISTENER.remove();
        } else {
            LISTENER.set(listener);
        }
    }

    public static void clear() {
        LISTENER.remove();
    }

    public static Object setValue(String path, Object value) {
        BiConsumer<String, Object> listener = LISTENER.get();
        if (listener != null) {
            listener.accept(path, value);
        }
        return value;
    }
}
