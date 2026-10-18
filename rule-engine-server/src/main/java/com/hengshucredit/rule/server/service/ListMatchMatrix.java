package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.core.util.MissingValueSemantics;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 统一执行“多个查询值 × 多个名单库”的命中矩阵。 */
@Service
public class ListMatchMatrix {

    public static final String ANY_FIELD_ANY_LIST = "ANY_FIELD_ANY_LIST";
    public static final String ALL_FIELDS_ANY_LIST = "ALL_FIELDS_ANY_LIST";
    public static final String ANY_FIELD_ALL_LISTS = "ANY_FIELD_ALL_LISTS";
    public static final String ALL_FIELDS_ALL_LISTS = "ALL_FIELDS_ALL_LISTS";

    private final RuleListService ruleListService;

    public ListMatchMatrix(RuleListService ruleListService) {
        this.ruleListService = ruleListService;
    }

    public boolean match(List<Long> listIds, List<Object> values, String combinationMode,
                         String matchMode, List<String> itemTypes, LocalDateTime matchTime) {
        validateCombinationMode(combinationMode);
        if (listIds == null || listIds.isEmpty() || values == null || values.isEmpty()) {
            return false;
        }
        if (ruleListService == null) {
            throw new IllegalStateException("名单匹配服务未初始化");
        }
        Map<String, Boolean> cells;
        try {
            cells = ruleListService.matchCells(listIds, values, itemTypes, matchMode, matchTime);
        } catch (com.baomidou.mybatisplus.core.exceptions.MybatisPlusException uninitializedStore) {
            cells = new java.util.LinkedHashMap<>();
            for (Long listId : listIds) {
                for (Object value : values) {
                    cells.put(String.valueOf(listId) + '\u0000' + String.valueOf(value),
                            ruleListService.match(listId, value, itemTypes, matchMode));
                }
            }
        }
        if (ANY_FIELD_ANY_LIST.equals(combinationMode)) {
            for (Object value : values) {
                for (Long listId : listIds) {
                    if (cell(cells, listId, value)) return true;
                }
            }
            return false;
        }
        if (ALL_FIELDS_ANY_LIST.equals(combinationMode)) {
            for (Object value : values) {
                boolean fieldMatched = false;
                for (Long listId : listIds) {
                    if (cell(cells, listId, value)) {
                        fieldMatched = true;
                        break;
                    }
                }
                if (!fieldMatched) return false;
            }
            return true;
        }
        if (ANY_FIELD_ALL_LISTS.equals(combinationMode)) {
            for (Object value : values) {
                boolean fieldMatched = true;
                for (Long listId : listIds) {
                    if (!cell(cells, listId, value)) {
                        fieldMatched = false;
                        break;
                    }
                }
                if (fieldMatched) return true;
            }
            return false;
        }
        for (Object value : values) {
            for (Long listId : listIds) {
                if (!cell(cells, listId, value)) return false;
            }
        }
        return true;
    }

    private boolean cell(Map<String, Boolean> cells, Long listId, Object value) {
        if (MissingValueSemantics.isMissing(value)) return false;
        return Boolean.TRUE.equals(cells.get(cellKey(listId, value)));
    }

    private String cellKey(Long listId, Object value) {
        return String.valueOf(listId) + '\u0000' + String.valueOf(value);
    }

    private static void validateCombinationMode(String mode) {
        if (!ANY_FIELD_ANY_LIST.equals(mode) && !ALL_FIELDS_ANY_LIST.equals(mode)
                && !ANY_FIELD_ALL_LISTS.equals(mode) && !ALL_FIELDS_ALL_LISTS.equals(mode)) {
            throw new IllegalArgumentException("不支持的名单组合模式: " + mode);
        }
    }
}
