package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hengshucredit.rule.model.dto.RuleValidationIssue;
import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDataObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("unchecked")
public class DataObjectSchemaResolverTest {

    private Fixture fixture;

    @Before
    public void setUp() {
        fixture = new Fixture();
    }

    @Test
    public void objectWithoutRefObjectUsesParentFieldChildren() {
        RuleDataObjectField object = field(42L, "features", "OBJECT", null, null);
        fixture.fields(object, child(101L, 42L, "apply_count", "INTEGER"));
        DataObjectSchemaResolver.ShapeResult result = fixture.resolver.resolveField(42L, 4L);
        assertEquals("object", result.getSchema().get("type"));
        assertTrue(properties(result).containsKey("apply_count"));
        assertEquals("INTEGER", result.getLeafTypesByPath().get("apply_count"));
    }

    @Test
    public void rootSchemaUsesLocalPropertyNamesForImportedArrayMembers() {
        fixture.object(10L, activeObject(10L, 4L));
        var items = rootField(42L, 10L, "items", "LIST");
        var name = child(43L, 42L, "User_ID", "STRING");
        name.setScriptName("items.User_ID");
        fixture.fields(items, name);
        var result = fixture.resolver.resolveRoot(10L, 4L);
        assertTrue(result.getDiagnostics().isEmpty());
        assertEquals(Map.of("items[].User_ID", "STRING"), result.getLeafTypesByPath());
    }

    @Test
    public void listOfObjectWithoutGenericObjectUsesInlineChildren() {
        RuleDataObjectField list = field(142L, "app_list", "LIST", null, null);
        fixture.fields(list, child(143L, 142L, "app_name", "STRING"));
        DataObjectSchemaResolver.ShapeResult result = fixture.resolver.resolveField(142L, 4L);
        assertEquals("array", result.getSchema().get("type"));
        assertEquals("object", items(result).get("type"));
        assertTrue(itemProperties(result).containsKey("app_name"));
        assertEquals("STRING", result.getLeafTypesByPath().get("[].app_name"));
    }

    @Test
    public void missingObjectShapeProducesOpenObjectWarning() {
        fixture.fields(field(42L, "features", "OBJECT", null, null));
        DataObjectSchemaResolver.ShapeResult result = fixture.resolver.resolveField(42L, 4L);
        assertEquals(Boolean.TRUE, result.getSchema().get("additionalProperties"));
        assertTrue(codes(result).contains("OBJECT_SHAPE_INCOMPLETE"));
    }

    @Test
    public void missingListItemShapeProducesOpenItemsWarning() {
        fixture.fields(field(142L, "app_list", "LIST", null, "OBJECT"));
        DataObjectSchemaResolver.ShapeResult result = fixture.resolver.resolveField(142L, 4L);
        assertEquals(Collections.emptyMap(), result.getSchema().get("items"));
        assertTrue(codes(result).contains("OBJECT_SHAPE_INCOMPLETE"));
    }

    @Test
    public void referencedObjectUsesRefObjectIdInsteadOfRefObjectCode() {
        RuleDataObjectField root = field(42L, "features", "OBJECT", 11L, null);
        root.setRefObjectCode("ignored_code");
        fixture.object(11L, activeObject(11L, 4L));
        fixture.fields(root, rootField(101L, 11L, "credit_score_v1", "NUMBER"));
        DataObjectSchemaResolver.ShapeResult result = fixture.resolver.resolveField(42L, 4L);
        assertTrue(properties(result).containsKey("credit_score_v1"));
        assertFalse(properties(result).containsKey("ignored_code"));
    }

    @Test
    public void inlineAndReferencedObjectFieldsAreMergedByStableFieldId() {
        RuleDataObjectField root = field(42L, "features", "OBJECT", 11L, null);
        fixture.object(11L, activeObject(11L, 4L));
        fixture.fields(root,
                child(102L, 42L, "inline_flag", "BOOLEAN"),
                rootField(101L, 11L, "credit_score_v1", "NUMBER"));

        DataObjectSchemaResolver.ShapeResult result = fixture.resolver.resolveField(42L, 4L);

        assertTrue(properties(result).containsKey("inline_flag"));
        assertTrue(properties(result).containsKey("credit_score_v1"));
    }

    @Test
    public void differentIdsWithSamePropertyNameProduceConflictInsteadOfOverwrite() {
        RuleDataObjectField root = field(42L, "features", "OBJECT", 11L, null);
        fixture.object(11L, activeObject(11L, 4L));
        fixture.fields(root,
                child(102L, 42L, "score", "STRING"),
                rootField(101L, 11L, "score", "NUMBER"));

        DataObjectSchemaResolver.ShapeResult result = fixture.resolver.resolveField(42L, 4L);

        assertTrue(codes(result).contains("OBJECT_SHAPE_CONFLICT"));
        assertEquals("string",
                ((Map<?, ?>) properties(result).get("score")).get("type"));
    }

    @Test
    public void parentAndReferencedObjectCycleIsRejected() {
        RuleDataObjectField first = field(42L, "first", "OBJECT", 11L, null);
        RuleDataObjectField second = rootField(43L, 11L, "second", "OBJECT");
        second.setRefObjectId(10L);
        fixture.object(10L, activeObject(10L, 4L));
        fixture.object(11L, activeObject(11L, 4L));
        fixture.fields(first, second);
        DataObjectSchemaResolver.ShapeResult result = fixture.resolver.resolveField(42L, 4L);
        assertTrue(codes(result).contains("DEPENDENCY_CYCLE"));
    }

    @Test
    public void entryFieldRejectsDisabledOwningObject() {
        RuleDataObject disabled = activeObject(10L, 4L);
        disabled.setStatus(0);
        fixture.object(10L, disabled);
        fixture.fields(field(42L, "features", "OBJECT", null, null),
                child(101L, 42L, "score", "NUMBER"));

        DataObjectSchemaResolver.ShapeResult result =
                fixture.resolver.resolveField(42L, 4L);

        assertTrue(result.getSchema().isEmpty());
        assertTrue(codes(result).contains("REFERENCE_TYPE_MISMATCH"));
    }

    @Test
    public void entryFieldRejectsCrossProjectOwningObject() {
        fixture.object(10L, activeObject(10L, 9L));
        fixture.fields(field(42L, "features", "OBJECT", null, null),
                child(101L, 42L, "score", "NUMBER"));

        DataObjectSchemaResolver.ShapeResult result =
                fixture.resolver.resolveField(42L, 4L);

        assertTrue(result.getSchema().isEmpty());
        assertTrue(codes(result).contains("REFERENCE_TYPE_MISMATCH"));
    }

    @Test
    public void sharedReferencedObjectIsNotReportedAsCycle() {
        RuleDataObjectField root = field(42L, "features", "OBJECT", 11L, null);
        RuleDataObjectField left = rootField(43L, 11L, "left", "OBJECT");
        left.setRefObjectId(12L);
        RuleDataObjectField right = rootField(44L, 11L, "right", "OBJECT");
        right.setRefObjectId(12L);
        fixture.object(11L, activeObject(11L, 4L));
        fixture.object(12L, activeObject(12L, 4L));
        fixture.fields(root, left, right,
                rootField(45L, 12L, "score", "NUMBER"));

        DataObjectSchemaResolver.ShapeResult result =
                fixture.resolver.resolveField(42L, 4L);

        assertFalse(codes(result).contains("DEPENDENCY_CYCLE"));
        assertTrue(properties(result).containsKey("left"));
        assertTrue(properties(result).containsKey("right"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shapeResultDefensivelyCopiesSchemaAndLeafTypeCollections() {
        fixture.fields(field(42L, "features", "OBJECT", null, null),
                child(101L, 42L, "apply_count", "INTEGER"));

        DataObjectSchemaResolver.ShapeResult result = fixture.resolver.resolveField(42L, 4L);
        Map<String, Object> firstSchema = result.getSchema();
        ((Map<String, Object>) firstSchema.get("properties")).clear();

        assertTrue(properties(result).containsKey("apply_count"));
        assertThrows(UnsupportedOperationException.class,
                () -> result.getLeafTypesByPath().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> result.getDiagnostics().clear());
    }

    private static RuleDataObject activeObject(Long id, Long projectId) {
        RuleDataObject object = new RuleDataObject();
        object.setId(id);
        object.setProjectId(projectId);
        object.setScope("PROJECT");
        object.setObjectCode("object_" + id);
        object.setScriptName("object_" + id);
        object.setStatus(1);
        return object;
    }

    private static RuleDataObjectField field(Long id, String code, String type,
                                             Long refObjectId, String genericType) {
        RuleDataObjectField field = rootField(id, 10L, code, type);
        field.setRefObjectId(refObjectId);
        field.setGenericType(genericType);
        return field;
    }

    private static RuleDataObjectField child(Long id, Long parentId,
                                             String code, String type) {
        RuleDataObjectField field = rootField(id, 10L, code, type);
        field.setParentFieldId(parentId);
        return field;
    }

    private static RuleDataObjectField rootField(Long id, Long objectId,
                                                 String code, String type) {
        RuleDataObjectField field = new RuleDataObjectField();
        field.setId(id);
        field.setProjectId(4L);
        field.setScope("PROJECT");
        field.setObjectId(objectId);
        field.setVarCode(code);
        field.setVarLabel(code);
        field.setScriptName(code);
        field.setVarType(type);
        field.setSortOrder(id.intValue());
        field.setStatus(1);
        return field;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(DataObjectSchemaResolver.ShapeResult result) {
        return (Map<String, Object>) result.getSchema().get("properties");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> items(DataObjectSchemaResolver.ShapeResult result) {
        return (Map<String, Object>) result.getSchema().get("items");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> itemProperties(DataObjectSchemaResolver.ShapeResult result) {
        return (Map<String, Object>) items(result).get("properties");
    }

    private static Set<String> codes(DataObjectSchemaResolver.ShapeResult result) {
        return result.getDiagnostics().stream()
                .map(RuleValidationIssue::getCode)
                .collect(Collectors.toSet());
    }

    private static final class Fixture {

        private final Map<Long, RuleDataObject> objects = new LinkedHashMap<>();
        private final Map<Long, RuleDataObjectField> fields = new LinkedHashMap<>();
        private final DataObjectSchemaResolver resolver;

        private Fixture() {
            objects.put(10L, activeObject(10L, 4L));
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new Configuration(), ""), RuleDataObjectField.class);
            RuleDataObjectMapper objectMapper = (RuleDataObjectMapper) Proxy.newProxyInstance(
                    RuleDataObjectMapper.class.getClassLoader(),
                    new Class<?>[]{RuleDataObjectMapper.class},
                    (proxy, method, args) -> "selectById".equals(method.getName())
                            ? objects.get(((Number) args[0]).longValue()) : null);
            RuleDataObjectFieldMapper fieldMapper =
                    (RuleDataObjectFieldMapper) Proxy.newProxyInstance(
                            RuleDataObjectFieldMapper.class.getClassLoader(),
                            new Class<?>[]{RuleDataObjectFieldMapper.class},
                            (proxy, method, args) -> {
                                if ("selectById".equals(method.getName())) {
                                    return fields.get(((Number) args[0]).longValue());
                                }
                                if ("selectList".equals(method.getName())) {
                                    return selectFields((LambdaQueryWrapper<RuleDataObjectField>) args[0]);
                                }
                                return null;
                            });
            resolver = new DataObjectSchemaResolver(objectMapper, fieldMapper);
        }

        private void object(Long id, RuleDataObject object) {
            objects.put(id, object);
        }

        private void fields(RuleDataObjectField... values) {
            Arrays.stream(values).forEach(value -> fields.put(value.getId(), value));
        }

        private List<RuleDataObjectField> selectFields(
                LambdaQueryWrapper<RuleDataObjectField> wrapper) {
            String sql = wrapper.getSqlSegment();
            Long id = wrapper.getParamNameValuePairs().values().stream()
                    .filter(Long.class::isInstance)
                    .map(Long.class::cast)
                    .findFirst()
                    .orElse(null);
            return fields.values().stream()
                    .filter(value -> Integer.valueOf(1).equals(value.getStatus()))
                    .filter(value -> {
                        if (sql.contains("parent_field_id")
                                || sql.contains("parentFieldId")) {
                            return Objects.equals(value.getParentFieldId(), id);
                        }
                        if (sql.contains("object_id")
                                || sql.contains("objectId")) {
                            return Objects.equals(value.getObjectId(), id);
                        }
                        return false;
                    })
                    .collect(Collectors.toList());
        }
    }
}
