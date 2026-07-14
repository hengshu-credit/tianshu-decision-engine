package com.hengshucredit.rule.server.functions;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.server.service.ListMatchMatrix;
import org.springframework.stereotype.Service;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** 仅在服务端注册的名单查询函数。 */
@Service("ruleListFunctions")
public class RuleListFunctions {

    private final ListMatchMatrix listMatchMatrix;

    public RuleListFunctions(ListMatchMatrix listMatchMatrix) {
        this.listMatchMatrix = listMatchMatrix;
    }

    public boolean isInLists(Object value, Object listIds) {
        return listMatch(Collections.singletonList(value), listIds,
                ListMatchMatrix.ANY_FIELD_ANY_LIST, "IN_LIST", Collections.emptyList());
    }

    public int isInListsNumber(Object value, Object listIds) {
        return isInLists(value, listIds) ? 1 : 0;
    }

    public boolean listMatch(Object values, Object listIds, String combinationMode,
                             String matchMode, Object itemTypes) {
        return listMatchMatrix.match(toLongList(listIds), toObjectList(values), combinationMode,
                matchMode, toStringList(itemTypes), null);
    }

    public int listMatchNumber(Object values, Object listIds, String combinationMode,
                               String matchMode, Object itemTypes) {
        return listMatch(values, listIds, combinationMode, matchMode, itemTypes) ? 1 : 0;
    }

    private static List<Long> toLongList(Object source) {
        List<Long> result = new ArrayList<>();
        for (Object value : toObjectList(source)) {
            if (value == null || String.valueOf(value).trim().isEmpty()) continue;
            try {
                result.add(value instanceof Number
                        ? ((Number) value).longValue()
                        : Long.valueOf(String.valueOf(value).trim()));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("名单ID必须是整数: " + value, e);
            }
        }
        return result;
    }

    private static List<String> toStringList(Object source) {
        List<String> result = new ArrayList<>();
        for (Object value : toObjectList(source)) {
            if (value != null && !String.valueOf(value).trim().isEmpty()) {
                result.add(String.valueOf(value).trim());
            }
        }
        return result;
    }

    private static List<Object> toObjectList(Object source) {
        Object value = parseJsonArray(source);
        List<Object> result = new ArrayList<>();
        if (value == null) return result;
        if (value instanceof Collection) {
            result.addAll((Collection<?>) value);
        } else if (value.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(value); i++) result.add(Array.get(value, i));
        } else {
            result.add(value);
        }
        return result;
    }

    private static Object parseJsonArray(Object source) {
        if (!(source instanceof String)) return source;
        String text = ((String) source).trim();
        if (!text.startsWith("[") || !text.endsWith("]")) return source;
        try {
            return JSON.parseArray(text);
        } catch (Exception e) {
            throw new IllegalArgumentException("数组参数不是有效JSON: " + source, e);
        }
    }
}
