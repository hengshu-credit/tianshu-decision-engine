package com.hengshucredit.rule.server.openapi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 只允许确定性对象属性和非负数组下标的 JSONPath。 */
public final class RestrictedJsonPath {

    private RestrictedJsonPath() {
    }

    public static Object read(Object root, String path) {
        List<PathPart> parts = parse(path);
        Object current = root;
        for (PathPart part : parts) {
            if (part.property != null) {
                if (!(current instanceof Map)) return null;
                Map<?, ?> values = (Map<?, ?>) current;
                if (!values.containsKey(part.property)) return null;
                current = values.get(part.property);
            } else {
                if (!(current instanceof List)) return null;
                List<?> values = (List<?>) current;
                if (part.index < 0 || part.index >= values.size()) return null;
                current = values.get(part.index);
            }
        }
        return current;
    }

    private static List<PathPart> parse(String path) {
        if (path == null || path.isEmpty() || path.charAt(0) != '$') {
            throw invalid(path);
        }
        List<PathPart> parts = new ArrayList<>();
        int cursor = 1;
        while (cursor < path.length()) {
            char marker = path.charAt(cursor);
            if (marker == '.') {
                int start = ++cursor;
                while (cursor < path.length() && path.charAt(cursor) != '.' && path.charAt(cursor) != '[') {
                    char current = path.charAt(cursor);
                    if (!isPropertyCharacter(current)) throw invalid(path);
                    cursor++;
                }
                if (start == cursor) throw invalid(path);
                parts.add(PathPart.property(path.substring(start, cursor)));
                continue;
            }
            if (marker == '[') {
                int start = ++cursor;
                while (cursor < path.length() && Character.isDigit(path.charAt(cursor))) cursor++;
                if (start == cursor || cursor >= path.length() || path.charAt(cursor) != ']') throw invalid(path);
                try {
                    parts.add(PathPart.index(Integer.parseInt(path.substring(start, cursor))));
                } catch (NumberFormatException e) {
                    throw invalid(path);
                }
                cursor++;
                continue;
            }
            throw invalid(path);
        }
        return parts;
    }

    private static boolean isPropertyCharacter(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '-';
    }

    private static IllegalArgumentException invalid(String path) {
        return new IllegalArgumentException("仅支持确定性属性和数组下标的受限 JSONPath: " + path);
    }

    private static class PathPart {
        private final String property;
        private final int index;

        private PathPart(String property, int index) {
            this.property = property;
            this.index = index;
        }

        private static PathPart property(String value) {
            return new PathPart(value, -1);
        }

        private static PathPart index(int value) {
            return new PathPart(null, value);
        }
    }
}
