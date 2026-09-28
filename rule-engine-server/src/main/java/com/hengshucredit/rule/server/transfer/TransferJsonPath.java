package com.hengshucredit.rule.server.transfer;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.server.artifact.CanonicalJson;
import java.util.List;
import java.util.Map;

/** 严格 JSON Pointer，保留 JSON 字符串存储形式；不支持模糊匹配或按名称查找。 */
final class TransferJsonPath {
    private TransferJsonPath() { }

    static String child(String path, String key) { return path + "/" + key.replace("~", "~0").replace("/", "~1"); }

    static Object read(Object root, String pointer) {
        return visit(root, tokens(pointer), 0, null, false);
    }

    static void replace(Map<String, Object> root, String pointer, Object value) {
        visit(root, tokens(pointer), 0, value, true);
    }

    private static String[] tokens(String pointer) {
        if (pointer == null || !pointer.startsWith("/")) throw new IllegalArgumentException("迁移引用路径无效");
        return pointer.substring(1).split("/", -1);
    }

    @SuppressWarnings("unchecked")
    private static Object visit(Object node, String[] tokens, int offset, Object replacement, boolean write) {
        if (offset == tokens.length) return write ? replacement : node;
        String token = tokens[offset].replace("~1", "/").replace("~0", "~");
        if (node instanceof String text && "@json".equals(token)) {
            Object nested;
            try { nested = JSON.parse(text); }
            catch (RuntimeException error) { throw new IllegalArgumentException("迁移引用指向非法 JSON", error); }
            Object result = visit(nested, tokens, offset + 1, replacement, write);
            return write ? CanonicalJson.write(result) : result;
        }
        if (node instanceof Map<?, ?> raw && raw.containsKey(token)) {
            Map<String, Object> map = (Map<String, Object>) raw;
            Object result = visit(map.get(token), tokens, offset + 1, replacement, write);
            if (write) { map.put(token, result); return map; }
            return result;
        }
        if (node instanceof List<?> raw && token.matches("0|[1-9][0-9]*")) {
            int index;
            try { index = Integer.parseInt(token); }
            catch (NumberFormatException error) { throw new IllegalArgumentException("迁移引用数组索引无效", error); }
            if (index < raw.size()) {
                List<Object> list = (List<Object>) raw;
                Object result = visit(list.get(index), tokens, offset + 1, replacement, write);
                if (write) { list.set(index, result); return list; }
                return result;
            }
        }
        throw new IllegalArgumentException("迁移引用路径不存在: /" + String.join("/", tokens));
    }
}
