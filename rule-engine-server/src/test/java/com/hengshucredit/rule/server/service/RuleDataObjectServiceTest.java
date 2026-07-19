package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hengshucredit.rule.model.entity.RuleDataObject;
import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import com.hengshucredit.rule.server.mapper.RuleDataObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.Test;
import org.junit.Assert;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RuleDataObjectServiceTest {

    @Test
    public void toGlobalConvertsObjectAndOwnedFieldsWithoutChangingCodes() {
        AtomicInteger objectUpdates = new AtomicInteger();
        AtomicInteger fieldUpdates = new AtomicInteger();
        AtomicReference<LambdaUpdateWrapper<RuleDataObject>> objectWrapper = new AtomicReference<>();
        AtomicReference<LambdaUpdateWrapper<RuleDataObjectField>> fieldWrapper = new AtomicReference<>();
        RuleDataObject object = object(10L, "TSRequestBody", "PROJECT", 7L);
        List<RuleDataObjectField> fields = Arrays.asList(
                field(101L, null, "name", "STRING"),
                field(102L, null, "age", "NUMBER"));
        fields.forEach(field -> {
            field.setScope("PROJECT");
            field.setProjectId(7L);
            field.setObjectId(10L);
        });
        RuleDataObjectService service = toGlobalService(object, 0L, Collections.emptyMap(), fields,
                1, objectUpdates, objectWrapper, fieldUpdates, fieldWrapper);

        service.toGlobal(10L);

        Assert.assertEquals(1, objectUpdates.get());
        Assert.assertEquals(1, fieldUpdates.get());
        Assert.assertFalse(objectWrapper.get().getSqlSet().contains("objectCode"));
        Assert.assertTrue(objectWrapper.get().getParamNameValuePairs().containsValue(0L));
        String fieldCondition = fieldWrapper.get().getSqlSegment().replace("`", "").toLowerCase();
        Assert.assertTrue(fieldCondition, fieldCondition.contains("object_id") || fieldCondition.contains("objectid"));
        Assert.assertTrue(fieldWrapper.get().getParamNameValuePairs().containsValue(0L));
    }

    @Test
    public void toGlobalRejectsConflictingGlobalObjectCode() {
        AtomicInteger objectUpdates = new AtomicInteger();
        RuleDataObjectService service = toGlobalService(
                object(10L, "TSRequestBody", "PROJECT", 7L), 1L, Collections.emptyMap(),
                Collections.emptyList(), 1, objectUpdates, new AtomicReference<>(),
                new AtomicInteger(), new AtomicReference<>());

        assertToGlobalRejected(service, 10L, "全局数据对象");

        Assert.assertEquals(0, objectUpdates.get());
    }

    @Test
    public void toGlobalRejectsProjectLevelReferencedObject() {
        RuleDataObjectField referencedField = field(101L, null, "address", "OBJECT");
        referencedField.setObjectId(10L);
        referencedField.setRefObjectId(20L);
        referencedField.setRefObjectCode("Address");
        Map<Long, RuleDataObject> references = new HashMap<>();
        references.put(20L, object(20L, "Address", "PROJECT", 7L));
        AtomicInteger objectUpdates = new AtomicInteger();
        RuleDataObjectService service = toGlobalService(
                object(10L, "TSRequestBody", "PROJECT", 7L), 0L, references,
                Collections.singletonList(referencedField), 1, objectUpdates, new AtomicReference<>(),
                new AtomicInteger(), new AtomicReference<>());

        assertToGlobalRejected(service, 10L, "DATA_OBJECT:20");

        Assert.assertEquals(0, objectUpdates.get());
    }

    @Test
    public void regularUpdateCannotBypassGlobalConversionValidation() {
        AtomicInteger objectUpdates = new AtomicInteger();
        RuleDataObjectService service = toGlobalService(
                object(10L, "TSRequestBody", "PROJECT", 7L), 0L, Collections.emptyMap(),
                Collections.emptyList(), 1, objectUpdates, new AtomicReference<>(),
                new AtomicInteger(), new AtomicReference<>());
        RuleDataObject edit = object(10L, "TSRequestBody", "GLOBAL", 0L);

        try {
            service.updateById(edit);
            Assert.fail("Expected scope change through regular update to fail");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("转为全局"));
        }

        Assert.assertEquals(0, objectUpdates.get());
    }

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

    @Test
    @SuppressWarnings("unchecked")
    public void objectManagementQueriesOrderByLatestUpdateThenId() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), RuleDataObject.class);
        List<String> sqlSegments = new ArrayList<>();
        RuleDataObjectMapper mapper = (RuleDataObjectMapper) Proxy.newProxyInstance(
                RuleDataObjectMapper.class.getClassLoader(), new Class<?>[]{RuleDataObjectMapper.class},
                (proxy, method, args) -> {
                    if ("selectList".equals(method.getName())) {
                        sqlSegments.add(((LambdaQueryWrapper<RuleDataObject>) args[0]).getSqlSegment());
                        return Collections.emptyList();
                    }
                    if ("selectPage".equals(method.getName())) {
                        sqlSegments.add(((LambdaQueryWrapper<RuleDataObject>) args[1]).getSqlSegment());
                        return args[0];
                    }
                    return null;
                });
        RuleDataObjectService service = new RuleDataObjectService();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);

        service.listByProject(7L);
        service.pageList(1, 10, null, null, null, null, null, null);
        service.getVariableTreeAll();

        assertEquals(3, sqlSegments.size());
        for (String sqlSegment : sqlSegments) {
            assertLatestUpdateOrder(sqlSegment);
        }
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

    private static RuleDataObject object(Long id, String code, String scope, Long projectId) {
        RuleDataObject object = new RuleDataObject();
        object.setId(id);
        object.setObjectCode(code);
        object.setObjectLabel(code);
        object.setScope(scope);
        object.setProjectId(projectId);
        object.setStatus(1);
        return object;
    }

    @SuppressWarnings("unchecked")
    private static RuleDataObjectService toGlobalService(
            RuleDataObject object, long conflictCount, Map<Long, RuleDataObject> references,
            List<RuleDataObjectField> fields, int objectUpdateResult,
            AtomicInteger objectUpdates, AtomicReference<LambdaUpdateWrapper<RuleDataObject>> objectWrapper,
            AtomicInteger fieldUpdates, AtomicReference<LambdaUpdateWrapper<RuleDataObjectField>> fieldWrapper) {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), RuleDataObject.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), RuleDataObjectField.class);
        RuleDataObjectMapper mapper = (RuleDataObjectMapper) Proxy.newProxyInstance(
                RuleDataObjectMapper.class.getClassLoader(), new Class<?>[]{RuleDataObjectMapper.class},
                (proxy, method, args) -> {
                    if ("selectById".equals(method.getName())) {
                        Long id = ((Number) args[0]).longValue();
                        return object.getId().equals(id) ? object : references.get(id);
                    }
                    if ("selectCount".equals(method.getName())) return conflictCount;
                    if ("update".equals(method.getName())) {
                        objectUpdates.incrementAndGet();
                        objectWrapper.set((LambdaUpdateWrapper<RuleDataObject>) args[1]);
                        return objectUpdateResult;
                    }
                    return defaultValue(method.getReturnType());
                });
        RuleDataObjectFieldMapper fieldMapper = (RuleDataObjectFieldMapper) Proxy.newProxyInstance(
                RuleDataObjectFieldMapper.class.getClassLoader(), new Class<?>[]{RuleDataObjectFieldMapper.class},
                (proxy, method, args) -> {
                    if ("selectList".equals(method.getName())) return fields;
                    if ("update".equals(method.getName())) {
                        fieldUpdates.incrementAndGet();
                        fieldWrapper.set((LambdaUpdateWrapper<RuleDataObjectField>) args[1]);
                        return fields.size();
                    }
                    return defaultValue(method.getReturnType());
                });
        RuleDataObjectService service = new RuleDataObjectService();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        ReflectionTestUtils.setField(service, "objectFieldMapper", fieldMapper);
        return service;
    }

    private static void assertToGlobalRejected(RuleDataObjectService service, Long objectId, String messagePart) {
        try {
            service.toGlobal(objectId);
            Assert.fail("Expected data object global conversion to fail");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(messagePart));
        }
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == boolean.class) return false;
        if (returnType == int.class) return 0;
        if (returnType == long.class) return 0L;
        return null;
    }

    private static void assertLatestUpdateOrder(String sqlSegment) {
        String normalized = sqlSegment.replace("`", "").replace(" ", "").toLowerCase();
        assertTrue(sqlSegment, normalized.contains("orderbyupdate_timedesc,iddesc")
                || normalized.contains("orderbyupdatetimedesc,iddesc"));
    }
}
