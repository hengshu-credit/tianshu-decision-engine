package com.hengshucredit.rule.server.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

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
        if (ANY_FIELD_ANY_LIST.equals(combinationMode)) {
            for (Object value : values) {
                for (Long listId : listIds) {
                    if (cell(listId, value, itemTypes, matchMode, matchTime)) return true;
                }
            }
            return false;
        }
        if (ALL_FIELDS_ANY_LIST.equals(combinationMode)) {
            for (Object value : values) {
                boolean fieldMatched = false;
                for (Long listId : listIds) {
                    if (cell(listId, value, itemTypes, matchMode, matchTime)) {
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
                    if (!cell(listId, value, itemTypes, matchMode, matchTime)) {
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
                if (!cell(listId, value, itemTypes, matchMode, matchTime)) return false;
            }
        }
        return true;
    }

    private boolean cell(Long listId, Object value, List<String> itemTypes,
                         String matchMode, LocalDateTime matchTime) {
        String effectiveMatchMode = matchMode == null || matchMode.trim().isEmpty() ? "IN_LIST" : matchMode;
        return matchTime == null
                ? ruleListService.match(listId, value, itemTypes, effectiveMatchMode)
                : ruleListService.matchAt(listId, value, itemTypes, effectiveMatchMode, matchTime);
    }

    private static void validateCombinationMode(String mode) {
        if (!ANY_FIELD_ANY_LIST.equals(mode) && !ALL_FIELDS_ANY_LIST.equals(mode)
                && !ANY_FIELD_ALL_LISTS.equals(mode) && !ALL_FIELDS_ALL_LISTS.equals(mode)) {
            throw new IllegalArgumentException("不支持的名单组合模式: " + mode);
        }
    }
}
