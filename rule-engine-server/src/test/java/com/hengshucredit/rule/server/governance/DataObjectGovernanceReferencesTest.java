package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class DataObjectGovernanceReferencesTest {
    @Test public void wholeObjectDependencyNeverLooksUpAnUnrelatedFieldWithTheSameId() {
        RuleDataObjectFieldMapper fields = (RuleDataObjectFieldMapper) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{RuleDataObjectFieldMapper.class}, (proxy, method, args) -> { throw new AssertionError("根对象不能查字段表"); });
        var refs = SimpleEntityGovernedResourceAdapter.dependenciesIn("{\"value\":{\"kind\":\"PATH\",\"refType\":\"DATA_OBJECT_ROOT\",\"refId\":42,\"relativePath\":\".score\"}}");
        assertEquals(1, refs.size());
        var root = DataObjectGovernanceReferences.owners(refs, fields).get(0);
        assertEquals("DATA_OBJECT", root.targetResourceType());
        assertEquals("DATA_OBJECT_ROOT", root.refType());
        assertEquals(Long.valueOf(42), root.targetResourceId());
    }
    @Test
    public void designerFieldAliasesResolveTheOwnerWhileObjectReferencesKeepTheirId() {
        RuleDataObjectFieldMapper fields = (RuleDataObjectFieldMapper) Proxy.newProxyInstance(
                RuleDataObjectFieldMapper.class.getClassLoader(), new Class<?>[]{RuleDataObjectFieldMapper.class},
                (proxy, method, args) -> {
                    assertEquals("selectById", method.getName());
                    assertEquals(42L, args[0]);
                    RuleDataObjectField field = new RuleDataObjectField();
                    field.setId(42L);
                    field.setObjectId(6L);
                    return field;
                });
        for (String name : List.of("varId", "refId", "_varId", "leftVarId", "rightVarId",
                "_targetVarId", "_condVarId", "_matchVarId", "_checkVarId", "_rightVarId", "refObjectId")) {
            String path = "$.content.modelJson[json].condition." + name;
            ResourceDependencyRef ref = new ResourceDependencyRef("DATA_OBJECT", 42L, "DATA_OBJECT", path, "REFERENCES", true);
            ResourceDependencyRef resolved = DataObjectGovernanceReferences.owners(List.of(ref), fields).get(0);
            assertEquals(path, resolved.referencePath());
            assertEquals(name, Long.valueOf("refObjectId".equals(name) ? 42L : 6L), resolved.targetResourceId());
        }
    }
}
