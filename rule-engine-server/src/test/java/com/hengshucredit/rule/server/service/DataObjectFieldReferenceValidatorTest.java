package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.model.entity.RuleVariable;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDataObjectMapper;
import com.hengshucredit.rule.server.mapper.RuleVariableMapper;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.lang.reflect.Proxy;
import java.util.function.BiFunction;

import static org.junit.Assert.*;

public class DataObjectFieldReferenceValidatorTest {
    private final Map<Long, RuleVariable> variableRows = new HashMap<>();
    private final Map<Long, RuleDataObject> objectRows = new HashMap<>();
    private List<RuleDataObjectField> storedFields = List.of();
    private final RuleVariableMapper variables = mapper(RuleVariableMapper.class,
            (name, args) -> "selectById".equals(name) ? variableRows.get(args[0]) : null);
    private final RuleDataObjectMapper objects = mapper(RuleDataObjectMapper.class,
            (name, args) -> "selectById".equals(name) ? objectRows.get(args[0]) : null);
    private final RuleDataObjectFieldMapper fields = mapper(RuleDataObjectFieldMapper.class,
            (name, args) -> "selectList".equals(name) ? storedFields : null);
    private final DataObjectFieldReferenceValidator validator = new DataObjectFieldReferenceValidator(variables, objects, fields);
    private RuleDataObject owner;
    private RuleDataObjectField field;
    private RuleVariable variable;
    private RuleDataObject target;

    @Before
    public void setUp() {
        owner = new RuleDataObject();
        owner.setId(4L);
        owner.setScope("PROJECT");
        owner.setProjectId(2L);
        field = new RuleDataObjectField();
        field.setId(30L);
        field.setVarCode("Alias");
        field.setVarType("NUMBER");
        variable = new RuleVariable();
        variable.setId(9L);
        variable.setScope("GLOBAL");
        variable.setStatus(1);
        variable.setVarType("NUMBER");
        variable.setVarSource("CONSTANT");
        target = new RuleDataObject();
        target.setId(9L);
        target.setStatus(1);
        target.setScope("GLOBAL");
        variableRows.put(9L, variable);
        objectRows.put(9L, target);
        objectRows.put(4L, owner);
        storedFields = List.of();
    }

    @Test
    public void constantsRequireCompatibleTypeStateAndScope() {
        field.setRefVariableId(9L);
        assertTrue(validator.validate(owner, field, List.of()).isEmpty());
        variable.setVarType("STRING");
        assertCode("VARIABLE_TYPE_MISMATCH");
        variable.setVarType("NUMBER");
        variable.setScope("PROJECT");
        variable.setProjectId(3L);
        assertCode("VARIABLE_SCOPE_MISMATCH");
        variable.setStatus(0);
        assertCode("VARIABLE_DISABLED");
    }

    @Test
    public void objectBindingsRequireObjectOrObjectListType() {
        field.setRefObjectId(9L);
        assertCode("OBJECT_TYPE_MISMATCH");
        field.setVarType("OBJECT");
        assertTrue(validator.validate(owner, field, List.of()).isEmpty());
        field.setVarType("LIST");
        field.setGenericType("STRING");
        assertCode("OBJECT_TYPE_MISMATCH");
        field.setGenericType("OBJECT");
        assertTrue(validator.validate(owner, field, List.of()).isEmpty());
    }

    @Test
    public void globalObjectCannotBindAProjectObject() {
        owner.setScope("GLOBAL");
        field.setVarType("OBJECT");
        field.setRefObjectId(9L);
        target.setScope("PROJECT");
        target.setProjectId(2L);
        assertCode("OBJECT_SCOPE_MISMATCH");
    }

    @Test
    public void directAndIndirectObjectCyclesAreRejected() {
        field.setVarType("OBJECT");
        field.setRefObjectId(4L);
        owner.setStatus(1);
        assertCode("OBJECT_REFERENCE_CYCLE");
        field.setRefObjectId(9L);
        RuleDataObjectField backReference = new RuleDataObjectField();
        backReference.setRefObjectId(4L);
        storedFields = List.of(backReference);
        assertCode("OBJECT_REFERENCE_CYCLE");
    }

    @Test
    public void conflictingInlineObjectStructureIsRejectedBeforeBinding() {
        field.setVarType("OBJECT");
        field.setRefObjectId(9L);
        RuleDataObjectField inline = new RuleDataObjectField();
        inline.setParentFieldId(30L);
        inline.setVarCode("City");
        RuleDataObjectField referenced = new RuleDataObjectField();
        referenced.setVarCode("City");
        storedFields = List.of(referenced);
        assertTrue(validator.validate(owner, field, List.of(inline)).stream()
                .anyMatch(issue -> issue.getCode().endsWith("OBJECT_FIELD_CONFLICT")));
    }

    @Test
    public void ambiguousReferencesAndCodeOnlyBindingsAreRejected() {
        field.setRefVariableId(9L);
        field.setRefObjectId(9L);
        assertCode("REFERENCE_CONFLICT");
        field.setRefVariableId(null);
        field.setRefObjectId(null);
        field.setRefObjectCode("Address");
        assertCode("OBJECT_ID_REQUIRED");
    }

    @Test
    public void listChildrenCannotUseScalarFallbackValues() {
        field.setParentFieldId(1L);
        field.setRefVariableId(9L);
        RuleDataObjectField parent = new RuleDataObjectField();
        parent.setId(1L);
        parent.setVarType("LIST");
        assertTrue(validator.validate(owner, field, List.of(parent)).stream()
                .anyMatch(issue -> issue.getCode().endsWith("LIST_CHILD_REFERENCE_UNSUPPORTED")));
    }

    @Test
    public void validationEndpointRejectsAFieldFromAnotherOwner() {
        assertThrows(IllegalArgumentException.class, () -> validator.validateCandidate(4L, field));
    }

    @Test
    public void clearingBindingsWritesNullInsteadOfRetainingOldDatabaseIds() throws Exception {
        for (String property : List.of("refVariableId", "refObjectId", "refObjectCode", "genericType")) {
            assertEquals(property, FieldStrategy.ALWAYS,
                    RuleDataObjectField.class.getDeclaredField(property).getAnnotation(TableField.class).updateStrategy());
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T mapper(Class<T> type, BiFunction<String, Object[], Object> handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> handler.apply(method.getName(), args));
    }

    private void assertCode(String suffix) {
        assertTrue(suffix, validator.validate(owner, field, List.of()).stream()
                .anyMatch(issue -> issue.getCode().equals("DATA_OBJECT_FIELD_" + suffix)));
    }
}
