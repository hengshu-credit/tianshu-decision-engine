package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class RuleDataObjectServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    public void buildVariableTreeSimplifiesLegacyDuplicatedObjectPrefixForDisplay() throws Exception {
        RuleDataObjectField params = field(1L, null, "request.request.params", "OBJECT");
        RuleDataObjectField taxpayerType = field(2L, 1L, "request.request.params.taxpayerType", "STRING");

        Method method = RuleDataObjectService.class.getDeclaredMethod("buildNestedVariableRows", List.class, String.class);
        method.setAccessible(true);

        List<Map<String, Object>> rows = (List<Map<String, Object>>) method.invoke(null, Arrays.asList(params, taxpayerType), "request");
        Map<String, Object> paramsRow = rows.get(0);
        List<Map<String, Object>> children = (List<Map<String, Object>>) paramsRow.get("children");
        Map<String, Object> taxpayerTypeRow = children.get(0);

        assertEquals("params", paramsRow.get("varCode"));
        assertEquals("params", paramsRow.get("varLabel"));
        assertEquals("request.params", paramsRow.get("scriptName"));
        assertEquals("taxpayerType", taxpayerTypeRow.get("varCode"));
        assertEquals("taxpayerType", taxpayerTypeRow.get("varLabel"));
        assertEquals("request.params.taxpayerType", taxpayerTypeRow.get("scriptName"));
    }

    private RuleDataObjectField field(Long id, Long parentId, String path, String varType) {
        RuleDataObjectField field = new RuleDataObjectField();
        field.setId(id);
        field.setProjectId(0L);
        field.setScope("GLOBAL");
        field.setObjectId(10L);
        field.setParentFieldId(parentId);
        field.setVarCode(path);
        field.setVarLabel(path);
        field.setScriptName(path);
        field.setVarType(varType);
        field.setSortOrder(id.intValue());
        field.setStatus(1);
        return field;
    }
}
