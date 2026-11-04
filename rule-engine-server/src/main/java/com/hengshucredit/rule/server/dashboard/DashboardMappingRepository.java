package com.hengshucredit.rule.server.dashboard;

import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleVariable;

import java.util.Collections;
import java.util.List;

public interface DashboardMappingRepository {

    RuleVariable findVariable(Long id);

    record SystemDefaults(String mappingsJson, long version) { }

    default SystemDefaults findSystemDefaults() {
        return new SystemDefaults("{}", 0L);
    }

    default void saveSystemDefaults(String mappingsJson, long version, String operator) {
        throw new UnsupportedOperationException("公共默认存储未配置");
    }

    default RuleDataObjectField findDataObjectField(Long id) {
        return null;
    }

    default RuleDataObject findDataObject(Long id) {
        return null;
    }

    default List<RuleVariable> listVariables(Long projectId) {
        return Collections.emptyList();
    }

    default List<RuleDataObjectField> listDataObjectFields(Long projectId) {
        return Collections.emptyList();
    }
}
