package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Masks configured trace paths only in the persisted trace copy. */
final class TracePayloadSanitizer {
    private TracePayloadSanitizer() { }

    static String mask(String json, String paths) {
        if (json == null || paths == null || paths.isBlank()) return json;
        Object root;
        try {
            root = JSON.parse(json);
        } catch (RuntimeException ignored) {
            return json;
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String item : paths.split(",")) {
            String path = item == null ? "" : item.trim();
            if (!path.isEmpty() && path.startsWith("$")) normalized.add(path);
        }
        for (String path : normalized) {
            try {
                maskAtPath(root, parsePath(path), 0);
            } catch (RuntimeException ignored) {
                // One invalid optional mask path must not break rule execution or log persistence.
            }
        }
        return JSON.toJSONString(root);
    }

    private static void maskAtPath(Object current, List<Object> tokens, int index) {
        if (current == null || index >= tokens.size() - 1) return;
        Object token = tokens.get(index + 1);
        if (WILDCARD.equals(token)) {
            if (current instanceof List<?>) {
                for (Object item : (List<?>) current) maskAtPath(item, tokens, index + 1);
            }
            return;
        }
        if (index + 1 == tokens.size() - 1) {
            if (current instanceof JSONObject map && token instanceof String) {
                map.put((String) token, "******");
            } else if (current instanceof JSONArray list && token instanceof Integer) {
                int position = (Integer) token;
                if (position >= 0 && position < list.size()) {
                    list.set(position, "******");
                }
            }
            return;
        }
        Object next = null;
        if (current instanceof java.util.Map<?, ?> && token instanceof String) next = ((java.util.Map<?, ?>) current).get(token);
        else if (current instanceof List<?> && token instanceof Integer) {
            int position = (Integer) token;
            if (position >= 0 && position < ((List<?>) current).size()) next = ((List<?>) current).get(position);
        }
        if (next != null) maskAtPath(next, tokens, index + 1);
    }

    private static List<Object> parsePath(String value) {
        if (value == null || value.isBlank() || !value.startsWith("$")) return List.of();
        List<Object> tokens = new ArrayList<>();
        tokens.add("$");
        int index = 1;
        while (index < value.length()) {
            if (value.charAt(index) == '.') {
                int end = index + 1;
                while (end < value.length() && value.charAt(end) != '.' && value.charAt(end) != '[') end++;
                if (end == index + 1) return List.of();
                tokens.add(value.substring(index + 1, end));
                index = end;
            } else if (value.charAt(index) == '[') {
                int end = value.indexOf(']', index);
                if (end < 0) return List.of();
                String token = value.substring(index + 1, end);
                if ("*".equals(token)) tokens.add(WILDCARD);
                else if (token.matches("\\d+")) tokens.add(Integer.valueOf(token));
                else if (token.length() >= 2 && token.charAt(0) == '\'' && token.charAt(token.length() - 1) == '\'') tokens.add(token.substring(1, token.length() - 1));
                else return List.of();
                index = end + 1;
            } else return List.of();
        }
        return tokens;
    }

    private static final String WILDCARD = "[*]";
}
